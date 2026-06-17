# Server Secret Storage & Key Management — Living Design Doc

> ⚠️ **TEMPORARY DOC.** Working design reference for the server-secret-storage /
> key-management feature. **When the feature ships, this file is replaced by a
> user-facing admin tutorial/explainer** (provider setup, `generate-keyring` /
> `rotate-key`, enabling/disabling encryption, dashboard status, deleting old
> keys), linked from `docs/HOW-TO-RUN-A-SERVER.md`. The sequenced build plan lives
> in [`SERVER-SECRET-STORAGE-PLAN.md`](SERVER-SECRET-STORAGE-PLAN.md).

## Goal

Stop treating the server secret as an auto-generated file living next to the
data it protects, and give the server a proper key-management story that
supports:

1. **Pluggable secret storage** — file, environment variable, and (later)
   external secrets managers — without forcing heavy dependencies on casual
   self-hosters.
2. **Per-row encryption modes**, including **no encryption at all**, for
   resource-constrained self-hosters (the Raspberry Pi case).
3. **Offline key rotation** as a deliberate maintenance step (no live/online
   rotation — see Decision 6).
4. A **provable "this key is no longer used"** signal so an operator can safely
   delete an old key (or migrate fully to plaintext), surfaced in the admin
   dashboard.

## Background — what exists today

The server secret is 32 random bytes, generated on first boot and written to
`~/hammer_data/server.secret`, cached in memory.
(`ServerSecretManager`, `getRootDataDirectory`.)

It has **two consumers**, both deriving from the *same* raw value:

- **`SimpleFileBasedAesGcmKeyProvider`** — PBKDF2 password mixed with each user's
  `cipher_secret` (client secret) to derive their per-user AES key.
- **`TokenHasher`** — HMAC-SHA256 key for hashing auth tokens.

`story_entity` has a nullable **`cipher TEXT`** column. On **store** it records
`encryptor.cipherName()` (e.g. `"AES/GCM/NoPadding"`). **But on load the column
is ignored** — `ProjectEntityDatabaseDatasource.loadEntity` decrypts with the
single DI-injected `ContentEncryptor` regardless of what the row says. There is
exactly one `ContentEncryptor` bound in the graph. So the column is currently
decorative on the read side.

### Problems with the status quo

- **Co-location defeats the purpose.** The secret sits in `hammer_data/` right
  next to `pgdata/`. The whole point of the secret is "a stolen DB/backup/volume
  snapshot is useless without the key" — but one `docker cp` / leaked snapshot /
  backup tarball grabs both. The secret provides ~zero marginal protection
  against the most likely leak vector. **The real win is separating the secret
  from the data it protects.**
- **Latent entropy bug.** `generateSecret()` does `secureRandom.nextBytes(32)`
  then `commonToUtf8String()`. Arbitrary bytes are **not** valid UTF-8 — invalid
  sequences collapse to U+FFFD, so the stored secret has materially less than
  256 bits of entropy and isn't a clean, round-trippable byte string.
- **Silent auto-generation is a footgun.** If a deploy's volume isn't actually
  persisted, a restart silently mints a *new* secret and bricks all existing
  encrypted data + invalidates all tokens, with no error.
- **Un-rotatable by construction.** The raw secret is fed directly into PBKDF2
  for every user and used as the HMAC key. There is no version tag anywhere, so
  it cannot be rotated without re-deriving every user's key.

## Locked design decisions

### 1. The secret's two roles get split

The content-encryption key and the token-HMAC key become **separate keys** in
the keyring, because they have different deletion stories:

- **Content key** — re-keyed by transparently re-encrypting rows (decrypt old →
  encrypt new). Once content is converged off a key, that key is deletable with
  **zero auth impact**.
- **Token-HMAC key** — HMAC is one-way; a stored hash cannot be re-encrypted.
  Rotating it means old hashes stop verifying → all sessions invalidated →
  global re-login. No special machinery; it's automatic on boot.

