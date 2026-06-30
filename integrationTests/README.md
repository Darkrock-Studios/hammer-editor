# Sync integration tests

End-to-end sync tests that run the **real client sync engine against a real server**. Each test
spins up an in-process Jetty server ([`RoundTripTestBase`](src/jvmTest/kotlin/com/darkrockstudios/apps/hammer/integration/RoundTripTestBase.kt))
and drives one or more fully-wired clients ([`HeadlessClient`](src/jvmTest/kotlin/com/darkrockstudios/apps/hammer/integration/HeadlessClient.kt))
through it. Unlike the server-side `:server` e2e tests, which drive the HTTP API by hand, these
exercise the client's store / hash / conflict logic — the layer where divergence bugs actually live.

## The model-free oracle

The hard part of testing sync is knowing the expected end state. We mostly avoid computing it, and
instead assert two properties that any correct sync must satisfy — both on `RoundTripTestBase`:

- **`assertConverged(projectName, vararg clients)`** — every client holds exactly the entity set the
  server holds, hash for hash. Doesn't care *what* the entities are, only that the sides agree.
- **`assertResyncSilent(client)`** — an immediate extra sync moves nothing over the wire. Built on
  **`tapWire()`**, an `HttpSend` interceptor that records real traffic (`download_entity` 200 vs 304,
  `upload_entity`). Any client/server hash divergence surfaces here as a re-download or re-upload.

`tapWire()` is the workhorse: assert what actually crossed the wire, not what the client claims it
did. The null-timestamp re-download bug was a `200` where a `304` belonged.

## The three regimes

Sync output is a function of `(client baseline, client ops, server ops)`. New tests should slot into
one of these:

| Regime | Setup | Oracle |
|---|---|---|
| **First-time** | client empty, server has entities | every entity pulled; client converges to server |
| **No-change** | nothing changed since last sync | `assertResyncSilent` — zero wire transfer |
| **Mixed** | creates / edits / deletes on one or both sides | `assertConverged` + `assertResyncSilent`; conflicts only where both sides touched the same entity |

## Coverage map

**First-time**
- `ServerDownloadsEntityTest` — server-only scene lands on a clean client
- `TwoDeviceSyncTest` (`a second device downloads…`) — a second device adopts an existing project

**No-change**
- `ResyncStabilityMatrixTest` — every entity type × edge-case field values, server-originated, resync silent
- `UploadResyncStabilityMatrixTest` — same matrix for client-created entities
- `ResyncDownloadsNothingTest` — all types in one project; first sync pulls + heals, second is silent
- `SyncHashStabilityTest`, `EditResyncNoConflictTest`, `ResyncBaselineScenariosTest`, `EntityTypeResyncMatrixTest`, `SyncedHashBackfillTest` — targeted baseline / hash-agreement cases

**Mixed**
- `MixedSyncFuzzTest` — seeded property test: random create/edit/delete/rename across all types, converge + silent
- `TwoDeviceSyncTest` (`independent edits…`) — two devices, disjoint edits, converge
- `IndependentEditsTest`, `ClientDeletionTest`, `ClientUploadsEntityTest`, `ServerOriginatedEntitiesTest` — specific transitions
- `ConflictPickClientTest`, `ConflictPickServerTest` — the conflict sub-case (both sides touch one entity)
- `SyncFuzzTest` — single-entity edit/rename fuzz (legacy; `MixedSyncFuzzTest` is the broader net)

## Adding tests

- **A new scenario**: extend `RoundTripTestBase`, drive `HeadlessClient`s, and finish with
  `assertConverged` / `assertResyncSilent` rather than hand-rolled state checks.
- **Two devices on one project**: `secondDeviceFor(primary, localName)`. The primary must have synced
  once. A second device that creates entities after adopting should re-open its editor
  (`initializeSceneEditor()`) first, mirroring a real session re-deriving its next id.
- **More fuzz coverage**: add seeds to `MixedSyncFuzzTest.SEEDS` (a failure prints the seed + iteration
  to replay).
- **Scripted "other device" changes**: `seedServerEntity` / `mutateServerEntity` /
  `seedServerEntityDeletion` (+ bump `last_id`) set server state directly — more faithful than a second
  real client for server-originated changes, since it sidesteps shared client-side id allocation.
