# Server Secret Storage & Key Management — Implementation Plan

> ⚠️ **TEMPORARY DOC.** Working implementation plan for the server-secret-storage
> feature. Delete once the feature is complete and merged. Design rationale lives
> in [`SERVER-SECRET-STORAGE.md`](SERVER-SECRET-STORAGE.md); this file is the
> sequenced *how* and the working checklist.

## How to use this doc

Each phase is an **independently shippable PR**. Phases are ordered by
dependency — later phases assume earlier ones merged. Check boxes as we go.
Acceptance criteria are the bar for "this PR is done."

**Branch:** `claude/server-secret-storage-4yzeyf` (design doc lives here; cut
feature branches per PR off `develop`).

## ✅ Resolved decisions

Full rationale in [`SERVER-SECRET-STORAGE.md`](SERVER-SECRET-STORAGE.md).

1. **NULL `cipher` = plaintext.** Grounded in commit `b28ecb60` ("At rest
   encryption #367") — `cipher` only began being written there; pre-#367 content
   was plaintext with NULL `cipher`. Read NULL rows with the plaintext encryptor;
   convergence normalizes NULL → `none`. (Today's load path AES-decrypts
   everything, so any surviving NULL row is a latent read bug the registry fixes.)
2. **Keyring format = single JSON document**, schema-versioned, **roles in from
   the start**, base64 key bytes, kotlinx-serialization data classes. Every
   provider returns the same string; one parser.
3. **Providers = File + Env in v1**; explicit single-provider selection via
   `ServerConfig` (default `file`). **No auto-generation anywhere** — both
   providers are read-only at runtime; generate via explicit CLI subcommands.
4. **Convergence = blocking pre-launch step** for enable / disable / rotate.
   **No background/online sweep** (dropped — read-only keyrings can't power a
   runtime admin trigger; offline maintenance is simpler). Token-HMAC rotation =
   automatic re-login on boot (old hashes stop verifying). Rotation on a large DB
   = a maintenance window; accepted.

---

## PR1 — Polymorphic reads + cipher registry (the keystone)

**Goal:** decryption dispatches on the row's `cipher` tag instead of the single
DI-injected encryptor. **No behavior change** — AES is still the only cipher.
This unblocks everything else.

- [x] Introduce a `ContentEncryptorRegistry` (tag → `ContentEncryptor`).
- [x] `PlaintextContentEncryptor` (identity), tag `none` — needed on the **read**
      side now so NULL/plaintext rows are readable (the latent-bug fix). The
      config to *write* plaintext is PR2.
- [x] `ProjectEntityDatabaseDatasource.loadEntity` resolves the encryptor from
      `dbEntity.cipher` via the registry (currently ignores it — uses the single
      injected `encryptor`). Store path unchanged (still tags with the active
      encryptor's `cipherName()`).
- [x] NULL `cipher` → resolve to the plaintext encryptor (Decision 1); unknown
      non-null tag → loud failure, not a silent fallback.
- [x] DI: bind the registry; register `AesGcmContentEncryptor` + plaintext under
      their tags. Resolve the `ContentEncryptor bind` in `mainModule.kt`.

**Acceptance:**
- [x] Existing data round-trips unchanged (load/store tests green).
- [x] **Keystone mixed-state read** (Layer 1): one DB with `aesgcm`, `none`, and
      `NULL` rows — each decrypts via the right encryptor; NULL reads as plaintext.
- [x] Unknown non-null tag → loud failure, never silent garbage.
- [x] **Invariant test: cipher ⊥ hash.** Re-encrypting a row with a different
      cipher must NOT change `entity.hash()` (it's computed over plaintext). Lock
      this — it's what makes convergence safe.

---

## PR2 — Plaintext encryptor + write-mode config

**Goal:** a server can be *configured* to write plaintext (the identity encryptor
already exists from PR1; reads are already polymorphic). No migration yet.

- [x] Config: encryption mode (`aes` | `none`) selecting the **active write**
      encryptor — dedicated `[encryption]` block in `ServerConfig` (`EncryptionMode`
      enum + `EncryptionConfig`, **default `none`** — zero-config = plaintext).
- [x] Store path uses the config-selected active encryptor (DI binds
      `ContentEncryptor` by `encryption.mode`).
- [x] **Reviews made polymorphic too.** `review_scene` gets a `cipher TEXT NOT NULL`
      column (schema v5, migration `4.sqm` backfills existing rows with the AES tag
      — no plaintext history there). `ReviewRepository` tags on write and resolves
      on read via the registry, mirroring `story_entity`.
- [x] **Downgrade guard.** `EncryptionModeGuard.verifyOnBoot` hard-stops the server
      when `mode=none` but AES-tagged rows exist (`story_entity`/`review_scene`),
      forcing an explicit `mode=aes`. Unconditional for now; PR5 refines it (see below).

**Acceptance:**
- [x] With mode=none, new writes are plaintext-tagged; existing AES rows still
      read correctly (proven by PR1's mixed-tag read + cipher⊥hash tests).
- [x] With mode=aes (default), behavior identical to today.
- [x] Switching the config and restarting does not break reads of pre-existing
      rows (proves the keystone).

> User-facing config docs (`HOW-TO-RUN-A-SERVER.md`) deferred to the final-cleanup
> admin tutorial — `mode=none` only affects new writes until convergence (PR5),
> so documenting it now would describe a half-feature.

---

## PR3 — Versioned keyring + `ServerSecretProvider` + generation

**Goal:** replace the single cached secret with a versioned keyring behind a
pluggable provider; fix encoding; make generation explicit.

- [x] `Keyring` / `RoleKeys` kotlinx-serialization data classes (Decision 2):
      single JSON document, `schema` field, `content` + `tokenHmac` roles each with
      `active` + `keys{ id → value }`. One parser (`KeyringCodec`).
- [x] `ServerSecretProvider` interface returning the keyring **string**; explicit
      selection via `ServerConfig` (`[secret] provider = file | env`, default file).
- [x] **File** provider (configurable path; default grandfathers
      `~/hammer_data/server.secret` → `content.v1`, **verbatim** — see key-model
      note) and **Env** provider (one var = keyring JSON).
- [x] `KeyringManager` resolves/parses the keyring; new keys = `base64(32 bytes)`.
      `AesGcmKeyProvider` now reads the active content key.
- [x] **CLI subcommands** via existing **kotlinx-cli** (`Subcommand`s in
      `Application.kt`): `generate-keyring` (both roles, `v1`; stdout or `--out`),
      `inspect-keyring` (ids/active, no bytes; `--in`). `rotate-key` lands with PR5.
- [x] **No auto-generation of content keys.** Missing keyring + `mode=aes` → fail
      fast (`MissingKeyringException`) naming the generate command.

> **Key model (settled):** keyring values are opaque key **strings used directly**
> (PBKDF2 password chars + UTF-8 HMAC bytes), never decoded to raw bytes. New keys
> are `base64(32 random bytes)` (fixes the lossy-entropy bug); a grandfathered key
> is the legacy `server.secret` string verbatim (existing data stays readable).
>
> **Deviations from original plan (deliberate):**
> - Tag stays `"AES/GCM/NoPadding"` — the `aesgcm:vN` format moves to **PR5** with
>   rotation/multiple keys (one active content key in PR3).
> - `TokenHasher` stays on the legacy auto-managed `server.secret` until **PR4**;
>   PR3 only migrates **content** keys to the keyring. (`ServerSecretManager` kept
>   for tokens for now rather than being replaced outright.)
> - Golden-corpus test uses a synthesized legacy secret (incl. non-UTF-8-clean
>   bytes) cross-checked against the legacy reader, rather than a checked-in DB.

**Acceptance:**
- [x] Existing single-`server.secret` deployment boots unchanged, content still
      decrypts (grandfathered as `content.v1`, verbatim).
- [x] Fresh `mode=aes` deployment with no keyring fails fast with guidance.
- [x] Env provider selectable via config (`SecretConfigTest`); File provider serves
      the booted e2e server.
- [x] `generate-keyring` output parses, has both roles, valid base64/32-byte keys
      (`KeyringCodecTest`); verified end-to-end via the CLI.
- [x] **Grandfather golden-corpus test** (Layer 2): grandfather preserves a
      non-UTF-8-clean secret **byte-for-byte**, matching the legacy reader
      (`KeyringManagerTest`). The C1/C5 guard.

---

## PR4 — Split content key from token-HMAC key

**Goal:** the two roles become separate keys so the content key can be retired
without a forced global re-login. Lightweight — no rotation machinery (token
rotation is just automatic re-login on boot).

- [x] `SimpleFileBasedAesGcmKeyProvider` uses `keyring.content` (PR3); `TokenHasher`
      now uses `keyring.tokenHmac`.
- [x] PR3/PR4 sequencing resolved: `TokenHasher` flips here.
- [x] **Token key, zero-config decision:** `TokenHasher` prefers
      `keyring.tokenHmac`; with **no keyring at all** it falls back to the
      auto-managed `server.secret`. So a zero-config plaintext server still
      authenticates with no key setup. "No auto-generation" applies to **content**
      keys (loss = data loss); the token key stays auto-managed (loss = re-login).

**Acceptance:**
- [x] Existing tokens still verify (grandfathered `tokenHmac.v1` == legacy secret →
      identical hashes; `TokenHasherTest`); existing content still decrypts.
- [x] No-keyring server still hashes tokens via the fallback.
- [ ] Deleting the content key after content convergence leaves auth working;
      rotating the token-HMAC key invalidates sessions → re-login, content
      untouched. *(Full deletion/rotation flow exercised in PR5.)*

---

## PR5 — Blocking pre-launch convergence (enable / disable / rotate)

**Goal:** one offline convergence engine for all three operations, with a
provable "old key unused" line. Adds the `rotate-key` CLI subcommand.

Sub-commit order: (a) **key-id tag refactor** — done; (b) convergence engine +
mode refinement + boot gate; (c) `rotate-key` CLI + dry-run + over-cap.

- [x] **Key-id-aware ciphers** (foundational, deferred from PR3): AES tag is now
      `aesgcm:<keyId>`; the registry holds one AES encryptor per content key
      generation; legacy `"AES/GCM/NoPadding"` aliases to `aesgcm:v1`. Key
      derivation takes the content-key value, cached per (content key, client
      secret). `KeyringManager.activeContentKeyId()` drives the active write tag.
- [x] Convergence engine (`EncryptionConvergence`): walk `story_entity` +
      `review_scene` where the normalized tag != target, re-crypt per row (atomic
      single-row update). Targets: `aesgcm:vN` (enable/rotate) or `none` (disable).
      NULL and legacy `AES/GCM/NoPadding` normalized in the predicate (no NULL→none
      churn; legacy ≡ v1 so no re-crypt for servers upgrading from before key ids).
- [x] **Resumable by construction** — the tag column is the progress ledger; a
      re-run re-selects only rows not yet on target (idempotency test covers this).
- [x] **last-applied marker** in `ServerConfigDao` (`encryption.lastAppliedTarget`):
      skip the scan when configured target == last-applied.
- [x] Gate **blocks serving** until convergence completes (`EncryptionBootstrap.run`
      in `appMain`, before routing).
- [x] Progress logging (`EncryptionBootstrap`). *(startup-probe doc note: deferred
      to the final admin tutorial.)*
- [ ] `rotate-key --role content` CLI: read keyring, add `vN+1`, set active, emit.
      *(sub-commit c)*
- [x] **Downgrade guard refined.** `encryption.mode` is now nullable: unspecified +
      encrypted data → hard stop (`UnspecifiedEncryptionModeException`); explicit
      `none` → converge to plaintext; explicit `aes` → converge to aes. *(Keyring
      content-key trigger still tracked for a later pass.)*
- [x] **Over-cap handling:** convergence throws `EncryptionConvergenceException`
      naming the entity + size when an encrypted row would exceed the cap; rows
      already converged stay converged (resumable). Tested.
- [ ] **Convergence dry-run** (mirrors `--migrate-dry-run`). *(sub-commit c)*

**Acceptance:**
- [x] Enable: mode=aes on a plaintext DB → every row `aesgcm:v1`, decrypts to
      original (`EncryptionConvergenceTest`, `EncryptionBootstrapTest`).
- [x] Disable: mode=none on an AES DB → every row plaintext; remaining("none")==0.
- [x] Rotate: converge to `aesgcm:v2` → every row on the new key, decrypts.
- [~] **Crash/no-loss** (C2): covered by design (per-row atomic, tag ledger) +
      idempotency/incremental tests; full mid-run failure injection still to add.
- [x] **Completion signal** (C4): `remaining(target)` counts only not-on-target
      rows; hits 0 only when done (`remaining counts only rows not on target`).
- [~] `kill -9` mid-gate: consistent by per-row atomicity; explicit kill test TODO.
- [ ] Idempotent: normal boot (no change) does not scan the table.
- [ ] Over-cap row hits the decided behavior, not a crash or silent drop.
- [ ] Dry-run reports accurately and commits nothing.

---

## PR6 — Admin dashboard: encryption status (read-only)

**Goal:** surface current encryption posture + the "safe to delete old key"
signal in `/admin`. Informational only (rotation is offline — no trigger button).

- [ ] **Current mode + active key id** (e.g. `AES (content v1)` / `none`), from
      config + the last-applied marker.
- [ ] **Live per-cipher tally** — rows per tag (e.g. `1,203 aesgcm:v1 · 0
      plaintext`); doubles as convergence verification + safe-to-delete signal.
- [ ] Slot into the existing Monitoring nav group (`AdminServerConfig` /
      `ConfigRepository`), "Writer's Desk" web design system.

**Acceptance:**
- [ ] Dashboard shows correct mode/active key and an accurate per-tag tally on a
      mixed-state and a converged DB.

---

## PR7 — External secret providers (opt-in plugins)

**Goal:** production-grade and security-conscious backends, additive via the
PR3 interface.

- [ ] Secrets-manager provider(s): Vault / cloud manager (native versioning maps
      to key ids).
- [ ] KMS envelope / SOPS+age provider for wrapped-keyring deployments.
- [ ] (Optional) systemd-creds / TPM note for bare-metal.

**Acceptance:**
- [ ] At least one external provider works end-to-end against a real/local
      instance; selectable purely via config; no impact on File/Env defaults.

---

## Cross-cutting invariants (apply to every PR)

- Reads stay **read-only** — convergence is owned by the write path + pre-launch gate.
- Re-crypting never changes `entity.hash()` (cipher ⊥ hash).
- No existing deployment is bricked: every phase grandfathers current state.
- Canonical secret encoding = Base64 of 32 raw bytes.
- Tests follow the project's classical style (real collaborators + fakes; fake
  filesystem / in-memory DAOs; assert observable outcomes). Tests in `desktopTest`
  / `server:test`.

## Testing strategy

Framed around the **catastrophes**, not coverage. A bug here doesn't throw in CI —
it silently makes a user's data unreadable. Each test earns its place by guarding
one failure mode:

- **C1 existing data unreadable** (grandfather changes the effective key / wrong
  encryptor picked)
- **C2 data loss mid-convergence** (crash between decrypt and write)
- **C3 silent corruption** (reads back as garbage without erroring)
- **C4 the "safe to delete old key" signal lies** (reports 0 old-key rows while
  some remain → operator deletes a live key)
- **C5 auth breakage** (token hashing changes; unexpected global logout)

Reuse existing infra: `MigrationFixtureBuilder`, `MigrationFullTableParityTest`,
`MigrationFailureModesTest`, the `OldSchemas/` fixtures, embedded Postgres
(Zonky), `RoundTripTestBase` (integrationTests), `E2eTestData`, and the
classical-style fakes already used in `server:test`.

**Layer 1 — registry & keyring behavior (PR1–PR3).** Keystone test: one DB with
mixed tags (`aesgcm:v1` + `none` + `NULL`), assert each row reads back via the
right encryptor. NULL → plaintext; unknown non-null tag → **loud failure** (C3).
`cipher ⊥ hash`. Keyring JSON round-trip; malformed/short keys rejected.

**Layer 2 — grandfather golden corpus (PR3, build first; C1+C5).** Check in a DB
produced by the *current released* server + its `server.secret`; run new code,
assert every entity decrypts to expected plaintext. Must cover:
- **Lossy-secret trap** — the fixture's `server.secret` deliberately contains
  non-UTF-8-clean bytes; grandfather-to-`content.v1` must preserve **exact bytes**
  (re-encoding shifts the derived key → all data unreadable).
- **Token continuity** — pre-existing tokens still verify after the keyring swap.

**Layer 3 — convergence engine (PR5; C2+C4).** Embedded Postgres, real DB. Per
op (enable/disable/rotate): every affected row ends on target tag AND decrypts to
original. The bug-catchers:
- **Crash resumability / no-loss** — inject failure after N of M rows → re-run
  finishes, nothing lost; inject failure *between decrypt and write* on one row →
  that row still holds its readable original (extends `MigrationFailureModesTest`).
- **Completion signal never false-positives** — per-tag tally reports the true
  remaining count mid-run, hits 0 only when genuinely done.
- **Idempotency** — second run is a no-op; matching last-applied marker → table
  not scanned.

**Layer 4 — end-to-end through real sync (PR5/PR6; C1+C3).** Extend
`RoundTripTestBase`: client syncs → toggle mode + restart (converge) → client
syncs again → data intact **and no spurious conflicts** (proves `cipher ⊥ hash`
through the actual sync/conflict path). Plus: File and Env providers both serve
the same data; fresh-deploy with no keyring **fails fast, does not auto-generate**.

**Layer 5 — property tests (PR1/PR5).** Round-trip a generated corpus (empty,
heavy unicode, near-max-size, binary-ish) through each cipher — UTF-8/base64
boundaries are where crypto bugs live.

## Final cleanup (after the feature ships)

- [ ] **Replace [`SERVER-SECRET-STORAGE.md`](SERVER-SECRET-STORAGE.md)** (the
      temporary design doc) with a **user-facing admin tutorial/explainer**:
      what the keyring is, choosing a provider, `generate-keyring` / `rotate-key`
      walkthroughs, enabling/disabling encryption, reading the dashboard status,
      and safely deleting an old key. Link it from `HOW-TO-RUN-A-SERVER.md`.
- [ ] Delete this plan doc.

## Key code touchpoints (for navigation)

- `server/.../project/ProjectEntityDatabaseDatasource.kt` — load/store, `cipher` tag
- `server/.../encryption/ContentEncryptor.kt`, `AesGcmContentEncryptor.kt`
- `server/.../encryption/SimpleFileBasedAesGcmKeyProvider.kt` — PBKDF2 derive
- `server/.../utilities/ServerSecretManager.kt` — becomes the keyring
- `server/.../utilities/TokenHasher.kt` — token-HMAC consumer
- `server/.../ServerConfig.kt` — config blocks
- `server/.../database/ServerConfigDao.kt` — last-applied marker
- `server/.../dependencyinjection/mainModule.kt` — DI bindings
- `server/.../sqldelight/.../StoryEntity.sq` — `cipher` column
- `server/.../Application.kt` — kotlinx-cli `ArgParser`; add keyring `Subcommand`s