Keeping them separate is what lets you "migrate content to plaintext, then delete
the content key" without forcing every user to log in again.

### 2. Pluggable `ServerSecretProvider`, explicit selection

A provider interface selected **explicitly via `ServerConfig`** (`secret.provider
= file | env`, default `file`), mirroring the existing `emailProvider` /
`analyticsProvider` / `storage.type` patterns. No resolution chain — one
configured answer for "where is the keyring read from." New backends are additive.

### 3. Versioned keyring as a single JSON document

`ServerSecretManager` becomes a **keyring**: a single JSON document, modeled as
kotlinx-serialization data classes, returned as an opaque string by *every*
provider and parsed once. **Roles are in the document from the start.** Key bytes
are **standard Base64** (matches existing `kotlin.io.encoding.Base64` usage).

```json
{ "schema": 1,
  "content":   { "active": "v1", "keys": { "v1": "<base64 32 bytes>" } },
  "tokenHmac": { "active": "v1", "keys": { "v1": "<base64 32 bytes>" } } }
```

```kotlin
@Serializable data class Keyring(val schema: Int = 1, val content: RoleKeys, val tokenHmac: RoleKeys)
@Serializable data class RoleKeys(val active: String, val keys: Map<String, String>) // value = base64 bytes
```

The keyring holds **key material + which key is active**. It does **not** hold the
encryption *mode* (aes vs none) — that's a separate `ServerConfig` setting
(Decision 5). `active` only selects which AES key new writes use; in `none` mode
no key is used for new writes.

Decoupling the format from the location means the provider writes/serialization
logic exists once. The one accepted tradeoff: a secrets manager's *native*
per-secret versioning is bypassed (we treat it as a plain KV store holding one
document); its versioning becomes a free audit/rollback nicety, not our source of
truth.

### 4. Per-row tag carries `(algorithm, key-id)`

The per-row `cipher` tag encodes **algorithm AND key generation**, not just the
algorithm. Tag format e.g. `"aesgcm:v1"`, `"none"`.

### 5. Polymorphic reads (the keystone) + plaintext mode

**Reads dispatch on the row's tag** via a registry of encryptors keyed by tag;
writes use the config-declared active encryptor/mode. This is the prerequisite
for everything else — once reads are polymorphic, **mixed-state data is correct
by construction.** A DB with some AES rows and some plaintext rows (or v1 + v2
rows) just works, forever. Convergence becomes a *hygiene* concern, never a
correctness gate.

Registry:
- `AesGcmContentEncryptor` → tag `aesgcm:<keyId>`
- **new** `PlaintextContentEncryptor` (identity) → tag `none`

