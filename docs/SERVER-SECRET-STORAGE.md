# Server Secret Storage & Key Management — Living Design Doc

> ⚠️ **TEMPORARY DOC.** This is a working design reference and checklist for the
> server-secret-storage / key-management feature. It is intended to be **deleted
> once the feature is complete and merged.** Do not treat it as permanent
> documentation — the user-facing bits should graduate into
> `docs/HOW-TO-RUN-A-SERVER.md`.

## Goal

Stop treating the server secret as an auto-generated file living next to the
data it protects, and give the server a proper key-management story that
supports:

1. **Pluggable secret storage** — file, environment variable, mounted secret,
   external secrets manager / KMS — without forcing heavy dependencies on casual
   self-hosters.
2. **Per-row encryption modes**, including **no encryption at all**, for
   resource-constrained self-hosters (the Raspberry Pi case).
3. **Online key rotation** on a live, running server with zero downtime.
4. A **provable "this key is no longer used"** signal so an operator can safely
   delete an old key (or migrate fully to plaintext).

## Background — what exists today

The server secret is 32 random bytes, generated on first boot and written to
`~/hammer_data/server.secret`, cached in memory.
(`ServerSecretManager`, `getRootDataDirectory`.)

It has **two consumers**, both deriving from the *same* raw value:

- **`SimpleFileBasedAesGcmKeyProvider`** — PBKDF2 password mixed with each user's
  `cipher_secret` (client secret) to derive their per-user AES key.
- **`TokenHasher`** — HMAC-SHA256 key for hashing auth tokens.

`story_entity` already has a nullable **`cipher TEXT`** column. On **store** it
records `encryptor.cipherName()` (e.g. `"AES/GCM/NoPadding"`). **But on load the
column is ignored** — `ProjectEntityDatabaseDatasource.loadEntity` decrypts with
the single DI-injected `ContentEncryptor` regardless of what the row says. There
is exactly one `ContentEncryptor` bound in the graph. So the column is currently
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
  256 bits of entropy and isn't a clean, round-trippable byte string. Any move
  to env/manager-based storage needs a defined encoding first.
- **Silent auto-generation is a footgun.** If a deploy's volume isn't actually
  persisted, a restart silently mints a *new* secret and bricks all existing
  encrypted data + invalidates all tokens, with no error.
- **Un-rotatable by construction.** The raw secret is fed directly into PBKDF2
  for every user and used as the HMAC key. There is no version tag anywhere, so
  it cannot be rotated without re-deriving every user's key.

## Locked design decisions

### 1. The secret's two roles get split

The content-encryption secret and the token-HMAC secret become **separate
keys**, because they have fundamentally different lifecycles and mechanics:

