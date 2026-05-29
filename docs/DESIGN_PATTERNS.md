# Design Patterns

A living catalogue of patterns we use in Hammer. When you're about to write
something new, check here first — there's a good chance the problem has a
canonical solution already.

If a pattern in this doc looks wrong for your case, that's fine — talk through
why and add the new variant. The point is shared vocabulary, not rules.

---

## Deep Linking

> **TL;DR** — Navigation flows through typed callbacks threaded from
> `ProjectRootComponent` down through `ProjectRootRouter` into each leaf
> component. Destinations are typed data (`SceneItem`, `EntryDef`, …), not
> raw IDs. The screen-stack owner (`ProjectRootComponent`) is the only thing
> that knows how to *find* the destination component on the stack.

### When to use this

Any time a click somewhere should open another screen and land on a specific
piece of content — opening a scene from a stat block, opening an encyclopedia
entry from a character chip, opening a note from a search result, jumping to
the timeline from a reference.

### Canonical example: Global Search

Global Search is the broadest deep-linker in the app — its results can land on
a scene, a note, an encyclopedia entry, or a timeline event. Read that flow
first; everything else is a subset of it.

**1. The result type encodes its destination.**

```kotlin
// common/.../components/globalsearch/GlobalSearch.kt
sealed class SearchResult {
	data class Scene(val sceneItem: SceneItem, …) : SearchResult()
	data class Note(val noteId: Int, …) : SearchResult()
	data class EncyclopediaEntry(val entryDef: EntryDef, …) : SearchResult()
	data class TimelineEvent(val eventId: Int, …) : SearchResult()
}
```

Each variant carries exactly the typed data the destination component needs.
Not a row id and a string — the actual `SceneItem` / `EntryDef`.

**2. The leaf component takes a navigation callback.**

```kotlin
// common/.../components/globalsearch/GlobalSearchComponent.kt
class GlobalSearchComponent(
	…,
	private val navigateToResult: (SearchResult) -> Unit,
) : GlobalSearch {
	override fun onResultClicked(result: SearchResult) = navigateToResult(result)
}
```

The leaf component knows nothing about routers, decompose stacks, or other
screens. It just hands the typed result back to whoever wired it.

**3. `ProjectRootComponent` is the only thing that resolves "where to go".**

```kotlin
// common/.../components/projectroot/ProjectRootComponent.kt
private fun navigateGlobalSearchResult(result: SearchResult) {
	when (result) {
		is SearchResult.Scene -> {
			showEditor()
			(routerState.value.active.instance as? Destination.EditorDestination)
				?.component?.showScene(result.sceneItem)
		}
		is SearchResult.EncyclopediaEntry -> showEncyclopediaEntry(result.entryDef)
			…
	}
	dismissGlobalSearch()
}
```

This is the dispatch point. Two steps for every destination:

1. Activate the right screen in the stack (`showEditor()`, `showNotes()`, …).
2. Pull the freshly-activated destination's component off `routerState` and
   call its `showX(...)` method to land on the specific item.

Helpers like `showEditorScene(sceneItem)` and `showEncyclopediaEntry(entryDef)`
exist on `ProjectRootComponent` precisely so other navigation entry points
(focus mode reopen, search result, dashboard click) all go through the same
two-step flow.

### Adding a new deep link

Concrete recipe — this is the pattern we used for the dashboard's "open the
longest scene" / "open this character's entry" affordances.

**1. Pick the destination's typed data.**

If the destination already accepts `SceneItem` / `EntryDef` / etc., reuse it.
Do **not** invent a new "id + name" tuple — see *Resolver gotcha* below.

**2. Add an action to the source component's interface.**

```kotlin
// ProjectHome.kt
interface ProjectHome : … {
	fun showLongestScene()
	fun showEntry(entry: EntryAppearance)
}
```

The action's arguments are whatever the source UI has on hand — a click
context, an appearance summary. The component's job is to translate that into
the destination's required type.

**3. Add a constructor callback to the source component.**

```kotlin
// ProjectHomeComponent.kt
class ProjectHomeComponent(
	…,
	private val onShowScene: (SceneItem) -> Unit,
	private val onShowEntry: (EntryDef) -> Unit,
) : ProjectHome {
	override fun showLongestScene() {
		val id = _state.value.longestSceneId ?: return
		val sceneItem = sceneEditorRepository.getSceneItemFromId(id) ?: return
		onShowScene(sceneItem)
	}

	override fun showEntry(entry: EntryAppearance) {
		val def = encyclopediaRepository.findEntryDef(entry.entryId) ?: return
		onShowEntry(def)
	}
}
```

Two things to notice:

- The component does the **resolve step** (id → `SceneItem`, id → `EntryDef`).
  This is the right place for it: the component already has the repositories
  injected, and resolving at *click time* (not cache time) means the data is
  fresh even if names/types have drifted.
- Both methods bail with `?: return` if the destination can't be resolved.
  Don't navigate to a stale id.

**4. Thread the callback through `ProjectRootRouter`.**

```kotlin
// ProjectRootRouter.kt
private fun home(…): ProjectHome = ProjectHomeComponent(
	…,
onShowScene = showScene,
onShowEntry = showEntry,
)
```

`ProjectRootRouter` already has the `showScene` / `showEntry` lambdas wired by
`ProjectRootComponent` — you're just connecting an existing pipe to a new
consumer.

**5. The UI calls the interface method.**

```kotlin
// ProjectStatsUi.kt
HdAttributionItem(
	label = entry.name,
	…,
onClick = { onShowEntry(entry) },
)
```

The UI never reaches across to a router or another component. It calls a
method on the component it already has.

