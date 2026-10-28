# How to Add an Entity Type

An **entity** is a unit of synced project content with a project-unique integer id: today
Scene, Note, TimelineEvent, EncyclopediaEntry, SceneDraft and Image. Adding one touches `:base`,
`:common`, `:composeUi`, `:desktop`, `:android`, `:operations`, `:plugins:wasmhost`, `:server`
and `:integrationTests`. This guide lists every touch point, grouped by layer, and calls out the
ones the compiler will not catch.

Each item is tagged with what happens if you skip it:

- **Forced**: an exhaustive `when` over `EntityType`, `ApiProjectEntity.Type` or a sealed class.
  The build breaks until you add the branch. These are listed for completeness; you cannot miss
  them.
- **Runtime**: compiles, then fails when the code runs. Usually a missing Koin definition or a
  test fixture that enumerates types.
- **Silent**: a hand-written list, map, `else` branch or default. Nothing fails. Behaviour is
  quietly wrong, often in a way that only shows up on a second device or a second sync.

Work through the sections in order. Sync (section 3) and hashing (section 2) are where the
expensive mistakes live, so they come first.

Use Note as the tracer when copying: it is the simplest complete type. Use SceneDraft when the
new type is owned by another entity, and Image when other entities refer to it or it carries
binary content.

## 1. Decide before you code

Answer these up front. Each one changes which later sections apply.

| Question | If yes |
|---|---|
| Is it owned by another entity (like drafts by scenes)? | The parent's `reIdEntity` must re-id children too (see `ClientSceneSynchronizer.reIdEntity` calling `draftRepository.reIdScene`). Place it after the parent in the server update sequence (3.4). |
| Does it carry tags? | Add to `TaggedEntityType`, `TagIndexService`, `THEMES_SOURCE_ORDER`, and the operations `EntityKind` (section 5). |
| Should global search find it? | `GlobalSearchFilter`, `SearchResult`, `SearchProjectUseCase`, and the operations `SearchFilter` (sections 5 and 6). |
| Should plugins and the CLI see it? | `CatalogKind`, `ActionPlace`, `EntityRenumbering`, new operations, and the plugin docs (section 8). |
| Can the server create it (like review drafts)? | Follow the server-originated pattern in `ReviewRepository` (section 9). |
| Does it hold prose that names encyclopedia entries? | `RenameEntryUseCase` and `RenamePlace` (section 5). |
| Do other entities reference it by id (in text or lists)? | Its `reIdEntity` must rewrite those references and return the referrers' ids (3.5). Follow `ImageReferenceRewriter`: one holder per kind of referrer. Place it **before** its referrers in the server update sequence (3.4) and early in `CROSS_TYPE_PRECEDENCE` (4.3). |
| Is it binary or large? | Inline base64 on the wire, hashed as decoded bytes (2.3). Shared validation in `:base`, enforced by client and server (3.4, 4.1). Check the reverse proxy body size in [HOW-TO-RUN-A-SERVER.md](HOW-TO-RUN-A-SERVER.md). |
| Does existing on-disk data need transforming? | A `DataMigrator` step and a `PROJECT_DATA_VERSION` bump (section 4). Otherwise no migration is needed; folders are created lazily. |