| | Content key | Token-HMAC key |
|---|---|---|
| Re-key mechanism | Transparent re-encrypt (decrypt old → encrypt new) | One-way HMAC — **cannot** re-encrypt a stored hash |
| Convergence | To a definitive point (we want to *know* it's done) | Lazy: verify-with-fallback, rehash on successful auth |
| Stragglers | Must be swept | Fine to leave — tokens are ephemeral and expire |
| Deletion impact | None once content converged | Deleting forces a **global re-login** |

Bolting them together means you can't delete one or rotate one without dragging
in the other's constraints. Splitting them is what makes "migrate content to
plaintext, then delete the content key" possible with **zero auth impact**.

### 2. Pluggable `ServerSecretProvider`

A provider interface selected via `ServerConfig`, mirroring the existing
`emailProvider` / `analyticsProvider` / `AesGcmKeyProvider` patterns.
Implementations ship incrementally; the interface makes new backends additive.

### 3. Versioned keyring, not a single secret

`ServerSecretManager` becomes a **keyring** of versioned keys (`v1`, `v2`, …).
Config names the **active** key (used for writes). Old keys stay loaded for
reads until convergence retires them.

### 4. Per-row tag carries `(algorithm, key-id)`

The per-row tag must encode **which algorithm AND which key generation** — not
just the algorithm name. The current `cipher` column stores only the algorithm,
which is enough for the plaintext toggle but useless for rotation (algorithm is
unchanged, only the key differs). Tag format e.g. `"aesgcm:v2"`, `"none"`.

### 5. Polymorphic reads (the keystone)

**Reads dispatch on the row's tag** via a registry of encryptors keyed by tag;
writes use the config-declared active encryptor. This is the prerequisite for
everything else — once reads are polymorphic, **mixed-state data is correct by
construction.** A DB with some AES rows and some plaintext rows (or v1 + v2 rows)
just works, forever, with no migration required for correctness. Migration
becomes purely a *convergence / hygiene* concern, never a correctness gate.

`ContentEncryptor` registry includes:
- `AesGcmContentEncryptor` → tag `aesgcm:<keyId>`
- **new** `PlaintextContentEncryptor` (identity) → tag `none`

### 6. Strategy follows the operation (not one global winner)

The same underlying machinery (tag + polymorphic reads + a convergence engine)
runs in two modes; the **operation** picks the mode:

| Operation | Lifecycle need | Strategy |
|---|---|---|
| Migrate to plaintext → delete key | Definitive "key is unused" line | **Blocking startup gate** |
| Live key rotation v1 → v2 | Zero downtime | **Online background sweep** |
| Token-HMAC key rotation | Convergence doesn't matter | **Lazy** verify-with-fallback + rehash-on-auth |

Both content modes share the same completion signal: **rows still tagged with
the old key/cipher == 0** → old key is provably unreferenced and safe to delete.
The gate *blocks boot* until that's true; the sweep *observes* it asynchronously.

### 7. Generation is explicit, not a boot side-effect

Auto-generation leaves the *serving* path. Generation logic stays in code behind
an explicit CLI subcommand. Canonical secret encoding is **Base64 of 32 raw
bytes** (256 bits) — fixes the entropy bug and makes the value portable across
all storage backends.

## Why a blocking gate (for the plaintext / delete-the-key case)

A background convergence job can't tell you when a cold, never-touched row last
needed the key, so you can never confidently delete it. A blocking gate gives a
clean, actionable invariant:

> If the server is up and serving with `cipher = none`, every content row is
> plaintext and the content key is provably unused.

Caveat that makes this fully actionable: deletion is only zero-impact **because**
of decision #1 (split keys). With a unified secret, deleting it still forces a
global re-login via `TokenHasher`.

## Why a background sweep (for live rotation)

You cannot take a live writing server offline to re-encrypt everyone's data on
boot, and rotation is exactly what you do on the busy production box. So:

- Keyring holds v1 + v2; writes use v2; hot data converges on write for free.
- A **throttled background sweeper** re-encrypts v1 rows → v2 online, resumable
  (the tag column *is* the progress ledger — re-scan `WHERE tag != target`).
- **Note the cost:** derived key = `PBKDF2(serverSecret, clientSecret)`, so
  rotating the server secret changes **every user's** content key — rotation is
  "re-encrypt every content row of every account," not a few rows. This is why
  it must be online + throttled, and why v1 must stay loaded until the *global*
  count hits zero.

## Generating the secret

**Primary — CLI subcommand in the server jar:**

```
java -jar hammer-server.jar gen-secret
# → HAMMER_SERVER_SECRET=<base64 of 32 random bytes>
```

Uses our own `SecureRandom` + canonical encoding (no UTF-8 mangling), can assign
the keyring version id, and prints copy-paste-ready output.

**Fallback — standard one-liners** (interchangeable given the canonical encoding):

```
openssl rand -base64 32
head -c 32 /dev/urandom | base64
```

**Bootstrap UX:** a fresh start with no secret configured must **fail fast with
guidance** (how to generate + where to put it + link to docs), not NPE. Preserve
the zero-config on-ramp as an explicit opt-in (`HAMMER_AUTO_GENERATE_SECRET=true`)
for toy instances — deliberate choice, not silent default.

## Storing & retrieving the secret

All behind `ServerSecretProvider`, selected via `ServerConfig`. Ordered
self-host-friendly → production-grade. The store must hold **multiple versioned
keys** (rotation needs v1 + v2 to coexist).

| Backend | How it gets there | Server reads via | Notes |
|---|---|---|---|
| **Env var(s)** | compose / k8s / systemd, injected from Docker/k8s secrets | `System.getenv` | Best broad default. Leak surface = the box (`/proc/<pid>/environ`, `docker inspect`). Keyring shape: `…_ACTIVE=v2` + `…_V1`/`…_V2`, or one var holding an encoded keyring. |
| **Mounted file, configurable path** | orchestrator mounts at e.g. `/run/secrets/hammer` (tmpfs, **outside** the data volume) | read file at config path | **Smallest delta from today** — make the path configurable + stop auto-gen. Native fit for Docker/k8s secrets, systemd `LoadCredential`. Keyring = small JSON/YAML `{active, keys:{…}}`. |
| **Secrets manager** (Vault, AWS/GCP/Azure, Infisical, Doppler) | `vault kv put …` once | fetch over API at boot, cache in memory, never on disk | Audit logs, access control, durable/replicated, **native versioning** (manager's version *is* the key id). Opt-in plugin; the grown-up option for hammer.ink. |
| **KMS / envelope** (AWS/GCP KMS; **SOPS+age** for self-host) | wrap the keyring; store ciphertext (even in the data dir — useless alone) | call KMS at boot to unwrap | On-disk artifact safe without the master key; rotate master without touching user data. SOPS+age is a tidy GitOps-y self-host flavor. |
| **systemd-creds / TPM** | `systemd-creds encrypt`, bound to TPM/host | `LoadCredential=` exposes at a path | Bare-metal Pi hosts with no containers. Niche. |

Generation and storage stay **decoupled**: `gen-secret` makes the value; you
place it via `export` / mounted file / `vault kv put` / `sops`. Adding v2 during
rotation = "generate one more, drop it in beside v1, point `active` at it."

## What we'd actually ship (recommended default stack)

- **Default: env var**, with **configurable mounted-file** as the other
  first-class provider — together ~90% of self-hosters, near-zero added
  complexity, native Docker/k8s secret fit.
- **`gen-secret` CLI** as the blessed generator (fixes encoding, assigns
  versions).
- **Vault / cloud-manager and SOPS+age as opt-in plugins** for hammer.ink and
  security-conscious hosts.
- **Fail-fast-with-guidance** when nothing's configured, plus the opt-in
  `AUTO_GENERATE` escape hatch.

## Implementation notes & invariants

- **Cipher is orthogonal to `hash`.** `hash = entity.hash()` is computed over the
  *plaintext* entity, not the ciphertext. So re-crypting a row does **not** change
  its hash → no spurious sync conflicts, clients never notice. Lock this with a
  test; it's what makes transparent re-encryption / convergence safe.
- **Keep the hot read path read-only.** Convergence is owned by the write path +
  the sweeper/gate. Never sneak write-back into `loadEntity`.
- **Per-row (or small-batch) transactions** for any sweep/gate — decrypt-then-
  write-plaintext as one unit so a `kill -9` leaves a consistent mixed state the
  next run resumes from. Never one giant transaction.
- **Cheap normal boots.** Don't scan the whole table every boot. Track a
  "last-applied cipher/key" marker (server config in DB via `ServerConfigDao`);
  only run the (blocking, resumable) convergence when configured target ≠
  last-applied. Otherwise boot instantly.
- **Long boots must survive orchestration.** Use a **startup probe** (not a
  liveness probe) + progress logging so a multi-minute Pi migration isn't killed
  mid-run (safe to kill — it's resumable — but it'd never finish if repeatedly
  killed).
- **Token-key rotation:** store the key-id alongside the token hash; on auth,
  verify against the row's key-id; on a successful auth with an old key-id,
  rehash with the active key and update the row. Stragglers expire on their own.
- **No auto-heal of the secret.** Once a key is intentionally deleted, the next
  boot must not silently regenerate it. Demand-load only; absence is intentional.

## Plaintext / `cipher` column — open decision

The column is nullable and legacy rows may be NULL. Lean toward an **explicit
sentinel** (`none`) for deliberately-plaintext, treating NULL as "legacy/unknown
→ assume AES (v1)", so the two stay distinguishable.

**Need to confirm:** do any current rows have NULL `cipher`, and if so, what
cipher were they actually written with? Answer determines the NULL-handling rule
and the v1 backfill.

## Open questions / TODO

- [ ] Confirm NULL-`cipher` semantics in existing prod data (above).
- [ ] Decide whether enabling encryption (plaintext → AES) also offers an
      optional **blocking** mode for security-conscious hosts (guarantee no
      plaintext remains before serving), vs. background sweep default.
- [ ] Exact keyring serialization format (env multi-var vs. single encoded var;
      file JSON/YAML schema).
- [ ] `ServerSecretProvider` interface shape + which providers ship in v1
      (proposed: File + Env).
- [ ] Admin UI / log surface for convergence progress + "old key now
      unreferenced, safe to delete" signal.
- [ ] Migration of the existing single `server.secret` → keyring `v1` (+ encoding
      fix) without bricking existing deployments.
