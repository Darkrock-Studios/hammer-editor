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
- [ ] **Grandfather golden-corpus test** (Layer 2): a checked-in DB + `server.secret`
      from the current released server still decrypts under new code — **including
      a secret with non-UTF-8-clean bytes** (lossy-secret trap) and **token
      continuity**. Build this first; it's the C1/C5 guard.

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
- [ ] **Refine the downgrade guard** (`EncryptionModeGuard`): distinguish an
      *explicit* `mode=none` (→ converge AES→plaintext) from an unspecified/default
      `none` with encrypted data present (→ hard stop). Needs the mode setting to
      become unspecified-aware (nullable). Also add the second trigger: keyring has a
      content key + `mode=none` → hard stop.
- [ ] **Over-cap handling.** Enabling encryption can push a near-`MAX_ENTITY_CONTENT_LENGTH`
      plaintext row over the cap once AES+base64+IV+tag. Decide behavior
      (skip-and-report vs. fail the migration) — must not silently drop or crash.
- [ ] **Convergence dry-run** (mirrors `--migrate-dry-run`): decrypt+re-encrypt in
      a rolled-back transaction, report per-tag counts + any over-cap rows, commit
      nothing. Operator confidence tool + clean test surface.

**Acceptance:**
- [ ] Enable: mode=aes on a plaintext DB → restart → every row `aesgcm:v1`,
      decrypts to original.
- [ ] Disable: mode=none on an AES DB → restart → every row plaintext; content
      key provably unused → deletable with zero auth impact (PR4).
- [ ] Rotate: `rotate-key` → restart → every content row on the new key; old key
      count 0 → removable.
- [ ] **Crash/no-loss** (C2): failure after N of M rows → re-run finishes, nothing
      lost; failure between decrypt and write → that row keeps its readable original.
- [ ] **Completion signal never false-positives** (C4): per-tag tally is accurate
      mid-run, hits 0 only when truly done.
- [ ] `kill -9` mid-gate leaves consistent mixed state; next boot resumes/finishes.
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
