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

- [ ] Introduce a `ContentEncryptorRegistry` (tag → `ContentEncryptor`).
- [ ] `ProjectEntityDatabaseDatasource.loadEntity` resolves the encryptor from
      `dbEntity.cipher` via the registry (currently ignores it — uses the single
      injected `encryptor`). Store path unchanged (still tags with the active
      encryptor's `cipherName()`).
- [ ] NULL `cipher` → resolve to the plaintext encryptor (Decision 1); unknown
      non-null tag → loud failure, not a silent fallback.
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

- [ ] `Keyring` / `RoleKeys` kotlinx-serialization data classes (Decision 2):
      single JSON document, `schema` field, `content` + `tokenHmac` roles each
      with `active` + `keys{ id → base64 }`. One parser.
- [ ] `ServerSecretProvider` interface returning the keyring **string**; explicit
      selection via `ServerConfig` (`secret.provider = file | env`, default file),
      mirroring `emailProvider`/`storage.type`.
- [ ] **File** provider (configurable path; default grandfathers
      `~/hammer_data/server.secret` → `content.v1`, reading its **exact** current
      bytes — do NOT re-encode) and **Env** provider (one var = keyring JSON).
- [ ] `ServerSecretManager` parses the keyring; new keys use canonical
      **Base64-of-32-bytes**.
- [ ] Extend the tag to `(algorithm, keyId)` → `aesgcm:v1`. Store tags with the
      active key id; registry/resolver picks `(algo, key)`.
- [ ] **CLI subcommands** via the existing **kotlinx-cli** `ArgParser` in
      `Application.kt` (add `Subcommand`s alongside `--config`/`--dev`/
      `--migrate-dry-run`) — **no new dependency**: `generate-keyring` (both roles,
      `v1`, `active: v1`; stdout default, `--out <path>`), `inspect-keyring`
      (ids/active, no bytes). `rotate-key` lands with PR5. Progress/feedback is
      plain log lines (works in non-TTY Docker/systemd), not an animated UI.
- [ ] **No auto-generation, no auto-heal.** Missing keyring → fail fast with
      guidance (the generate command + where to put it for the provider). Nothing
      mints a key on boot.

**Acceptance:**
- [ ] Existing single-`server.secret` deployment boots unchanged, data still
      decrypts (grandfathered as `content.v1`).
- [ ] Fresh deployment with no keyring fails fast with a helpful message naming
      the generate command + target location.
- [ ] Env provider works end-to-end (set var → boot → decrypt).
- [ ] `generate-keyring` output parses, has both roles, valid base64/32-byte keys,
      and is usable by both providers.

---

## PR4 — Split content key from token-HMAC key

**Goal:** the two roles become separate keys so the content key can be retired
without a forced global re-login. Lightweight — no rotation machinery (token
rotation is just automatic re-login on boot).

- [ ] `SimpleFileBasedAesGcmKeyProvider` uses `keyring.content`; `TokenHasher`
      uses `keyring.tokenHmac`. (Generator already emits both roles from PR3;
      grandfathered v1 is the same bytes for both initially.)
- [ ] Resolve the open PR3/PR4 sequencing: whether `TokenHasher` reads
      `keyring.tokenHmac` in PR3 already or flips here.

**Acceptance:**
- [ ] Existing tokens still verify; existing content still decrypts.
- [ ] Deleting the content key (after content convergence) leaves auth working;
      rotating the token-HMAC key invalidates sessions → re-login, content
      untouched.

---

## PR5 — Blocking pre-launch convergence (enable / disable / rotate)

**Goal:** one offline convergence engine for all three operations, with a
provable "old key unused" line. Adds the `rotate-key` CLI subcommand.

- [ ] Convergence engine: walk rows where `tag != target`, re-crypt in **per-row
      (or small-batch) transactions** (decrypt-then-write as one unit). Targets:
      `aesgcm:vN` (enable/rotate) or `none` (disable). Normalize NULL → `none`.
- [ ] **Resumable by construction** — the tag column is the progress ledger;
      restart re-scans.
- [ ] **last-applied marker** in `ServerConfigDao`: skip the scan entirely when
      configured target == last-applied (instant normal boots). Run only on an
      actual mode/key change, then update the marker.
- [ ] Gate **blocks serving** until convergence completes.
- [ ] Progress logging; document **startup probe** (not liveness) so a long
      maintenance boot isn't killed mid-run.
- [ ] `rotate-key --role content` CLI: read keyring, add `vN+1`, set active, emit.
      (Offline flow: rotate-key → place keyring → restart → gate re-encrypts.)

**Acceptance:**
- [ ] Enable: mode=aes on a plaintext DB → restart → every row `aesgcm:v1`.
- [ ] Disable: mode=none on an AES DB → restart → every row plaintext; content
      key provably unused → deletable with zero auth impact (PR4).
- [ ] Rotate: `rotate-key` → restart → every content row on the new key; old key
      count 0 → removable.
- [ ] `kill -9` mid-gate leaves consistent mixed state; next boot resumes/finishes.
- [ ] Normal boot (no change) does not scan the table.

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
