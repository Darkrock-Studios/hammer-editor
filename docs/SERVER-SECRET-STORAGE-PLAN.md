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

## ⛔ Blocking decision before any code

PR1 depends on this — resolve first:

- [ ] **NULL-`cipher` semantics.** The `story_entity.cipher` column is nullable
      and legacy rows may be NULL. Confirm against prod data: do any rows have
      NULL `cipher`, and what cipher were they actually written with? This sets
      the read-dispatch fallback rule (proposed: NULL → `aesgcm:v1`) and the v1
      backfill. **Action:** query a prod/representative DB:
      `SELECT cipher, COUNT(*) FROM story_entity GROUP BY cipher;`

Decisions still open but **not** blocking PR1 (decide before the noted phase):
- [ ] Optional **blocking mode for enabling encryption** (plaintext → AES) — decide before PR5.
- [ ] Keyring **serialization format** (env multi-var vs. single encoded var; file JSON/YAML schema) — decide before PR3.
- [ ] Which providers ship in v1 (proposed: File + Env) — decide before PR3.

---

## PR1 — Polymorphic reads + cipher registry (the keystone)

**Goal:** decryption dispatches on the row's `cipher` tag instead of the single
DI-injected encryptor. **No behavior change** — AES is still the only cipher.
This unblocks everything else.