### Resolver gotcha — don't reconstruct typed destinations from cached fields

When the source data is a denormalized cache (a stats snapshot, a search
index entry), it's tempting to hand-build the destination type from the
fields you already have:

```kotlin
// ⚠️ Don't do this
val def = EntryDef(
	projectDef = projectDef,
	id = entry.entryId,
	type = entry.type,
	name = entry.name,
)
```

This works *today* but silently drifts if the user renames the entry or
changes its type between when the cache was built and when they click.

Always go through the canonical resolver:

```kotlin
// ✅ Authoritative lookup
val def = encyclopediaRepository.findEntryDef(entry.entryId) ?: return
val sceneItem = sceneEditorRepository.getSceneItemFromId(id) ?: return
```

If the canonical resolver doesn't exist for your destination type, add one to
the repository — don't replicate it inline.

### Why this shape

- **No global state.** Navigation is just function references passed down.
  Each component is testable with a fake callback.
- **The router is centralized but the *intent* is decentralized.** Anywhere in
  the app can express "open this scene" without knowing how the stack is
  arranged. Only `ProjectRootComponent` knows the stack.
- **Type-safe destinations.** A `SceneItem` parameter can't be confused with a
  `NoteId`. The compiler enforces the deep link contract.
- **One resolve point per destination.** `showEditorScene(SceneItem)` is the
  one path into the editor with a target scene — focus mode reopen, search
  result, dashboard click all funnel through it.

### File map

| File                                                                                                                                                | Role                                                                             |
|-----------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| [ProjectRoot.kt](../common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/projectroot/ProjectRoot.kt)                      | Top-level navigation interface                                                   |
| [ProjectRootComponent.kt](../common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/projectroot/ProjectRootComponent.kt)    | Owns the screen stack, defines `showEditorScene` / `showEncyclopediaEntry`       |
| [ProjectRootRouter.kt](../common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/projectroot/ProjectRootRouter.kt)          | Constructs leaf components, injects the navigation callbacks                     |
| [GlobalSearchComponent.kt](../common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/components/globalsearch/GlobalSearchComponent.kt) | Canonical deep-link source — sealed `SearchResult` carries the typed destination |

---

## Scene-Editing Domain API

> **TL;DR** — Components and UI talk to the scene-editing domain **only through
> `SceneEditorService`** (plus a couple of purpose-built UseCases like
> export/import). The three data repositories behind it —
> `SceneRepository` (tree/structure/paths/sync-hash), `SceneContentRepository`
> (buffers/autosave), `SceneMetadataRepository` (scene + project metadata) — are
> lower-level building blocks. Components never inject them directly.

### When to use this

Any Component (or component-scoped UseCase) that needs to create/rename/move/
delete/archive a scene, read or edit a scene's buffer, read/write scene
metadata, or observe the scene list. There is exactly one door: inject
`SceneEditorService`. A caller never has to decide "repo or service?"

### Why this shape

The scene-editing domain was historically one ~1300-line `SceneEditorRepository`
that bundled ~12 responsibilities and depended sideways on sibling repos and
*up* on a Service-level tracker. It was decomposed (see
[REFACTOR-SceneEditorRepository.md](REFACTOR-SceneEditorRepository.md)) into:

- **`SceneEditorService`** — the single Component-facing API. Owns write
  *orchestration* (it applies the cross-cutting side-effects — statistics,
  reference-index deltas, writing-activity, sync-marking — around each command),
  composes the derived scene-list, and delegates reads one-to-one to the owning
  repository. Wide surface, but thin: all real logic/state lives in the repos.
- **`SceneRepository`** — the scene tree, structure, path computation, and
  sync-hash. Pure data; depends only on datasources + the foundational
  `IdRepository`/`SyncDataRepository`. Does **not** know about statistics,
  references, or writing-activity.
- **`SceneContentRepository`** — the buffer/editing engine: in-memory buffers,
  the content debounce pipeline, temp-buffer autosave, dirty tracking, and the
  `bufferUpdateFlow`. Emits a "buffer persisted" signal the service hangs save
  side-effects off, rather than reaching up into those collaborators itself.
- **`SceneMetadataRepository`** — scene + project metadata (pure persist + the
  `metadataUpdateFlow`); the reference-index delta on a confirmed-references
  change is orchestrated by the service, not here.

The repositories are still consumed *directly* by other Services, UseCases, and
the sync layer (Service→Repository / UseCase→Repository edges are legal). Only
the **Component tier** is restricted to the service.

> **Note (Stage 1).** A few reverse edges remain by design — sibling repos like
> `SceneDraftRepository`/`GlobalSearchRepository` still depend on the scene
> repos, and `SceneRepository` still leans on its sibling sub-repos for
> project-scope init and scene-list dirty-buffer composition. Resolving those
> (e.g. reclassifying `GlobalSearchRepository` as a Service, pushing `Id`/`Sync`
> into a true foundational layer) is deferred to Stage 2.

### File map

| File                                                                                                                                                             | Role                                                                  |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| [SceneEditorService.kt](../common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/data/sceneeditorrepository/SceneEditorService.kt)                 | The single Component-facing API: orchestration + derived state + reads |
| [SceneRepository.kt](../common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/data/sceneeditorrepository/SceneRepository.kt)                       | Tree / structure / paths / sync-hash (pure data)                      |
| [SceneContentRepository.kt](../common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/data/sceneeditorrepository/SceneContentRepository.kt)         | Buffers, content debounce, temp-buffer autosave, lifecycle            |
| [SceneMetadataRepository.kt](../common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/data/sceneeditorrepository/SceneMetadataRepository.kt)       | Scene + project metadata                                              |