**Protocol version.** Old clients cannot sync a project that contains a type they do not know:
the download parses the `X-Entity-Type` header with `ApiProjectEntity.Type.fromString`, gets null,
and throws. The `4.0.0` branch already bumped `HAMMER_PROTOCOL_VERSION` to 4, so a type that ships
in 4.0 rides that bump. A type added after 4.0 ships needs its own bump (or the server must filter
the update sequence by client version, which does not exist today). The "no bump needed" guidance
in [SYNCING-PROTOCOL.md](SYNCING-PROTOCOL.md#server-storage-is-shape-agnostic) is about adding a
*field*, not a *type*.

## 2. Hashing

This is the part that has gone wrong most often. Read it even if you only skim the rest.

### 2.1 Why it matters

Sync decides what to transfer by comparing hashes, never by comparing content. The server stores
one hash per entity. The client records the hash it last synced as the conflict baseline. If any
two hashes of the same content disagree, one of these happens, and no test fails unless you
wrote one:

- The entity is re-downloaded or re-uploaded on **every** sync, forever ("ping-pong").
- A field never converges between devices because the hasher ignores it.
- A **phantom conflict** appears: the user is asked to resolve a conflict between two identical
  entities.

### 2.2 Every entity has two hash paths that must agree

1. **The API model**: `ApiProjectEntity.XEntity.hash()` delegates to `EntityHasher.hashX(...)` in
   `base`. The server uses it when storing, when checking conflicts and when answering downloads.
   The client uses it right after an upload or download to lock in the baseline.
2. **The local model**: `ClientXSynchronizer.getEntityHash(id)` rebuilds the digest **from the
   repository's on-disk representation** and calls `EntityHasher.hashX(...)` directly. The client
   uses it to report its state to the server, to detect local edits, and in
   `healServerIfEnriched`.

Both paths must produce the same bytes for the same entity. The second path is where fields get
dropped, defaulted or normalised differently (trimmed strings, a missing timestamp read back as
epoch 0, a name that does not survive the filename encoding). There is **no shared code** between
them beyond the `EntityHasher` function itself, so every field you add to the API model must be
threaded through `getEntityHash` and `createEntityForId` by hand.

Rule of thumb: `createEntityForId(id).hash()` must equal `getEntityHash(id)` for every entity the
repository can produce. Write that assertion as a test (see 2.5).

### 2.3 Writing `EntityHasher.hashX`

Copy the shape of `hashNote` or `hashSceneDraft` in
[EntityHasher.kt](../base/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/base/http/synchronizer/EntityHasher.kt)
and follow these invariants:

- **Every serialized field goes in**, including `id`. The polymorphic `type` discriminator is the
  one exception.
- **Sets and maps are sorted** before hashing. Iteration order is not stable across platforms.
- **Two adjacent collections get a size prefix** each. Without it `{a=[7], b=[]}` hashes the same
  as `{a=[], b=[7]}` (see the `confirmedReferences` / `dismissedReferences` comment in
  `hashScene`).
- **Nullable fields hash a presence byte first**, then the value. A missing `Instant` must differ
  from epoch 0 (see `created` / `lastEdited` in `hashScene`).
- **Adjacent strings and byte arrays get a length prefix** in a new type's hasher, so `"ab"` + `"c"`
  differs from `"a"` + `"bc"` (see `hashImage`). The older hashers lack them and cannot gain them
  without changing every stored hash, so this is a convention for new types only.
- **Hash decoded content, not its wire encoding**: `hashImage` takes the bytes, not the base64.
- **Order of `d.update` calls is part of the format.** Do not reorder existing calls when adding
  a field; append.
- Keep `hash()` **in `ApiProjectEntity.kt`, directly below the field list** it hashes. The KDoc on
  `ApiProjectEntity.hash` explains why: adding a field puts the hasher in your diff.

### 2.4 Adding a field to an existing type later

Existing stored hashes (server rows, client `lastSyncedHash` baselines) must stay byte-identical
for data that does not use the new field, or every project re-syncs everything and reports phantom
conflicts. So the **default or absent value must contribute zero bytes**:

```kotlin
// Zero bytes at the default (false): pre-existing hashes must stay identical.
if (excludeFromDictionary) {
	d.update(1, buf)
}
```

See `excludeFromDictionary` in `hashEncyclopediaEntry` for the pattern, and
[SYNCING-PROTOCOL.md](SYNCING-PROTOCOL.md#server-storage-is-shape-agnostic) for the mixed-version
behaviour that results. Collections follow the same rule: `images` in the same hasher contributes
nothing when empty, else its size and then the ids.

The server has a read-repair for the case where the hash *algorithm* changes
(`ProjectEntityDatabaseDatasource.repairStaleHash`), but it cannot help with a client that
hashes differently from the API model. Two more guards keep a changed hash from raising false
conflicts:

- `ServerEntitySynchronizer.checkForConflict` recomputes the stored entity's hash on a mismatch and
  accepts a baseline equal to it, so a stale hash column does not conflict before a download has
  repaired it.
- The client records its local hash as the baseline on a `304` (`EntityTransferOperation.downloadEntry`).

A migration that changes an entity's content or its hash must also set each **clean** touched
entity's baseline (`syncedHashes`) to the hash of its pre-migration content under the current
hasher. That is the hash the server recomputes from what it stores, and the value read-repair
writes, so the upload passes whether or not another device downloaded first. See
`EntryImagePromoter.recordInSyncJournal`. Leave a dirty entity's baseline alone.

### 2.5 Guards that already exist, and the ones you must add

| Guard | What it catches | New type: what to do |
|---|---|---|
| `EntityHashSensitivityTest` (server tests) | A serialized field that does not affect `hash()`. Reads the `@Serializable` descriptor, so new fields and nested DTOs are caught automatically. Its second test fails until every `ApiProjectEntity.Type` has a spec. | **Runtime**. Add an `xSpec` with a mutation for every field. This is the one guard that enforces itself. |
| `EntityHasherTest` (base tests) | Golden hash values per type, determinism, sort-independence. | Add a `hashX` golden test. Not enforced. |
| `EntityHasherExtTest` (server tests) | `hash()` equals a direct `EntityHasher.hashX` call. The test's own comment warns this is a tautology if both sides forget the same field. | Add a block. Low value on its own. |
| `ClientXSynchronizerTest` | Local-model hash equals API-model hash. See `ClientImageSynchronizerTest` ("Local hash equals the API hash after a store, for every format and name shape") and `ClientEncyclopediaSynchronizerTest`. | **Write this.** Store an entity through `storeEntity`, then assert `getEntityHash(id) == createEntityForId(id).hash()`. Cover null and populated optional fields, and every field the on-disk format could normalise. |
| `ResyncStabilityMatrixTest` (integrationTests) | Ping-pong. Seeds server entities, syncs twice, asserts the second sync moves nothing on the wire. Sweeps every type across null/empty/populated edge cases. | **Add cases.** This is the end-to-end proof that the two paths agree, with the real server and real files. |
| `UploadResyncStabilityMatrixTest` | Same, for client-created entities. | Add cases. |
| `ResyncDownloadsNothingTest` | One entity of every type, second sync downloads nothing. | Add one entity, bump the id assertions. |
| `EntityTypeResyncMatrixTest` | Edit on one side, resync, converge, per type. | Add a test. |

Nothing ties the integration tests to the `Type` registry. If you skip them, nothing tells you.

## 3. Sync

### 3.1 Shared `base` module

All in `base/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/base/http/`.

- `EntityType.kt`: add the constant. This is the **client-side** dispatch enum.
- `ApiProjectEntity.kt`:
  - Add `@Serializable data class XEntity(override val type: Type = Type.X, override val id: Int, ...)`
    with `hash()` (section 2.3). Every `when (entity)` over the sealed interface becomes **Forced**.
  - Add `X(n)` to the `Type` enum, next free int. `toStringId()` is **Forced**.
  - `Type.fromString()` ends in `else -> null`: **Silent**. Add the string. The upload header is
    written with `type.name` and parsed with `fromString(lowercase)`, so `toStringId()` must equal
    `name.lowercase()`. Miss this and downloads throw "Missing entity-type header".
- `synchronizer/EntityHasher.kt`: `hashX` (section 2.3).
- `synchronizer/EntityConflictException.kt`: add `XConflictException`. `fromEntity` is **Forced**.
- `ProjectApi.kt`: add a `LoadEntityResponse(x: XEntity)` secondary constructor. **Silent**, but the
  client API uses this shape.
- `JsonSerializer.kt`: nothing. There is no polymorphic `SerializersModule`; every wire call uses the
  concrete class serializer.
- `ClientEntityState`, `ProjectContentHasher`: generic, nothing to do.

### 3.2 Client synchronizer

In `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/data/sync/projectsync/`.

- Add `synchronizers/ClientXSynchronizer.kt` extending `EntitySynchronizer<ApiProjectEntity.XEntity>`
  and `ProjectScoped`, with the repository via `projectInject()`. Copy `ClientNoteSynchronizer`.
  - `prepareForSync` must leave the store loaded; `DuplicateIdService` calls it before hashing.
  - `getEntityHash` and `createEntityForId` must agree (section 2.2).
  - `hashEntities(newIds)` must exclude `newIds`.
  - `storeEntity` may validate the incoming entity and return false to reject it
    (`ClientSceneDraftSynchronizer` does this for path traversal in names; `ClientImageSynchronizer`
    runs `ImageFormat.validate`). Validate anything that becomes a filename.
  - `reIdEntity` returns the ids of other entities whose content it rewrote (3.5); `emptySet()`
    when nothing refers to the type.
  - `deleteEntityLocal` logs a string resource such as `sync_note_deleted`.
- `EntitySynchronizers.kt`:
  - Add `val xSynchronizer: ClientXSynchronizer by projectInject()`.
  - Add to the `synchronizers` map. **Silent**, and the worst miss on the client: a type absent from
    the map is invisible to ownership, hashing, prepare, finalize and delete, so it never uploads and
    its hashes are left out of the project content hash. Map order also decides ownership when two
    synchronizers claim the same id.
  - `get`, `handleConflict`, `reIdEntry`, `getLocalEntityHash`: **Forced**.
- `SceneTypeConverter.kt`: `ApiProjectEntity.Type.toEntityType()` is **Forced**. It is the only
  API-type to `EntityType` mapping.
- `operations/EntityTransferOperation.kt`: the `storeEntity` dispatch is **Forced**. Everything else
  in `operations/` is generic.
- `common/.../server/ServerProjectApi.kt`: upload `setBody`, conflict decode and download decode are
  **Forced**. The `fromString` parse is the **Silent** one from 3.1.
- Koin: `scoped<ClientXSynchronizer>()` in the `ProjectDefScope` block of
  `common/.../dependencyinjection/mainModule.kt`. **Runtime**.

### 3.3 Conflict resolution UI model

- `common/.../components/projectsync/ProjectSynchronization.kt`: add `EntityConflict.XConflict` and
  `EntityMergeError.XMergeError`.
- `ProjectSynchronizationComponent.kt`: `resolveConflict` and `onConflict` are **Forced**. Add
  `onXConflict` (builds the local `ApiProjectEntity` from the repository, sets `conflictTitle`) and
  `validateX`.
- `composeUi/.../projectsync/`: add `XConflict.kt` copying `NoteConflict.kt`. The dispatch and
  `rememberConflictSummary` in `ProjectSynchronizationUi.kt` are **Forced**. Labels come from
  `sync_conflict_entity_label_*`.
- `composeUi/src/desktopMain/.../preview/ProjectSynchronization.Preview.kt`: add a conflict preview.

### 3.4 Server

In `server/src/main/kotlin/com/darkrockstudios/apps/hammer/`.

- Add `project/synchronizers/ServerXSynchronizer.kt` extending
  `ServerEntitySynchronizer<ApiProjectEntity.XEntity>`. Override `entityType`, `entityClazz` and
  `pathStub` (lowercase name). Copy `ServerNoteSynchronizer`. Override `getUpdateSequence` only if
  the type needs ordering, as `ServerSceneSynchronizer` does for groups.
- Override `validate(entity)` when the server must refuse content. It runs before the conflict
  check, on `force` uploads too. Return `EntityValidationException` (`400`) or
  `EntityTooLargeException` (`413`); `ProjectRoutes.kt` maps both. See `ServerImageSynchronizer`.
- `project/ProjectEntityRepository.kt`:
  - Add the `by inject()` field.
  - `saveEntity`, `deleteEntity`, `loadEntity`: **Forced**.
  - `getUpdateSequence`: **Silent**, and the worst miss on the server. It is a hand-written list of
    `updateSequence += xSynchronizer.getUpdateSequence(...)`. A type missing here is never offered
    for download, so other devices never receive it. Insert it **after any type it references**
    and before any type that references it (drafts follow scenes; images come first).
- `project/ProjectRoutes.kt`: `uploadEntity` receive, `respondConflictedEntity` and
  `respondDownloadedEntity` are **Forced**.
- Koin: `single<ServerXSynchronizer>()` in `dependencyinjection/mainModule.kt`. **Runtime**.
- Persistence: nothing. All types share the `story_entity` table (SQLDelight, Postgres) with a
  `type` text column and encrypted JSON content. No schema change, no migration, unless you add
  per-type columns (then `server/src/main/sqldelight/migrations/N.sqm` and the `version` in
  `server/build.gradle.kts`).
- `StoryEntityDao` throws on an unknown `type` string. Rows of a new type make an older server
  binary fail to list the project's entities, so do not roll a server back past the release that
  added the type.

### 3.5 Entities that other entities reference

When content holds the new type's id (Markdown links, id lists, a project data field), a re-ID must
rewrite it, and the rewritten referrers must upload in the same sync. The plumbing is generic:
`EntitySynchronizers.reIdEntry` puts `reIdEntity`'s return value in
`EntityIdChange.rewrittenReferrers`; `IdConflictResolutionOperation` adds each referrer to the dirty
list with its synced baseline and to `CollatedIds.rewrittenIds`; `EntityTransferOperation` visits
`rewrittenIds` even when the server's `idSequence` does not list them; `DuplicateIdService` marks
them dirty after a cross-type renumber. Per type:

- `reIdEntity` returns the referrers. Returning `emptySet()` while something refers to the type
  compiles and is **Silent**: the sync clears the dirty list and the next one downloads the server's
  unrewritten copy over the rewrite.
- Rename or move the entity first, then rewrite, so a reference never points at an id with no
  entity (`ClientImageSynchronizer.reIdEntity`).
- Write through whatever owns the live state, never the datasource behind it: a loaded scene
  through `SceneEditorService.rewriteSceneText` (an open editor's next save would undo a file
  write), project data through `ProjectDataRepository.updateData` (its sync reads the cache).
- A rewrite of local-only state (unrestored scene edits, temp buffers) must **not** report the
  entity: syncing it would upload its unchanged file over newer server changes. Project data is not
  an entity either; its holder reports nothing and its own phase uploads it.
- Guard each holder and each item: the rewriter logs and skips, never throws.
- The deleting device removes references before the entity; `deleteEntityLocal` on other devices
  deletes only the entity, since the referrers' rewrites arrive as their own updates.

## 4. Client storage and ids

In `common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/data/`.

### 4.1 Datasource and repository

- `XDatasource` owns the files: directory constant, filename pattern, static path helpers, read,
  write, delete, `reId`. Copy `notesrepository/NotesDatasource.kt`. Create the directory lazily in
  the getter; project creation does not pre-create per-type folders.
- `XRepository` owns in-memory state and change flows. Copy `notesrepository/NotesRepository.kt`:
  create claims `idAllocator.claimNextId()` then `markForSync`; delete calls
  `syncJournal.recordIdDeletion(id)`; sync writes with `markForSync = false`.
- Architecture tests (`architectureTests/`) constrain naming and layering: the suffix is
  `Datasource` (not `DataSource`), a Repository may reference only Datasources and foundation
  classes, and `writeToml` / `readToml` may only appear in files whose name contains `Datasource`
  or in `data.migrator`.
- Koin: `scoped<XDatasource>()` and `scoped<XRepository>()` in the `ProjectDefScope` block.
  **Runtime**.
- If the store needs explicit initialization, start it in `ProjectEditorScopeUtils` with the
  others. Note that `DuplicateIdService.rationalizeOnOpen()` runs before any store loads.
- `projectsrepository/ProjectsRepository.projectContentMarkers`: **Silent**. The list of top-level
  entries that mark a folder as Hammer content, used to adopt and heal unmarked project
  directories. A new top-level folder is not recognised until it is added.
- Rules both sides enforce live in `:base` (`base/.../image/ImageFormat.kt`) and are called from
  the repository, `storeEntity` and the server's `validate`.
- A name that becomes a filename is encoded with `FilenameEncoding` (in `:base`;
  `ProjectsRepository.encodeForFilename` delegates to it) and must round-trip
  (`FilenameEncoding.roundTrips`, used by `ImageFormat.isValidName`). A name that reads back
  different hashes differently, so the server refuses it too.
- Code that runs without a project scope (the project list) needs top-level helpers that take
  `ProjectDef` and `FileSystem`: `findImageFile` and `readImageFileStamp` in `ImagesDatasource.kt`.
- Expensive hashes are cached by file stamp (`ImagesRepository.contentHash`, keyed by
  `ImageFileStamp`), and screens that only need a cache key never hash
  (`ImagesRepository.cachedContentHash`, else `ImageFileStamp.cacheToken`).

### 4.2 Id allocation

- Add `id/datasources/XIdDatasource.kt` implementing `IdDatasource`. Copy `NotesIdDatasource`. The
  interface lets `findIds` return **null** when storage cannot be read; if your read can fail,
  return null, never an empty set.
- `id/IdAllocator.getIdSources()`: **Forced**, but each branch resolves from Koin, so also
  **Runtime**: `factory<XIdDatasource>()` in `mainModule.kt`.
- Why null matters: `SyncJournal` rebuilds itself from the same id sources, and any id missing
  from **every** source is inferred as **deleted** and removed from the server at the next sync. An
  incomplete id datasource causes server-side deletes.

### 4.3 Duplicate and cross-type ids

In `duplicateids/`. Read [Renumber items that share an id across types](https://github.com/Darkrock-Studios/hammer-editor/pull/1029)
for the background.

- `DuplicateIdSet.DuplicateIdKind`: add a kind if the type's files can be duplicated on disk.
  Makes `DuplicateIdService.reload` and the `DuplicateIdsDialog` `when`s **Forced**.
- Add `XDuplicateIdHandler` extending `DuplicateIdHandler`. Override `entityType` and `ids()`;
  their defaults (null, empty) are **Silent** and leave the type out of cross-type detection.
  Copy `NoteDuplicateIdHandler`, which rewrites the id inside the content on `claim`.
- `ProjectFileRationalizer.handlers`: **Silent**. Add the handler. A type with no handler must be
  special-cased like the timeline is, or its collisions go undetected.
- `DuplicateIdService.CROSS_TYPE_PRECEDENCE`: **Silent, and actively wrong when missed**.
  `keeperOf` picks `minBy { indexOf(it) }`; a type not in the list gets index -1 and **always wins
  as keeper**, so existing scenes and entries get renumbered instead of the new type. Add it in
  dependency order (most depended-on first). Image is first, since everything can reference it.
- `DuplicateIdService` lazily injects the repositories it reloads; add one if the new kind needs
  reloading.

### 4.4 Migrations

Only if existing on-disk data must change shape: add a `MigrationN_M`, register it in
`migrator/DataMigrator.kt` and `dependencyinjection/MigratorModule.kt`, and bump
`PROJECT_DATA_VERSION`. `Migration2_3` (draft filename format) is the per-type example;
`Migration4_5` (`EntryImagePromoter`) creates entities of a new type from old data. A brand new
type with new folders needs none of this.

- `Migration.migrate` is synchronous and runs before any project Koin scope. Use the scope-less
  helpers (`readProjectSyncData` / `writeProjectSyncData` in `SyncJournalDatasource.kt`) and
  construct id datasources directly. A migration that allocates ids lists its id sources by hand
  (`EntryImagePromoter.firstFreeId`): **Silent** for the next type.
- **Never throw.** A throw fails every launch. Catch per step, log, and carry on.
- **Make it re-runnable.** `DataMigrator.repairMigratedProjects` runs `EntryImagePromoter` on every
  launch for projects already at version 5, so whatever a failed step left behind is finished
  later. A second run over migrated data must change nothing.
- **Ship `XIdDatasource` with the migration** that creates the type's files: a journal rebuilt
  without it infers the new ids as deleted (4.2).
- Record in `sync.json` only when it exists: new ids in `newIds`, touched entities in `dirty`,
  `cachedProjectHash = null`, and baselines as in 2.4.
- Two devices migrating offline both mint entities for the same old data. Decide whether that is
  acceptable (for entry images, the entry conflicts and the spare Image stays unused).

## 5. Cross-cutting client indexes

All opt-in. Nothing forces any of these, so decide from section 1 and do them deliberately.

| Feature | Files | Tags |
|---|---|---|
| Tag index | `data/tagindex/TaggedEntityType.kt` (separate enum, no Draft today); `TagIndexService` flow merge and per-type `buildTagIndex` block; composeUi `ProjectStatsUi.THEMES_SOURCE_ORDER`, `colorForTagSource` (may need a new `HammerExtendedColors` entry), and the "all four" strings in `strings_project_home.xml` | **Silent** |
| Global search | `components/globalsearch/GlobalSearch.kt` (`GlobalSearchFilter`, `SearchResult`); `data/globalsearch/SearchProjectUseCase` per-type deferred and `includesX` flag; composeUi `GlobalSearchUi` icon/label/prefix/key `when`s (**Forced** once the sealed variant exists); `ProjectRootComponent.navigateGlobalSearchResult` | **Silent** in the use case |
| Entity catalog (picker, plugin entity fields) | `data/entitycatalog/EntityCatalog.kt` `CatalogKind` (string ids are public plugin API); `BuildEntityCatalogUseCase` `xItems()`; composeUi `EntityPickerDialog` glyph and label | **Forced** after the enum entry |
| Project statistics | `data/projectstatistics/ProjectStatistics` count field; `StatisticsService` count and assignment; `components/projecthome/ProjectHome.State`; `ProjectHomeComponent`; composeUi `ProjectStatsUi` count cell in both grid layouts | **Silent** |
| Encyclopedia rename | `encyclopediarepository/rename/RenameModels.RenamePlace`; `RenameEntryUseCase` scan, `readText`, `writeText` | Only if the type holds prose |
| References, spell check, writing activity, story export, backup | Scene or encyclopedia only; backup zips the whole folder | Nothing |
| Syntax embedded in Markdown | `countWords` (`base/.../markdown/WordCount.kt`) and `MarkdownProjector` must skip it; exporters must degrade it (`EpubStoryRenderer.kt` writes an image's alt text); a rewrite of an open scene runs `WritingSessionTracker.transformBaseline` | **Silent** |

## 6. Navigation and screens

### 6.1 Decompose components (`common/.../components/`)

Each feature is a `ChildStack` with Browse, View and Create configs. Copy `notes/` wholesale:
`Notes.kt` (interface, `Destination`, `@Serializable Config`), `NotesComponent.kt` (`createChild`,
`isAtRoot`, `shouldConfirmClose`, `isUnsaved`, `discardUnsaved`, child factories,
`childStack(key = "XRouter")`), `BrowseNotesComponent`, `ViewNoteComponent` (stateKeeper draft,
`BackCallback` while editing, `addMenu`/`removeMenu`, `isEditingAndDirty`) and
`CreateNoteComponent`. Browse and View/Create components extend `TagSuggesting` if tagged.

`isUnsaved` in the copied router has an `else -> false`: list your own View and Create
destinations. **Silent**.

Project root wiring:

- `projectroot/ProjectRoot.kt`: `showX()`, `Destination.XDestination`, `DestinationTypes.X` (text
  and short text resources; drives the rail, bottom bar and plugin place icons). `getLocationType`
  is **Forced**.
- `projectroot/ProjectRootRouter.kt`: `Config.XConfig`, the `x()` child factory, `showX` via
  `bringToFront`. `createChild` is **Forced**.
- `projectroot/ProjectRootComponent.kt`: inject the repository, override `showX`;
  `showDestination`, `navigateGlobalSearchResult`, `navigateToDeepLink`, `discardUnsaved` are
  **Forced**. Deep links resolve the id through the repository, then `showX()` and cast the active
  instance. See [DESIGN-PATTERNS.md](DESIGN-PATTERNS.md).
- `projectroot/CloseConfirm.kt` and `projectroot/ProjectDeepLink.kt`: add a variant each.
- Components must live under a `components` package and must not reference a Datasource
  (architecture tests).

Project-wide modals:

- Full-screen project modals share one `ChildSlot` (`ProjectRootModalRouter`, `@Serializable`
  configs, no lambdas), so only one shows at a time. Focus mode fills the slot, which is why it
  cannot open the gallery.
- To return a result from a modal, use a project-scoped request bus like `ImagePickRequests`:
  `pick(target)` emits a request, `ProjectRootComponent` opens the modal for it, and the modal
  `deliver`s exactly one result, null when cancelled. The `@Serializable` target
  (`ImagePickTarget`) says which screen asked.
- The asking component holds the pending result in its state and saves it and an in-progress flag
  with `stateKeeper`, so after process death it waits again with `awaitResult`
  (`SceneEditorComponent.restoreImagePick`).

### 6.2 Compose UI (`composeUi/.../`)

- New `x/` package: `XUi.kt` (children `when`, `XFab`), `BrowseXUi.kt`, `ViewXUi.kt` (add
  `saveShortcutModifier` for Ctrl+S and `DetailViewDropdownMenu(..., ActionPlace.X, id)` for plugin
  actions), `CreateXUi.kt`, `ConfirmDeleteXDialog.kt`. Copy `notes/`.
- `projectroot/ProjectRootUi.kt`: `getDestinationIcon`, `FeatureContent`, `ProjectRootFab` are
  **Forced**. Add `ic_x.xml` to `common/src/commonMain/composeResources/drawable/`.
- `projectroot/ProjectRootScaffold.kt`: `NAV_X_TAG` constant (**Silent**; also mirrored in
  `android/src/androidTest/.../EditorNavigationTest.kt` and `ios/iosUITests/HammerUITest.swift`).
  `CloseConfirm` dispatch is **Forced**. The rail and bottom bar iterate `DestinationTypes.entries`,
  so the item appears automatically; the compact bottom bar already holds five, check it still fits.
- `projectroot/ConfirmCloseDialogs.kt`: `ConfirmCloseUnsavedXDialog`.
- `composeUi/src/desktopMain/.../preview/x/`: previews with fake components. The fakes implement
  the component interfaces, so they break at compile time when the interface changes.
- A per-project service the editors need goes in a composition local provided once in
  `ProjectRootUi` (`LocalImageProvider`, provided by `ProvideProjectImages`, next to
  `LocalRichTextStyles`).
- Coil keys a file by its path, so load files with `rememberImageRequest(path, version)` and pass a
  version that changes with the bytes: the cached hash, else `ImageFileStamp.cacheToken`, the same
  token on every screen so a file keeps one cache entry. Call `evictImageFile` after a replace or
  delete.

### 6.3 Desktop, Android, iOS, Wear

- `desktop/.../ProjectEditorWindow.kt`: `CloseConfirm` `when` is **Forced** (a duplicate of the
  scaffold dispatch). `desktop/.../ConfirmCloseDialogs.kt`: add the dialog function.
- `desktop/.../DesktopLaunchArgs.kt`: add a `--x` launch flag to `mutuallyExclusiveOptions`. The
  usage error text hard-codes the flag list: **Silent**.
- `android/.../ProjectRootActivity.resolveDeepLink` handles only `ProjectDeepLink.Scene`. Existing
  gap for every non-scene type; extend it if you need Android deep links.
- iOS has no Kotlin entity wiring. Wear knows only notes and story ideas and reads pending sync
  counts generically; nothing to do unless the type should be capturable from the watch.

### 6.4 Strings

Compose Multiplatform resources under `common/src/commonMain/composeResources/values/`. Add
`strings_x.xml` for the feature; Crowdin's globs already cover new files. Mirror the per-entity
keys in the shared files:

- `strings_project_navigation.xml`: `project_nav_x`, `project_nav_x_short` (four uppercase
  characters).
- `strings.xml`: `unsaved_x_dialog_title`, `unsaved_x_dialog_message`.
- `strings_globalsearch.xml`: `global_search_source_x`, `global_search_filter_x` (also the picker
  tab label).
- `strings_project_home.xml`: `project_home_stat_num_x`, `project_home_stat_themes_source_x`, and
  the "all four" captions if the type is tagged.
- `strings_sync.xml`: `sync_conflict_x_title`, `sync_conflict_entity_label_x`,
  `sync_conflict_title_x_field_*`, `sync_x_deleted`.
- [KEYBOARD-SHORTCUTS.md](KEYBOARD-SHORTCUTS.md): add the view screen to the Editors table.

## 7. Operations and plugins

In `operations/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/operations/`.

- `core/ContentOperations.kt`: `x.list` and `x.read` plus DTOs. `core/ContentWriteOperations.kt`:
  `x.create`, `x.update`, `x.delete` with the right `access`, plus a `requireX` helper. Operations
  are `operation<In, Out>(name, description, access, scope)`; JSON schemas derive from the
  `@Serializable` types. The CLI, the MCP plugin and WASM plugins pick them up through
  `OperationRegistry` with no further wiring. Names are dotted lowercase.
- `core/SearchOperations.kt`: `EntityKind` is **Forced** against `TaggedEntityType`; `SearchFilter`
  mirrors `GlobalSearchFilter` by hand (**Silent**); `buildTagIndex` awaits each tagged
  repository's flow by hand (**Silent**); two operation descriptions hard-code the type list
  (**Silent**).
- `core/StatsOperations.kt`: count fields (**Silent**).
- `plugin/PluginAction.kt` `ActionPlace`: string ids are public plugin API. `of()` uses `entries`,
  so manifest validation accepts the new id automatically.
- `plugin/EntityRenumbering.kt`: `place` and `kind` are **Forced** over `EntityType`.
- composeUi `PluginActionDialogs.openActionTarget` and `RuntimePluginsSection.destination()` are
  **Forced** after the `ActionPlace` entry.
- `plugins/wasmhost/.../PluginManifest.kt` doc comment lists place ids. Update it.
- [CLIENT-PLUGINS.md](CLIENT-PLUGINS.md): the operation table, search filters, `stats.project`
  contents, place ids, launch flags, and the picker naming section all enumerate types.

## 8. Server-originated entities

Only if the server creates entities of the new type (review drafts are the example). Follow
`server/.../review/ReviewRepository.kt`: hold the session with `withInternalSyncSession`, reserve
the id range by bumping `lastId` via `updateSyncData` from
`max(lastId, findLastId, max deletedId) + 1`, save with `originalHash = null, force = true`, and
roll back with `deleteEntity` on failure.

## 9. Tests to update

Everything here fails at **runtime** or not at all. None of it is compiler-checked.

### 9.1 `base`

- `EntityHasherTest`: golden `hashX` test.

### 9.2 `common` (`common/src/desktopTest/kotlin/`)

- `synchronizer/MockSynchronizers.kt`: `@MockK` field, `synchronizers` list, `addSynchronizers`.
- `synchronizer/EntitySynchronizersTest.kt`: per-type tables and verify-none lists.
- `synchronizer/ClientXSynchronizerTest.kt`: new, copy `ClientNoteSynchronizerTest`. Include the
  hash parity assertion from 2.5, `getEntityHash(id) == createEntityForId(id).hash()`, over every
  name shape and format (`ClientImageSynchronizerTest`). Add a `...SecurityTest` if any field
  becomes a filename.
- If the type is referenced (3.5): `synchronizer/operations/IdConflictResolutionOperationTest` and
  `EntityTransferOperationTest` already cover the plumbing; test the rewriter's holders
  (`references/ImageReferenceRewriterTest`).
- If hashes are cached or lazy: assert the screen that opens a list hashes nothing
  (`ImageGalleryComponentTest` "Opening the gallery hashes nothing, …") and that the cache never
  reads the file (`ImagesRepositoryTest` "cachedContentHash only answers from the cache …").
- A migration: `datamigrator/MigrationN_MTest` including a second run that changes nothing, and
  the mock in `DataMigratorTest`.
- `synchronizer/operations/FetchLocalDataOperationTest.kt`: `is ClientXSynchronizer` dispatch.
- Koin registrations of every `IdDatasource` (`getIdSources` resolves all of them):
  `repositories/id/IdAllocatorTest`, `repositories/syncdata/SyncJournalTest`,
  `repositories/duplicateids/DuplicateIdServiceTest`, `CrossTypeIdRenumberTest` (also every
  synchronizer).
- `repositories/duplicateids/CrossTypeIdsTest` "Every type takes part".
- `repositories/id/datasources/XIdDatasourceTest`, `repositories/x/XRepositoryTest`: new.
- Component tests: `components/x/*`, `components/projectroot/ProjectRootComponentTest`
  (`showDestination`, deep link), `components/projectsync/ProjectSynchronizationComponentTest`,
  `components/projecthome/ProjectHomeComponentTest` (counts, tag breakdown),
  `components/globalsearch/*`.
- Data: `usecases/globalsearch/SearchProjectUseCaseTest`, `usecases/entitycatalog/EntityCatalogTest`,
  `repositories/tagindex/TagIndexServiceTest`.
- Fixture project: `common/src/desktopTest/resources/Test Project 2/` if tests need on-disk data.

### 9.3 `composeUi`, `operations`, `wasmhost`, `desktop`

- `composeUi/src/desktopTest`: `PluginActionRunnerTest` (open, renumber), `SearchFilterTest`
  (per-screen query filter), a `BrowseXUiTest` if you want parity with `TimeLineOverviewUiTest`.
- `operations/src/desktopTest`: `ReadOperationsTest`, `WriteOperationsTest`, `JsonSchemaTest`,
  `plugin/ActionSessionStoreTest`, `plugin/ActionFieldsTest`.
- `plugins/wasmhost/src/desktopTest`: `RuntimePluginsTest` places and fields.
- `desktop/src/jvmTest`: `DesktopLaunchArgsTest`, `ApplicationStateTest`.

### 9.4 `server` (`server/src/test/kotlin/`)

- `utilities/EntityHashSensitivityTest`: `xSpec`. **The one test that fails on its own.**
- `utilities/EntityHasherExtTest`: block.
- `project/synchronizers/ServerXSynchronizerTest`: new, extend `ServerEntitySynchronizerTest`.
  Cover every `validate` rejection and its status (`ServerImageSynchronizerTest`).
- `project/repository/ProjectEntityRepositoryBaseTest`: mock field, `getUpdateSequence` stub, Koin
  `single`. `beginProjectSync` tests fail at runtime without it.
- `project/repository/ProjectEntityRepositorySyncTest`: stub and expected sequence.
- `server/src/testFixtures/.../e2e/util/ApiEntitySerializers.kt`: **Forced**. Optionally
  `E2eTestData.createTestX`.
- Optional: `ProjectEntityRepositoryEntityTest`, `ProjectEntityDatabaseDatasourceTest`
  (`setupEntities` shifts ids), `e2e/ProjectSyncTest` and `TestDataSet1` (new type data shifts id
  assertions).

### 9.5 `integrationTests`

Real client sync engine against a real server. Nothing enforces per-type coverage. Add the new type
to `ResyncStabilityMatrixTest`, `UploadResyncStabilityMatrixTest`, `ResyncDownloadsNothingTest`,
`EntityTypeResyncMatrixTest` and `MixedSyncFuzzTest` (random op branches and the `nextInt` bound).
Update the coverage map in `integrationTests/README.md`.

- A referenced type: a re-ID scenario where the second device's colliding entity is re-IDed and
  the server ends up with the rewritten referrers, third sync silent (`ImageReIdRewritesReferencesTest`).
- A migration that changes hashes: a two-device upgrade scenario that syncs the migrated project
  against a server holding the old shape and stale hash, both with and without another device's
  download repairing the hash first, with no conflict (`EntryImageUpgradeTest`).

### 9.6 Instrumented and UI tests

- `android/src/androidTest/kotlin/`: `XWorkflowTest`, `EditorNavigationTest` nav tag list.
- `ios/iosUITests/`: `HammerUITest.swift` tag constants, `XWorkflowUITests.swift`.

## 10. Docs to update

- [SYNCING-PROTOCOL.md](SYNCING-PROTOCOL.md): Terminology, Entity list; the update-sequence
  ordering if the type has a parent or is referenced; a section for its wire shape, hash and
  validation when they have rules of their own (see Images there).
- [HOW-TO-RUN-A-SERVER.md](HOW-TO-RUN-A-SERVER.md): the proxy body size, if the entity can be large.
- [ARCHITECTURE.md](ARCHITECTURE.md): the persisted data tier lists user content kinds and the
  content directories `healUnmarkedProjectDirectories` looks for.
- [DESIGN-PATTERNS.md](DESIGN-PATTERNS.md): the deep-link and search enumeration.
- [CLIENT-PLUGINS.md](CLIENT-PLUGINS.md): see section 7.
- [KEYBOARD-SHORTCUTS.md](KEYBOARD-SHORTCUTS.md): Editors table.
- `integrationTests/README.md`: coverage map.

## 11. The silent list

Everything the compiler will not catch, in one place. Check each before opening the PR.

Sync and ids:

- `ApiProjectEntity.Type.fromString` (`else -> null`)
- `EntitySynchronizers.synchronizers` map (client)
- `ProjectEntityRepository.getUpdateSequence` (server), in dependency order
- `DuplicateIdService.CROSS_TYPE_PRECEDENCE` (unlisted type always wins as keeper)
- `ProjectFileRationalizer.handlers`; `DuplicateIdHandler.entityType` and `ids()` defaults
- `ProjectsRepository.projectContentMarkers`
- `XIdDatasource` returning empty instead of null on unreadable storage (causes server deletes)
- `ClientXSynchronizer.getEntityHash` dropping or normalising a field that `hash()` includes
- `reIdEntity` returning `emptySet()` for a referenced type; a referrer kind missing from the
  rewriter's `holders`; a local-only rewrite that reports its entity
- `ServerXSynchronizer.validate` default (accepts everything)
- A migration's hand-written id source list (`EntryImagePromoter.firstFreeId`), and its baselines
  for clean entities (2.4)
- `DataMigrator.repairMigratedProjects` for a migration that can leave work behind
- Koin: client `scoped<ClientXSynchronizer>`, `scoped<XDatasource>`, `scoped<XRepository>`,
  `factory<XIdDatasource>`; server `single<ServerXSynchronizer>`

Indexes and UI:

- `TagIndexService` flow merge and build block; `THEMES_SOURCE_ORDER`; "all four" captions
- `SearchProjectUseCase` deferred and `includesX`; operations `SearchFilter`, `buildTagIndex`
  preloads, operation descriptions
- `StatisticsService`, `ProjectStatistics`, `ProjectHome.State`, `ProjectStatsUi` count cells,
  `StatsOperations`
- `LoadEntityResponse` secondary constructor
- `NAV_X_TAG` and its Android and iOS test mirrors
- `DesktopLaunchArgs` usage text
- The copied router's `isUnsaved` `else -> false`
- Markdown syntax: `countWords`, `MarkdownProjector`, exporters
- `rememberImageRequest` version token, and `evictImageFile` after a replace or delete
- Reverse proxy body size (`client_max_body_size` in [HOW-TO-RUN-A-SERVER.md](HOW-TO-RUN-A-SERVER.md))
  for large entities

Tests: every list in section 9. Only `EntityHashSensitivityTest` and `ApiEntitySerializers`
enforce themselves.

## 12. Verifying

```bash
./gradlew :base:allTests :common:desktopTest :composeUi:desktopTest :operations:desktopTest :plugins:wasmhost:desktopTest :desktop:jvmTest :architectureTests:test
```

```bash
./gradlew :server:test
```

```bash
./gradlew :integrationTests:jvmTest
```

Then the manual check that no test covers end to end: create one of the new type on device A,
sync, sync device B, edit on B, sync both, and confirm that the third sync on each device
transfers **nothing** in the sync log. If it transfers the entity again, the two hash paths
disagree (section 2.2).