**NULL `cipher` = plaintext.** Confirmed from history: `cipher` only began being
written in commit `b28ecb60` "At rest encryption (#367)" — before that, content
was stored plaintext with NULL `cipher`. So NULL rows are pre-#367 plaintext;
the reader hands them to the plaintext encryptor. A wrong guess fails *loud*
(bytes won't deserialize as JSON), not silently. Convergence normalizes NULL →
`none`. (Note: today's load path AES-decrypts everything, so any surviving
NULL/plaintext row is currently a latent read failure that the polymorphic reader
fixes.)

The encryption mode (`aes` | `none`) is an explicit `ServerConfig` setting
(`[encryption] mode`) that selects the **active write** encryptor. **Default =
`none` (plaintext).** A zero-config server stores plaintext and needs no key
material — the simplest, hardest-to-misconfigure path for a casual self-hoster.
Enabling AES is a deliberate opt-in that requires a keyring.

> ⚠️ Upgrade note: pre-feature servers always encrypted (auto-generated secret).
> After this ships, an existing deployment defaults to `none`. To stop a silent
> downgrade, **the server hard-stops on boot** (`EncryptionModeGuard`) when
> `mode=none` but AES-tagged rows exist — the admin must explicitly set
> `mode=aes`. (Today the hard-stop is unconditional; PR5 refines it so an
> *explicit* `mode=none` instead triggers convergence-to-plaintext, and adds a
> second trigger on a keyring content-key being present.)

**Reviews are polymorphic too.** `review_scene.snapshot_content` carries the same
per-row `cipher` tag and decrypts through the same registry. Unlike `story_entity`,
review rows have **no plaintext history** (the table postdates at-rest encryption),
so the schema-v5 migration backfills existing rows with the AES tag — NULL would
wrongly mean plaintext there. The column is `NOT NULL`; every write states its
cipher.

### 6. All convergence is an offline, blocking pre-launch step

Enable (plaintext → AES), disable (AES → plaintext), and rotate (v1 → v2) are
**all** handled by one blocking pre-launch convergence step — the server
re-crypts every affected row *before* it accepts traffic. Run in the spirit of a
data migration: idempotent, resumable (the tag column is the progress ledger —
re-scan `WHERE tag != target`), deterministic.

**There is no background/online sweep.** It was considered and dropped: its main
justification was an admin-triggered "rotate now" button, but read-only keyring
backends (env/file/secrets-manager) can't mutate the active pointer at runtime,
so it would have forced DB-backed active state + a throttled online sweeper +
lazy token rehash — a lot of machinery for zero-downtime rotation that a
self-hosted writing app doesn't need. Rotation as offline maintenance is simpler
and consistent with the rest of the design.

| Operation | Strategy |
|---|---|
| Enable encryption (plaintext → AES) | Blocking pre-launch step |
| Disable encryption (AES → plaintext) | Blocking pre-launch step |
| Content key rotation (v1 → v2) | Blocking pre-launch step |
| Token-HMAC key rotation | Automatic on boot (old hashes stop verifying → re-login) |

**Accepted tradeoff:** enabling/rotating on a large DB means boot downtime
proportional to data size (a maintenance window). Acceptable for offline
maintenance.

**Completion signal:** rows still tagged with the old key/cipher == 0 → old key
is provably unreferenced and safe to delete. Surfaced in the admin dashboard
(below). With the blocking gate, this is always true at runtime for the active
target — the dashboard tally confirms it and flags when an old key can be dropped.

### 7. No auto-generation; generation via explicit CLI subcommands

There is **no auto-generation anywhere** — both providers are read-only at
runtime; nothing mints a key as a side effect of booting (the
silent-regeneration footgun is gone by construction). A fresh server with no
keyring **fails fast with guidance** naming the generate command and where to put
its output for the configured provider.

Generation is explicit CLI subcommands on the server:
- `generate-keyring` — emit a fresh keyring (both roles, each `v1`, `active: v1`).
  stdout by default; `--out <path>` to write a file; canonical Base64-of-32-bytes
  via our `SecureRandom`.
- `rotate-key --role content` — read an existing keyring, add `vN+1`, set active,
  emit the updated document. (Offline rotation: place updated keyring → restart →
  blocking gate re-encrypts.)
- `inspect-keyring` — show key ids / active without revealing bytes.

## Admin dashboard: encryption status (read-only)

Surfaced in the existing `/admin` Monitoring area (`AdminServerConfig` /
`ConfigRepository` pattern). Informational only — no trigger button, since
rotation is offline.

- **Current mode + active key id** — e.g. `Encryption: AES (content v1)` or
  `Encryption: none (plaintext)`, from config / the last-applied marker.
- **Live per-cipher tally** — count of rows per tag, e.g.
  `1,203 aesgcm:v1 · 0 plaintext`. Doubles as convergence verification and the
  **"safe to delete old key"** signal: when an old tag's count hits 0, the
  operator knows that key is unreferenced.

## Storing & retrieving the keyring

All behind `ServerSecretProvider`, explicitly selected via `ServerConfig`. Every
provider returns the **same JSON keyring string** (Decision 3); only the location
differs. **v1 ships File + Env**; managers/KMS are deferred opt-in plugins.

| Backend | Ships | How it gets there | Server reads via | Notes |
|---|---|---|---|---|
| **File** (configurable path) | **v1** | operator writes the keyring JSON to a path | read file at config path | Default path grandfathers `~/hammer_data/server.secret` → keyring v1. Also covers Docker/k8s secret mounts (`/run/secrets/…`), **systemd-creds** (`$CREDENTIALS_DIRECTORY`), and SOPS-decrypt-to-file — all "for free". |
| **Env var** | **v1** | one var holds the keyring JSON string | `System.getenv` | 12-factor; native fit for compose/k8s secret injection. Leak surface = the box (`/proc/<pid>/environ`, `docker inspect`). |
| **Secrets manager** (Vault, AWS/GCP/Azure, Infisical, Doppler) | later | `vault kv put …` once | fetch over API at boot, cache in memory | Audit logs, access control, durable/replicated. Opt-in plugin; the grown-up option for hammer.ink. |
| **KMS / envelope** (AWS/GCP KMS; **SOPS+age**) | later | wrap the keyring; store ciphertext | call KMS at boot to unwrap | On-disk artifact safe without the master key. SOPS+age is a tidy GitOps-y self-host flavor. |

Generation and storage stay **decoupled**: `generate-keyring` makes the document;
you place it via `--out` / `export` / `vault kv put` / `sops`. Offline rotation =
`rotate-key` → place updated keyring → restart.

## Implementation notes & invariants

- **Cipher is orthogonal to `hash`.** `hash = entity.hash()` is over the
  *plaintext* entity, not the ciphertext. Re-crypting a row does **not** change
  its hash → no spurious sync conflicts. Lock with a test; it's what makes
  convergence safe.
- **Keep the hot read path read-only.** Convergence is owned by the write path +
  the pre-launch gate. Never sneak write-back into `loadEntity`.
- **Per-row (or small-batch) transactions** in the gate — decrypt-then-write as
  one unit so a `kill -9` leaves a consistent mixed state the next run resumes
  from. Never one giant transaction.
- **Cheap normal boots.** Track a "last-applied mode/key" marker (DB via
  `ServerConfigDao`); run the blocking convergence only when configured target ≠
  last-applied. Otherwise boot without scanning.
- **Long boots must survive orchestration.** Use a **startup probe** (not a
  liveness probe) + progress logging so a multi-minute convergence isn't killed
  mid-run (safe to kill — resumable — but it'd never finish if repeatedly killed).
- **No auto-heal of the secret.** An intentionally-deleted key is never silently
  regenerated. Demand-load only; absence is intentional → fail fast.

## Resolved decisions (the four we walked through)

1. **NULL `cipher` = plaintext** (read with identity encryptor; normalize NULL →
   `none` on convergence). Grounded in commit `b28ecb60`.
2. **Keyring format** = single schema-versioned JSON document, roles in from the
   start, base64 key bytes, kotlinx-serialization data classes.
3. **Providers** = File + Env in v1; explicit single-provider selection via
   `ServerConfig` (default file); **no auto-generation** — generate via CLI
   subcommands.
4. **Convergence** = blocking pre-launch step for enable/disable/rotate; **no
   background sweep**; token-HMAC rotation = automatic re-login on boot.

## Open questions / TODO

- [ ] PR3/PR4 sequencing: pre-PR4, does `TokenHasher` already read
      `keyring.tokenHmac`, or stay on the legacy path until PR4? (Generator emits
      both roles either way.)
- [ ] Migration of the existing single `server.secret` → keyring `content.v1`
      (grandfather its exact current bytes — do **not** re-encode — so existing
      data still decrypts) without bricking deployments.
- [x] Encryption-mode setting: dedicated `[encryption] mode` block (`aes` |
      `none`, default `none`). Shipped in PR2. `secret.provider` shape still TBD
      (PR3).