- [ ] Introduce a `ContentEncryptorRegistry` (tag → `ContentEncryptor`).
- [ ] `ProjectEntityDatabaseDatasource.loadEntity` resolves the encryptor from
      `dbEntity.cipher` via the registry (currently ignores it — uses the single
      injected `encryptor`). Store path unchanged (still tags with the active
      encryptor's `cipherName()`).
- [ ] NULL/unknown `cipher` → fallback per the blocking decision above.
- [ ] DI: bind the registry; keep `AesGcmContentEncryptor` registered under its
      tag. Resolve the `ContentEncryptor bind` in `mainModule.kt`.

**Acceptance:**
- [ ] Existing data round-trips unchanged (load/store tests green).
- [ ] Mixed-state read works: a row tagged AES and a row tagged with a fake
      registered cipher both decrypt via the right encryptor (test with a stub).
- [ ] **Invariant test: cipher ⊥ hash.** Re-encrypting a row with a different
      cipher must NOT change `entity.hash()` (it's computed over plaintext). Lock
      this — it's what makes convergence safe.

---

## PR2 — Plaintext encryptor + write-mode config

**Goal:** a server can be configured to write plaintext; mixed AES+plaintext
rows are correct (reads already polymorphic from PR1). No migration yet.

- [ ] `PlaintextContentEncryptor` (identity), tag `none`. Register it.
- [ ] Config: encryption mode selecting the **active write** encryptor (new
      `encryption` block in `ServerConfig`, or fold into `StorageConfig` — decide).
- [ ] Store path uses the config-selected active encryptor.

**Acceptance:**
- [ ] With mode=none, new writes are plaintext-tagged; existing AES rows still
      read correctly.
- [ ] With mode=aes (default), behavior identical to today.
- [ ] Switching the config and restarting does not break reads of pre-existing
      rows (proves the keystone).

---

## PR3 — Versioned keyring + `ServerSecretProvider` + generation

**Goal:** replace the single cached secret with a versioned keyring behind a
pluggable provider; fix encoding; make generation explicit.

- [ ] `ServerSecretProvider` interface (selected via `ServerConfig`), mirroring
      `emailProvider`/`analyticsProvider` patterns.
- [ ] Providers: **File** (default) and **Env**. File provider **grandfathers**
      the existing `~/hammer_data/server.secret` as key `v1` (read its exact
      current value — do NOT re-encode it, or existing data won't decrypt).
- [ ] `ServerSecretManager` → keyring: map of `keyId → secret`, plus an `active`
      key id. New keys use canonical **Base64-of-32-bytes** encoding.
- [ ] Extend the tag to `(algorithm, keyId)` → `aesgcm:v1`. Update store to tag
      with the active key id; update the registry/resolver to pick `(algo, key)`.
- [ ] **`gen-secret` CLI subcommand** in the server jar (uses our `SecureRandom`
      + canonical encoding; emits a keyring-ready value).
- [ ] **Remove silent auto-gen from the serving path.** Missing secret →
      fail-fast with guidance (how to generate + where to put it + docs link).
- [ ] Opt-in `HAMMER_AUTO_GENERATE_SECRET=true` escape hatch for toy instances.
- [ ] **No auto-heal:** an intentionally-deleted key is never silently regenerated.

**Acceptance:**
- [ ] Existing single-`server.secret` deployment boots unchanged, its data still
      decrypts (grandfathered as v1).
- [ ] Fresh deployment with no secret fails fast with a helpful message; with the
      opt-in flag, generates v1.
- [ ] Env provider works end-to-end (set var → boot → decrypt).
- [ ] `gen-secret` output is valid Base64/32 bytes and usable by both providers.

---

## PR4 — Split content key from token-HMAC key

**Goal:** the two roles become separate keys so content can be retired without a
forced global re-login.

- [ ] Two key roles in the keyring (content, token-HMAC). Grandfather the
      existing v1 secret as **both** initially (same value) so nothing breaks.
- [ ] `SimpleFileBasedAesGcmKeyProvider` uses the content key; `TokenHasher` uses
      the token-HMAC key.
- [ ] Token hashes record the key id used (needed for PR6 lazy rotation).

**Acceptance:**
- [ ] Existing tokens still verify; existing content still decrypts.
- [ ] Rotating/deleting one role's key does not require touching the other
      (covered concretely in PR5/PR6).

---

## PR5 — Convergence engine: blocking startup gate

**Goal:** deterministic convergence for the plaintext-migration / key-retirement
path, with a provable "old key unused" line.

- [ ] Convergence engine: walk rows where `tag != active target`, re-crypt in
      **per-row (or small-batch) transactions** (decrypt-then-write as one unit).
- [ ] **Resumable by construction** — the tag column is the progress ledger;
      restart re-scans.
- [ ] **last-applied marker** in `ServerConfigDao`: skip the scan entirely when
      configured target == last-applied (instant normal boots). Run the gate only
      on an actual mode/key change, then update the marker.
- [ ] Gate **blocks serving** until convergence completes.
- [ ] Progress logging; ensure a long boot survives orchestration (document
      **startup probe**, not liveness probe).
- [ ] Surface the completion signal: count of rows still on the old tag → when 0,
      old key is provably unreferenced.

**Acceptance:**
- [ ] Configure mode=none on an AES DB → restart → after boot every row is
      plaintext; the content key is provably unused (and, post-PR4, deletable
      with zero auth impact).
- [ ] `kill -9` mid-gate leaves consistent mixed state; next boot resumes and
      finishes.
- [ ] Normal boot (no mode change) does not scan the table.

---

## PR6 — Online rotation: background sweep + lazy token rehash

**Goal:** zero-downtime key rotation on a live server.

- [ ] Keyring holds v1 + v2; writes use v2; hot data converges on write for free.
- [ ] **Throttled background sweeper** re-encrypts old-key rows → active, online,
      resumable. Rate-limit configurable (Pi-friendly).
- [ ] v1 stays loaded until the **global** count of v1-tagged rows hits zero;
      surface that signal for safe retirement.
- [ ] **Token-HMAC lazy rotation:** on auth, verify against the token row's key
      id; on success with an old key id, rehash with the active key and update.
      Stragglers expire naturally.

**Acceptance:**
- [ ] Add v2, mark active, server keeps serving throughout; sweeper migrates all
      content v1 → v2 without downtime.
- [ ] After sweep, v1-tagged content count is 0 and v1 can be removed.
- [ ] Tokens transparently rehash on use; no forced global re-login on rotation.

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

- Reads stay **read-only** — convergence is owned by the write path + sweeper/gate.
- Re-crypting never changes `entity.hash()` (cipher ⊥ hash).
- No existing deployment is bricked: every phase grandfathers current state.
- Canonical secret encoding = Base64 of 32 raw bytes.
- Tests follow the project's classical style (real collaborators + fakes; fake
  filesystem / in-memory DAOs; assert observable outcomes). Tests in `desktopTest`
  / `server:test`.

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
