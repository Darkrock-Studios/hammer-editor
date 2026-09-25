# Client Plugins

Design note for extending the Hammer client (desktop, Android, iOS) with plugins,
and for exposing the same API as a command line interface and an MCP server.
Status: rollout steps 1 to 4 and 11 (runtime plugins on desktop) are built; the rest is a proposal. The server already has an equivalent
plugin seam (`server/.../plugin/ServerPlugin.kt`); this mirrors it where the
shapes match.

## Scope

**Now: headless plugins.** A plugin that adds behavior without adding a feature
surface. It may still contribute a settings pane (and later, project actions);
that does not make it a UI plugin. Examples: a grammar checker, an exporter, a backup target,
a statistics collector.

**Now: one API, several front ends.** The operations plugins use (list projects,
read a scene, write a scene, link an entry) are defined once, and the same
definitions are exposed to in-process plugins and a CLI. MCP is not core: it is
one of the first plugins, built on the CLI (see [MCP plugin](#mcp-plugin)). The
CLI also
runs sync, so any machine that can run the JVM can be a headless sync client.

**Later: feature plugins.** A plugin that adds a whole new kind of thing, with its
own data type, screen, sync, and search integration (brainstorming cards, say).
Out of scope for this note. The only requirement is that nothing here makes it
impossible. See [Keeping the door open](#keeping-the-door-open).

**Later: runtime plugins.** Only the JVM can load jars, so native code never
loads at runtime. Plugins anyone can install run as sandboxed WebAssembly
instead, on every platform. See [Runtime plugins](#runtime-plugins-wasm).

## Architecture

```
            in-process plugins       hammer <command>        hammer mcp
                    |                       |              (MCP plugin's
                    |                  CLI adapter          CLI command)
                    |                       \                     /
                    |                        running app? --yes--> forward over local socket
                    |                             | no                    |
                    v                             v                       v
               +---------------------------------------------------------------+
               |                Operation registry (:operations)               |
               +---------------------------------------------------------------+
                                              |
                                repositories, services, Koin
```

Compile-time plugins are how an overlay repo or fork adds behavior (the
hammer.ink server plugin is the existing example). External tools call
operations through the CLI, or through a plugin's CLI command such as
`hammer mcp`. The only code loaded at runtime is sandboxed WebAssembly, through
the same operations (see [Runtime plugins](#runtime-plugins-wasm)).

## Modules

New code goes in a new `:operations` module between `:common` and
`:composeUi`, rather than growing `:common`.

```
:base <- :common <- :operations <- :plugins:plaintext <- :composeUi <- :android, :desktop
                         ^
                         +---- :plugins:mcp <---------------------------------- :desktop
```

| Module | Holds |
| --- | --- |
| `:common` | Same role as today. Gains only extension points (below) and pluggable export |
| `:operations` | `Operation`, `OperationRegistry`, the core operations, `ClientPlugin`, `PluginRegistry`, `ProjectPluginContext`, `PluginSettingsDatasource` |
| `:composeUi` | `PluginUi`, `PluginUiRegistry`, `installedPlugins()` and `installedPluginUis()`, the Plugins section of Settings, and the UI halves of in-tree plugins |
| `:desktop` | The CLI adapter and `Dispatcher`, socket forwarding, the writer lock, `installedDesktopPlugins()` |
| `:plugins:plaintext` | The data half of the [plain text plugin](#plain-text-exporter-plaintext). All platforms, depends on `:operations` |
| `:plugins:mcp` | The MCP plugin. JVM only, depends on `:operations` and the MCP Kotlin SDK |
| `:plugins:wasmhost` | The [runtime plugin](#runtime-plugins-wasm) host: chasm, the package loader, and `WasmPlugin`. All platforms |

**Why registration lives in `:composeUi`.** A plugin module depends on
`:operations`, so `:operations` cannot list it. A plugin's UI half needs
`:composeUi`'s design system, so it cannot sit above `:composeUi` either, and
`:composeUi` is also what the iOS framework is built from. So a cross-platform
plugin is split: its data half is its own module below `:composeUi`, and its UI
half lives in `:composeUi` next to the registration files.

`:wear` depends only on `:common` and gets no plugins.

`:operations` targets the same platforms as `:common` (Android, desktop JVM,
iOS), because in-process plugins run everywhere.

**`:common` does not know about plugins.** It exposes extension points as
contributions collected from Koin, and `:operations` installs each plugin's
contributions into them:

- **Exporters.** `StoryExporterRegistry` always holds the built-in formats and
  adds every `StoryExporter` bound in Koin. `PluginRegistry` binds each plugin's
  `exporters()`, after checking each format id carries the plugin's prefix. Text
  diagnostics providers work the same way.
- **Project lifecycle.** `openProjectScope` and `closeProjectScope` notify
  every `ProjectLifecycleListener` bound in Koin when a project is opened for
  editing and when that session closes. Every platform already opens and
  closes projects through these, and temporary scopes (sync, import) are not
  reported. `PluginRegistry` binds a listener that builds each plugin's
  `ProjectPluginContext`, calls its hooks, and holds the contexts for lookup.

This keeps the dependency arrows pointing one way, and `:common` stays testable
with no plugin code present.

**iOS.** Koin starts in `:common`'s iOS source set, but it is called from
`HammerAppInit` in `:composeUi`, which already passes extra modules. That is
where iOS picks up `installedPlugins()`, from the same module.

**The v2 rule, partly enforced.** `:operations` declares no dependency on
Compose resources, Napier, or Decompose. Napier and Decompose still reach it
through `:common`'s `api` dependencies until the v2 core split, so a lint rule
covers the gap. In v2 the data layer leaves `:common` for a UI-free `:core`, and
`:operations` re-points from `:common` to `:core`.

## Operations

An operation is a named, typed unit of the public API.

```kotlin
package com.darkrockstudios.apps.hammer.operations

interface Operation<I, O> {
	/** Dotted, e.g. `scene.read`. Plugin operations are prefixed with the plugin id. */
	val name: String
	/** English. Used for CLI help and agent tool descriptions. */
	val description: String
	val input: KSerializer<I>
	val output: KSerializer<O>
	val access: Access
	/** Safe to offer to automated agents. Opt-in; the registry rejects Destructive operations that set it. */
	val agentVisible: Boolean get() = false
	/** Overridden only when valid values are known at runtime, such as `project.export`'s formats. */
	fun inputSchema(): JsonObject = jsonSchema(input.descriptor)

	suspend fun run(context: OperationContext, input: I): O
}

enum class Access { Read, Write, Destructive }

class OperationContext(
	val projects: ProjectResolver,
	/** For operations built on other operations. */
	val operations: OperationRegistry,
)

interface ProjectResolver {
	/** By name or server project id. */
	fun resolve(project: String): ProjectDef
	/** Opens the project's Koin scope for [block] if it is not already open. */
	suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T
}
```

Most operations are written with the `operation<I, O>(name, description, access)
{ ... }` builder. Errors the caller caused are `OperationException`s with a kind
(`NotFound`, `InvalidInput`); anything else thrown is a bug. `dispatch(name,
JsonElement)` is what front ends call: unknown input fields, missing fields, and
wrong types are `InvalidInput`. Binary content is base64 in JSON.

`OperationRegistry` holds core operations plus any contributed by plugins, and
is the single place every front end dispatches through. Project-scoped
operations open the scope via the existing `temporaryProjectTask` helper when the
project is not already open, so they work the same in the app and headless.
A temporary scope neither restores nor discards unsaved edits left by a crashed
editor session; only an editor does. Operations on a project that is not open
therefore read the saved text.

The full list is in [Operation catalog](#operation-catalog).

Rules that keep this a stable API rather than a mirror of internals:

- Input and output types are dedicated `@Serializable` classes in
  `:operations`, not repository or entity types.
- Operation names and input fields are append-only once shipped. Renames get a
  new operation.
- Operations never expose Koin, Decompose, or file paths. Inputs and outputs
  carry content (text, or bytes for images and exports), never a path to read
  or write. Reading and writing local files is the CLI adapter's job, so an
  agent can only hand Hammer content it already has and only receives content
  back.
- Operation code does not use Compose resources, Napier, or Decompose, so it
  can move into a native-capable core module unchanged. See
  [Native CLI](#native-cli) under v2. (`SyncAccountUseCase` already localizes its
  log lines through Compose resources; that is covered by the v2 plan for
  structured sync logs.)

## Operation catalog

Conventions:

- Names are `noun.verb`. The CLI maps dots to words: `scene.read` is
  `hammer scene read`.
- Project-scoped operations take `project`, either the project name or its
  server project id.
- Entity ids are the project's own ids, which are stable across sync.
- Body text (scene, note, entry, timeline event, idea) is read from stdin on the
  CLI.
- **Read** operations change nothing. **Write** operations change content.
  **Destructive** operations delete or overwrite in a way a draft cannot undo;
  the CLI requires `--confirm` and they are never agent-visible.
- Operations whose input or output is a file (import, export, entry images)
  carry the bytes. The CLI maps them to `--in FILE` and `--out FILE`, or to
  stdin and stdout.
- **Agent-visible by default:** none. The catalog opts in every Read and Write
  operation in the projects, scenes and drafts, notes, encyclopedia, timeline,
  ideas, cross-cutting, and statistics sections. Account and sync operations stay hidden.

### Projects

| Operation | Access | Notes |
| --- | --- | --- |
| `project.list` | Read | Name, server project id, word count, last modified |
| `project.info` | Read | Metadata, statistics, sync linkage |
| `project.create` | Write | |
| `project.rename` | Write | |
| `project.delete` | Destructive | |
| `project.export` | Read | Format id and scene subset as in `ExportOptions`; returns the file's bytes |
| `export.formats` | Read | Available export formats: id, file extension, MIME type |
| `project.import` | Write | Format and split options as in `ImportOptions`, plus the file's bytes |
| `backup.list` | Read | |
| `backup.create` | Write | |

### Scenes and drafts

| Operation | Access | Notes |
| --- | --- | --- |
| `scene.tree` | Read | Nested groups and scenes: ids, names, order, word counts |
| `scene.read` | Read | Markdown plus metadata |
| `scene.write` | Write | Replaces the text; `mode` is required, see below |
| `scene.append` | Write | Appends to the live text; for capture devices and scripts |
| `scene.create` | Write | Parent, name, position, scene or group |
| `scene.rename` | Write | |
| `scene.move` | Write | New parent and index |
| `scene.archive` | Write | |
| `scene.unarchive` | Write | |
| `scene.archived` | Read | |
| `scene.delete` | Destructive | |
| `scene.meta.read` | Read | Outline, notes, tags |
| `scene.meta.write` | Write | Outline, notes, tags |
| `draft.list` | Read | Drafts of one scene |
| `draft.read` | Read | |
| `draft.create` | Write | Snapshots the scene's current text under a name |
| `draft.apply` | Write | Replaces the scene's text with the draft |
| `draft.delete` | Destructive | |

`scene.write` has no default mode. `mode: draft` saves the new text as a named
draft and leaves the scene alone. `mode: live` replaces the scene text through
the same service the editor uses, so an open editor sees the change instead of
clobbering it. The MCP plugin offers only `draft` unless its "Allow live edits"
setting is on.

### Notes, encyclopedia, timeline

| Operation | Access | Notes |
| --- | --- | --- |
| `note.list` | Read | Optional tag filter |
| `note.read` | Read | |
| `note.create` | Write | |
| `note.update` | Write | |
| `note.delete` | Destructive | |
| `entry.list` | Read | Optional type and tag filters |
| `entry.read` | Read | Text, type, tags, whether it has an image, referencing scenes |
| `entry.create` | Write | |
| `entry.update` | Write | |
| `entry.delete` | Destructive | |
| `entry.image.get` | Read | Returns the image bytes and extension |
| `entry.image.set` | Write | Takes the image bytes and extension |
| `entry.image.remove` | Write | |
| `timeline.list` | Read | |
| `timeline.read` | Read | |
| `timeline.create` | Write | |
| `timeline.update` | Write | |
| `timeline.move` | Write | |
| `timeline.delete` | Destructive | |

### Account-level content

| Operation | Access | Notes |
| --- | --- | --- |
| `idea.list` | Read | Optional archived filter |
| `idea.create` | Write | |
| `idea.update` | Write | |
| `idea.archive` | Write | |
| `idea.unarchive` | Write | |
| `idea.delete` | Destructive | |

### Cross-cutting

| Operation | Access | Notes |
| --- | --- | --- |
| `search` | Read | Query plus filter: all, scenes, notes, encyclopedia, timeline |
| `tag.list` | Read | Ranked, optional entity type filter |
| `tag.find` | Read | Every entity carrying a tag |
| `ops.list` | Read | Every operation with its input and output JSON schema |

`ops.list` is what CLI help is generated from, and what the MCP plugin builds
its tool list from.

### Statistics

| Operation | Access | Notes |
| --- | --- | --- |
| `stats.project` | Read | What the Home screen shows: word and scene totals, words by chapter, longest, shortest, and median scene, note, timeline, and encyclopedia counts, top entry appearances, word-count goal. Optional `recalculate` |
| `stats.activity` | Read | Daily word totals over a date range, plus lifetime words per device |
| `stats.sessions` | Read | Raw writing sessions over a date range, per device |

These read through `StatisticsService` and `WritingActivityRepository`, the
same sources as the Home screen. `stats.project` serves the cached statistics
and recalculates when the cache is dirty or `recalculate` is set; refreshing
the cache is not a content write. The output is a dedicated type, not
`ProjectStatistics` itself, which carries cache bookkeeping (`isDirty`,
`schemaVersion`, `lastCalculated`). Word totals across projects come from
`project.list`.

Export formats are contributed at runtime (see [Exporters](#exporters)), so
`project.export`'s `format` input is a string validated against the exporter
registry, not a fixed enum. Its schema in `ops.list` is built from the registry
when the list is generated, and `export.formats` returns the same set.

### Account and sync

| Operation | Access | Notes |
| --- | --- | --- |
| `account.status` | Read | Server URL, email, whether the token is valid |
| `account.login` | Write | Password from stdin or an environment variable, never argv |
| `account.logout` | Write | |
| `sync.status` | Read | Per project: linked or not, last sync, count of locally changed entities |
| `sync.run` | Write | See [Headless sync](#headless-sync) |

None of these are agent-visible.

### Deliberately excluded

- **Account creation and terms acceptance** stay in the GUI.
- **Server admin** is the server's web UI.
- **Global settings** are not exposed. Plugin settings may be, as plugin
  operations.
- **Reference confirmation** is an editor interaction, not an API.
- **Partial scene edits** (`scene.patch`, find and replace within a scene) are
  likely wanted by agents but wait until full-text writes have settled.

## Plugin interfaces

Two interfaces, paired by `id` and registered separately. The data half lives in
`:operations`, and its signature uses no Compose types, Compose resources included,
so it can move into a UI-free core module later (see
[Native CLI](#native-cli)). The UI half lives in `:composeUi` and owns
everything user-facing: name, settings pane, labels.

### `ClientPlugin` (`:operations`)

```kotlin
package com.darkrockstudios.apps.hammer.operations.plugin

interface ClientPlugin {
	/** Stable, lowercase, directory-safe. Keys settings, storage, menu ids, operation names. */
	val id: String

	/** Koin definitions, installed alongside mainModule. May declare scope<ProjectDefScope> entries. */
	fun koinModule(): Module? = null

	/** Contributed to the registry, so they appear in the CLI and to agents too. */
	fun operations(): List<Operation<*, *>> = emptyList()

	/** Extra top-level CLI commands, e.g. `hammer mcp`. Desktop only; ignored elsewhere. */
	fun cliCommands(): List<CliCommand> = emptyList()

	/** Runs in every process, headless included, before any operation. Must not assume a UI. */
	fun onAppStart(appScope: CoroutineScope) {}

	/** UI only: called when a project window opens and closes. Operations must not depend on these. */
	fun onProjectOpened(project: ProjectPluginContext) {}
	fun onProjectClosed(project: ProjectPluginContext) {}

	// Optional capabilities, one getter each, null when not provided.
	// Added as each is needed; these are the expected near-term ones.
	fun textDiagnostics(): TextDiagnosticsProvider? = null
	fun exporters(): List<StoryExporter> = emptyList()

	/** Typed settings the host renders as a form. See Declared settings. */
	fun settings(): List<SettingDeclaration> = emptyList()
}

class ProjectPluginContext(
	val pluginId: String,
	val projectDef: ProjectDef,
	val projectScope: Scope,
	/** Cancelled when the project closes. */
	val coroutineScope: CoroutineScope,
) {
	/** `<project>/.plugins/<pluginId>/`, created on first call. Included in backups, never synced. */
	fun dataDirectory(): HPath
}
```

Plugins call operations through the registry as the supported API. Koin stays
available as an escape hatch, as it is on the server.

```kotlin
interface CliCommand {
	val name: String
	val help: String

	/** Runs until done. Reaches Hammer only through [dispatcher]; never through Koin. */
	suspend fun run(args: List<String>, io: CliIo, dispatcher: Dispatcher): Int
}

interface Dispatcher {
	/** Runs one operation exactly like a separate CLI call: forwarded to the app if it is up, else headless. */
	suspend fun dispatch(operation: String, input: JsonElement): JsonElement
	suspend fun operations(): List<OperationDescriptor>
}
```

A plugin's CLI command is for protocols and long-running tools, not for
exposing plugin features; plugin features are operations, which already get a
generated subcommand.

Capabilities follow the server pattern (`allowedUsersSource()`): a getter per
capability rather than marker interfaces, nullable for one-per-plugin
capabilities and a list where a plugin may contribute several. The registry
collects them in one pass and the plugin author sees every hook in one place.

### Exporters

```kotlin
package com.darkrockstudios.apps.hammer.common.data.export

interface StoryExporter {
	/** Stable id, e.g. `epub`. Plugin formats are prefixed with the plugin id, e.g. `plaintext.txt`. */
	val formatId: String
	val fileExtension: String
	val mimeType: String
	/** False only for formats that ignore project data (Markdown today), which skips loading it. */
	val needsProjectData: Boolean get() = true

	fun render(sink: BufferedSink, input: ExportInput)
}

class ExportInput(
	val projectName: String,
	val projectData: ProjectData?,
	/** One per top-level node of the scene tree. */
	val chapters: List<StoryChapter>,
	val treatTopLevelAsChapters: Boolean,
	val language: String,
	val strings: ExportStrings,
) {
	fun requireProjectData(): ProjectData
	/** [chapters], or one chapter named after the project when top-level nodes are not chapters. */
	fun bookChapters(): List<StoryChapter>
}
```

The five built-in renderers already share this shape (sink, project name,
project data, chapters, language, localized strings), so the interface is
lifted from them rather than invented. What changes:

- **`ExportFormat` stops being an enum.** `ExportOptions.format` becomes a
  format id. The four places that switch on the enum today (file extension and
  render dispatch in `ExportStoryUseCase`, the Android MIME type in
  `ExportDirectoryPicker`, the format list and labels in `ExportOptionsDialog`)
  read from a `StoryExporterRegistry` in `:common` instead, which mirrors the
  existing `StoryImporterRegistry` and adds contributed formats from Koin (see
  [Modules](#modules)). It lists the built-in formats first, in their menu
  order, then contributed ones by id.
- **All five built-in formats become `StoryExporter`s**, with ids `epub`,
  `docx`, `rtf`, `pdf`, and `markdown`, so built-in and plugin formats take the
  same path.
- **Export moves to the data layer.** `ExportStoryUseCase` and the renderers
  move from `components/projecthome` to `data/export`, next to the importers,
  where operations and a future core module can reach them.
- **Labels come from the UI half.** Built-in labels stay in `:composeUi`.
  A plugin's labels come from `PluginUi.exportFormatLabels()`. A format with no
  label shows its file extension in upper case. The Android save picker takes
  the exporter's MIME type, and the desktop one its extension.

Importers could follow the same pattern (`ImportFormat` is the same kind of
closed enum). Not in v1.

### `PluginUi` (`:composeUi`)

```kotlin
package com.darkrockstudios.apps.hammer.common.compose.plugin

interface PluginUi {
	/** Matches a registered ClientPlugin's id. */
	val id: String
	val name: StringResource

	/** Display names for the export formats this plugin contributes, keyed by format id. */
	fun exportFormatLabels(): Map<String, StringResource> = emptyMap()

	/** Localized text for the plugin's declared settings, keyed by setting key. */
	fun settingLabels(): Map<String, SettingLabels> = emptyMap()

	/** Shown under the plugin's name in the Plugins section of Settings. Null for no pane. */
	val settingsPane: (@Composable ColumnScope.() -> Unit)? get() = null
}
```

The settings pane is a nullable property rather than a function so Settings can
tell which plugins have one. The Plugins section only appears when at least one
does, so a build with no plugins looks exactly as it does today.

**No project menus yet.** `MenuDescriptor` and the `addMenu` callback look like
a menu slot, but every platform passes a no-op: menu items now render inside
each screen. A project-level action slot therefore needs a real in-UI home
(most likely the project root's overflow or navigation rail). It is added when
the style report needs it.

The settings pane and labels for contributed ids are deliberately the only UI
hooks for now.
Future slots (project navigation destination, scene editor toolbar action,
dialogs) are added here when a plugin needs them, not speculatively.

### Registration

```kotlin
// composeUi/.../compose/plugin/InstalledPlugins.kt
fun installedPlugins(): List<ClientPlugin> = listOf(PlainTextPlugin)
fun installedPluginUis(): List<PluginUi> = listOf(PlainTextPluginUi)
```

Same rule as the server file: registering is activating, there is no separate
enabled flag in the registry. A plugin that wants a user-facing on/off switch
keeps that in its own settings and honors it itself. A plugin with a UI half is
registered in both lists.

Each app entry point (`desktop/.../Main.kt`, `HammerApplication`, and
`HammerAppInit` on iOS) accepts a plugin list defaulting to `installedPlugins()`,
so tests can supply fakes the way `EndToEndTest` does for the server. The desktop and
Android entry points take a `PluginUi` list the same way.

**Platform-specific plugins.** `installedPlugins()` is for plugins that run on
every platform. A plugin that only builds for one platform, like the MCP plugin,
is registered in that platform's own file, and the entry point appends it:

```kotlin
// desktop/.../plugin/InstalledDesktopPlugins.kt
fun installedDesktopPlugins(): List<ClientPlugin> = listOf(McpPlugin)
```

Android and iOS get the same kind of file when a plugin first needs one.

Plugins are cross-platform by default. Only a plugin built on something a
platform lacks, such as a local MCP server or a CLI command, is registered per
platform. No supported-platforms field is needed: for a compiled-in plugin the
module's targets and the registration file already say where it runs, and a
runtime plugin runs wherever the WASM host does.

`PluginRegistry` (`:operations`) holds the plugin list. It collects Koin modules
and operations at startup, and binds each plugin's capabilities into `:common`'s
extension points. `PluginUiRegistry` (`:composeUi`) holds the UI list,
pairs each entry with its plugin by id, and logs a warning for a UI half whose
plugin is not registered. The Plugins section of Settings reads it.

## Lifecycle

- **Process start.** Registry built before Koin starts (plugins may inject in
  their hooks, so anything they construct must be lazy). Plugin modules
  installed with the main modules. `onAppStart` runs after Koin is up and data
  migration has run, and receives the Koin-bound `APP_SCOPE`. This is the same in the app and in headless runs, so a
  plugin's operations see the same initialized state either way. Each headless
  call starts its own Koin application (see [Concurrency](#concurrency)), so
  `onAppStart` runs per call and must be cheap.
- **Project open.** `openProjectScope`, when opening for editing, notifies the
  `ProjectLifecycleListener`s. The plugin listener builds each plugin's
  `ProjectPluginContext` and calls `onProjectOpened`.
- **Project close.** `closeProjectScope` counts editors per project (Android
  can show one project in two tasks), so the close event and the Koin scope
  close happen when the last editor closes. The listeners are notified first.
  Each plugin gets `onProjectClosed`, then the shared context coroutine scope is
  cancelled and joined, with a short timeout, so no plugin work outlives the
  Koin scope.
- **Hooks are synchronous.** They run on the caller's thread, often the UI
  thread, so they stay quick and launch real work into the scope they are given.
- **Failures are contained.** A throwing hook is logged and skipped; it cannot
  stop a project opening or closing, or keep other plugins from running.
- **Project-scoped operations** never rely on the project hooks. Headless runs
  do not open projects in the UI sense, so any per-project plugin state an
  operation needs must be reachable lazily through the Koin project scope.

Plugins never see the Decompose stack or the root router config.

## CLI

The CLI is the normal desktop binary with subcommands, not a separate target.
Every package format (Flatpak, Snap, MSIX, the Mac bundle) already ships one
launcher, and a second target would double that work. The existing Clikt parser
in `DesktopLaunchArgs.kt` gains subcommands; with no subcommand the app launches
as today.

```
hammer project list
hammer scene read --project "My Novel" --id 12
hammer scene write --project "My Novel" --id 12 < revised.md
hammer mcp        # contributed by the MCP plugin
```

- Subcommands are generated from the operation registry. Each input field
  becomes an option; a text field marked as the body reads from stdin.
- Output is the operation's output type as JSON.
- Plugin `cliCommands()` are added alongside the generated subcommands. A plugin
  command cannot shadow a generated one.
- With a subcommand, `main` starts Koin without opening a window, runs the
  command, and exits. `:common` has no Compose UI usage, so this is feasible;
  string resources and the data migrator need confirming without a window.
- JVM startup plus Koin init is on the order of a second per call on a desktop,
  several on a Pi Zero. Deferred to v2: `hammer batch`, one process reading
  operations as JSON lines on stdin and writing one JSON result line per
  operation to stdout. It is a few lines on top of `Dispatcher`, the same loop
  the MCP plugin runs with MCP framing.
- Getting `hammer` onto PATH differs per package: Flatpak needs a `flatpak run`
  wrapper, MSIX needs an app execution alias, the Mac bundle needs a symlink or
  documented path.

## Concurrency

The running app keeps scene buffers and the next entity id in memory, writes
projects that are not open in a window (sync-all, backups, renames from the
project list), and refreshes the account token, which the server rotates on
every refresh for a given install id. A second process doing any of that at the
same time collides on ids, clobbers buffers, desyncs the sync journal, or
invalidates the other process's token. So there is exactly one writer at a
time for the whole data set, not per project.

**Writer lock.** One OS file lock, `writer.lock` in the app's data directory
(next to the global settings, outside the projects directory, so backups,
renames, and restores never touch it). The app holds it for its whole lifetime.
A headless call holds it for the duration of that call. OS locks release on
process death, so there are no stale lock files to clean up. A headless call
that finds it held by another headless call waits briefly, then fails.

**Forwarding to a running app.** The app, as the lock holder, listens on a Unix
domain socket in its data directory (supported by the JDK on Windows 10+ as
well), created with user-only permissions and replaced on startup if a stale one
is left behind. Every CLI call tries the socket first. If the app answers, the
CLI sends the operation name and input, the app runs it against its live state,
and the CLI prints the result. If nothing answers, the CLI takes the writer lock
and runs the operation in-process. If the lock is held but the socket does not
answer, the app is running with "Allow external tools" off, and the CLI fails
with a message saying so.

**Multiple app instances.** The desktop app currently runs with
`enableSingleInstance = false`, so several can start. Under this design a second
instance cannot take the writer lock. It forwards its launch arguments (for
example, a project to open) to the first over the socket and exits, which makes
the app effectively single-instance. Why single-instance is off today (possibly
a Nucleus or Tao backend constraint) needs checking before this lands.

**No state between calls.** Each headless call, including each dispatch from a
long-running plugin command like `hammer mcp`, starts Koin, runs, and stops it.
Nothing loaded for one call (id allocator state, repositories, settings, the
cached auth token) survives to the next, and every call re-checks the socket, so
an app launched mid-session is picked up on the next call.

Repositories resolve through Koin's global context, so this is a stop and
restart of the global context, and calls within one process run one at a time.
That is why `CliCommand` reaches Hammer only through `Dispatcher`: anything it
resolved from Koin would be dead after the first call. Two things to verify
early: that project and app scopes shut down cleanly on `stopKoin`, and that
restart cost is small once the JVM is warm.

Forwarding and locking are desktop only. Android and iOS have no CLI and are
single process.

## MCP plugin

MCP is a plugin, not core, and one of the first built. The core only provides
the CLI, `Dispatcher`, and the `cliCommands()` capability; everything
MCP-specific lives in `:plugins:mcp`.

`hammer mcp` speaks MCP over stdio, which every MCP client supports, and the
agent launches it as a child process. It builds its tool list from
`Dispatcher.operations()`, keeping only agent-visible ones, and sends each tool
call through `Dispatcher.dispatch`. So it works against the running app's live
state or, with no app running, directly against the files. No network port and
no token management.

| Exercises | How |
| --- | --- |
| `cliCommands()` capability | Contributes `mcp` |
| Plugin as API consumer | Tool list and every call go through `Dispatcher` |
| Plugin in its own module | `:plugins:mcp`, with its own `Res` for strings |
| Platform-specific registration | Registered in `installedDesktopPlugins()`, since the SDK has no iOS or Android target |
| Global plugin settings | "Enable MCP" and "Allow live edits" in `plugins/mcp.toml` |
| UI half | Settings pane with both toggles and the config snippet to paste into an agent |

**Off until enabled.** "Enable MCP" defaults to off, and `hammer mcp` exits with
a message pointing to the setting. Many writers will not want agent access at
all. For a distribution that shouldn't ship it, removing one registration line
removes it entirely.

"Allow external tools" still gates forwarding to the running app, for the CLI
and the MCP plugin alike.

## Headless sync

`hammer sync run` does what the "Sync all" button does: the account phase
(project creates, renames, deletes, and ideas), then every linked project,
skipping ones the server reports unchanged.

```
hammer account login --url https://hammer.ink --email me@example.com < password.txt
hammer sync run
hammer sync run --project "My Novel" --on-conflict server
```

- **Already in place.** `SyncAccountUseCase` in the data layer holds the
  sync-all orchestration. It reports through a `SyncAccountListener`, takes a project filter,
  and returns a per-project `ProjectSyncOutcome`. `sync.run` is a CLI listener
  plus that call; no refactor needed.
- **Conflicts.** `--on-conflict abort|local|server`, default `abort`. Abort is
  what bulk sync already does: the project stops, is reported as needing
  resolution, and other projects continue. Idea conflicts follow the same flag.
- **Output.** Sync log lines go to stderr. A JSON summary with each project's
  outcome goes to stdout.
- **Exit codes.** 0 all synced, 1 failure, 2 at least one project needs
  resolution, 3 unauthorized (run `account login` again). These map from
  `ProjectSyncOutcome`: `Failed` is 1, `NeedsResolution` is 2, and `Success`,
  `Unchanged`, `NotOnServer`, and `Skipped` are 0, with `NotOnServer` logged as
  a warning. `onUnauthorized` gives 3.
- **Credentials.** The desktop token store is an AES-encrypted file keyed on
  the user name and home directory. It needs no keyring or desktop session, so it
  works on a bare Linux box.
- **App running.** `sync.run` is forwarded like any other operation, so the
  app's own sync state stays coherent and only one process ever refreshes the
  token. Before forwarding exists, the writer lock makes the CLI refuse with a
  message instead.

What this enables: a cron job on a NAS or Pi acting as an always-on sync peer
and backup mirror, the Typewriter device syncing without the GUI's involvement,
and scripted pipelines such as sync then export.

**Hardware floor.** Anything that runs a 64-bit JVM, which starts around a
Pi Zero 2 W (512 MB, Cortex-A53). There, startup is several seconds per call,
which is fine for cron sync and slow for chatty tooling until `hammer batch`
lands in v2 (see [CLI](#cli)).

## Settings

Plugins do not add fields to `GlobalSettings`. Each gets its own file in the
settings directory, `plugins/<id>.toml`, read and written through a
`PluginSettingsDatasource` that takes the plugin's serializable settings type
and replaces the file atomically, so an interrupted write cannot reset it.
(It is a datasource by name because `:common`'s architecture rule keeps raw TOML
I/O in datasource files.) This
keeps plugin schemas out of the core settings migrations and lets an overlay
plugin change its settings shape without touching upstream files.

Per-project plugin state goes in `<project>/.plugins/<id>/`. That directory is
included in backups for free (backups zip the project directory) and ignored by
sync for free (sync is entity-based, it never walks the directory). Rename and
move work because it lives inside the project.

### Declared settings

Most plugin settings are a handful of typed values, so a plugin declares them
and the host renders the form, instead of each plugin writing Compose. This is
the only way a [runtime plugin](#runtime-plugins-wasm) can have settings, and
the default for compiled-in ones.

A declaration is a list of typed fields:

```toml
[[setting]]
key = "scene_break"
type = "choice"
label = "Scene break"
default = "hash"
options = [
	{ value = "hash", label = "#" },
	{ value = "asterisks", label = "* * *" },
	{ value = "blank", label = "Blank line" },
]

[[setting]]
key = "chapter_headings"
type = "bool"
label = "Chapter headings"
hint = "Only when top-level scenes are exported as chapters."
default = true

[[setting]]
key = "min_length"
type = "int"
label = "Shortest word counted"
default = 3
min = 1
max = 20
```

- **Types.** `bool` (toggle), `int` with optional `min` and `max`, `string`
  with optional `multiline`, and `choice` (dropdown). More types are added when
  a plugin needs one. There is no secret type: plugin settings are plain TOML,
  so credentials do not belong in them.
- **Where it lives.** A runtime plugin ships `settings.toml` in its package. A
  compiled-in plugin returns the same model from `ClientPlugin.settings()` in
  Kotlin (`:operations`, no Compose types), so both kinds share one parser-free
  path from the model onward.
- **Storage.** Values go in the plugin's existing `plugins/<id>.toml`, one key
  per setting. On load, each value is checked against its declaration; a
  missing, mistyped, or out-of-range value falls back to its default, and keys
  no longer declared are dropped on the next write.
- **Reading values.** A compiled-in plugin can decode the file into its own
  `@Serializable` class, as today, since the keys are the property names. A
  runtime plugin receives its current settings as JSON with every call, so it
  never needs a host call to read them.
- **Rendering.** `:composeUi` turns a declaration into Hd components: a toggle
  row, a number field, a text field, or a dropdown. Labels are plain strings in
  the declaration. A compiled-in plugin's UI half can localize them with
  `PluginUi.settingLabels()`, keyed by setting key: a `SettingLabels` holding
  the label, hint, and choice option labels.
- **Access.** `PluginRegistry.settings(pluginId)` is the plugin's
  `DeclaredSettingsStore`: current values as a `StateFlow<JsonObject>`,
  `decode(serializer)` into the plugin's own class, and `set(key, value)`,
  which ignores undeclared keys and invalid values.
- **Custom panes remain.** `PluginUi.settingsPane` stays for settings a form
  cannot express (MCP's config snippet, say). If a plugin has both, the
  declared form renders first and the custom pane follows it.

Each plugin currently gets a block in the Plugins section of Settings. Once
runtime plugins can be installed, the list may grow long enough that each
plugin needs its own settings screen, opened from that list. The declared form
works the same either way.

## Strings

`PluginUi.name` and the format labels take `StringResource`, which is
module-agnostic. An in-tree or overlay plugin's UI half adds
`values/<id>-strings.xml` under `:composeUi`'s own `composeResources`, which
generates a `Res` separate from the app's (the app's strings stay in `:common`).
Crowdin picks those files up too. A plugin in its own Gradle module uses its
own `Res`. Either way the plugin owns its strings.

Operation descriptions are plain English strings. They are read by agents and
shown in CLI help, neither of which is localized today.

## Example plugins

Two plugins to build against v1, alongside the [MCP plugin](#mcp-plugin),
chosen so that between them they exercise most of the seam. All three are real
features, not test fixtures.

### Plain text exporter (`plaintext`)

Built. Exports a story as plain text for pasting into submission forms, which
want text with their own conventions for scene breaks and italics. Settings:
scene break marker (`#`, `* * *`, or a blank line), italics (underscores,
asterisks, or removed), paragraphs (blank line between, or indented), and
chapter headings. Formatting comes from `:common`'s public prose parser, so it
reads markdown the same way the built-in formats do.

| Exercises | How |
| --- | --- |
| Exporter capability | `exporters()` returns one exporter, format id `plaintext.txt` |
| Global plugin settings | `plugins/plaintext.toml`, through a small store the exporter reads at render time |
| UI half | Settings pane and `exportFormatLabels()`, with strings in `:composeUi`'s own `Res` |
| Declared settings (next) | Its four settings are three choices and a toggle, so it moves to a [declaration](#declared-settings) and drops its hand-written pane, proving the form before runtime plugins depend on it |
| Split module | Data half in `:plugins:plaintext`, UI half in `:composeUi` |
| Operations for free | `hammer project export --format plaintext.txt`, and export through the MCP plugin, with no plugin code |

**Manuscript format moves into core.** Standard Manuscript Format (12 point
Times or Courier, double spacing, a surname, title, and page number header, a
rounded word count, `#` scene breaks) was the first example plugin. It fits the
built-in DOCX better: writers mostly want DOCX to send to agents, editors, and
contests, all of whom expect manuscript format, and EPUB and PDF already cover
a styled book. Converting the built-in DOCX is a separate change, pending where
the page-one contact block comes from.

### Style report (`style`)

Adds a `style.report` operation (Read, agent-visible): per-scene readability,
adverb density, dialogue ratio, and repeated words and phrases.

| Exercises | How |
| --- | --- |
| Plugin operations | `operations()` returns `style.report`; it appears in the CLI and as an MCP tool |
| Headless parity | Word lists load in `onAppStart`, which runs headless too, so results match |
| Plugin as API consumer | Reads through `scene.tree` and `scene.read`. A "Style report" project action writes the report to a note through `note.create` |
| Project actions | The first user of the project action slot, which it adds (see [`PluginUi`](#pluginui-composeui)) |
| Per-project storage | Per-scene results cached in `<project>/.plugins/style/`, keyed by content hash |

It depends on `note.create`, so it lands after write operations.

### Gaps these expose

- **No content-change events.** A plugin that wants to react to edits (live
  stats, a background linter) has nothing to subscribe to. The style report
  avoids needing one by keying its cache on content hashes. The likely shape is
  a `changes` flow on `ProjectPluginContext`, built on `SceneEditorService`'s
  existing scene update subscriptions. Deferred until a plugin needs it.
- **No project action slot.** Menu contributions are a no-op on every platform,
  so the style report brings the first real in-UI action slot with it.
- **No dialog or panel slot.** The style report writes a note because there is
  nowhere to show a result. A result dialog is the most likely next UI slot.
- **No per-project settings pane.** Plugin settings are global. Per-project
  settings would need a slot on the project settings screen. Neither example
  plugin needs one yet.

## Keeping the door open

A feature plugin needs a new entity type. Today that means touching every one of:

- `EntityType` and `ApiProjectEntity` in `:base` (shared with the server)
- `IdAllocator` and its per-type `IdDatasource`s
- `EntitySynchronizers` and a client and server synchronizer pair
- `SearchProjectUseCase`
- `ProjectRootRouter.Config` and the navigation rail
- `DataMigrator`

None of that is generalized in this proposal. The rules that keep it possible
later are:

1. Nothing in `ClientPlugin`, `PluginUi`, `ProjectPluginContext`, or the
   operation API references any of the types above.
2. Plugin ids are stable and match the server plugin id convention, so a future
   feature plugin can be one id with a client half and a `ServerPlugin` half.
3. Plugin lifecycle is already project-scoped, so registering project-scoped
   repositories needs no new hook.
4. Per-project plugin storage is explicitly *not* synced. Owning synced entities
   becomes a separate, opt-in capability (`entityOwner()` or similar) when the
   entity model is opened up, rather than something plugins fall into by writing
   files.
5. A feature plugin's operations register the same way as any other, so its data
   type is scriptable and agent-accessible the day it ships.

## Runtime plugins (WASM)

Compiled-in plugins need a fork or overlay repo. Runtime plugins let anyone
write a plugin and let users install it, by running WebAssembly in a sandbox.
They are a second way to produce a `ClientPlugin`, not a second plugin system.

### Runtime

[chasm](https://github.com/CharlieTap/chasm), a WebAssembly interpreter written
in Kotlin Multiplatform, MIT and Apache licensed. It is published for JVM,
Android, iOS, macOS, Linux, and Windows, so one host in `commonMain` covers
every target with no native libraries to package. It never generates code at
runtime, which iOS forbids. It supports Wasm 3.0 plus GC, exception handling,
and typed function references (what Kotlin/Wasm output needs), and WASI
Preview 1 through a companion library. It does not support SIMD or Memory64.

### Languages

C, through clang and wasi-sdk, is the first supported language: small modules
that use linear memory only, which any runtime handles. Rust, Go, Zig, and
AssemblyScript work the same way. Kotlin/Wasm might run, since chasm supports
the proposals it needs, but that is unproven until the spike tries it.

### Package

A `.hammerplugin` file is a zip holding `manifest.toml`, `plugin.wasm`, and,
if the plugin has settings, a [`settings.toml`](#declared-settings).

```toml
id = "wordfreq"               # same rules as compiled-in ids
name = "Word Frequency"
version = "1.0.0"
api = 1                       # host API version the plugin was built against

[permissions]
operations = ["project.read", "scene.tree", "scene.read"]

[[exporters]]
format = "wordfreq.csv"       # prefixed with the id, as for compiled-in plugins
extension = "csv"
mime = "text/csv"
label = "Word frequency (CSV)"
```

There is no platforms field: a WASM module runs anywhere the host does.
Labels are plain strings in the manifest. Localized manifests can come later.

### Calling convention

Everything crosses the boundary as JSON in the plugin's linear memory, so the
host needs no knowledge of plugin types:

- **Plugin to host:** one import that dispatches an operation, plus logging.
  This is the [`Dispatcher`](#clientplugin-operations) call, so a runtime plugin
  reaches exactly the API an in-process plugin or the CLI does.
- **Host to plugin:** exports for each capability: render an export, run one of
  the plugin's own operations, describe itself.

**Decision for the spike: Extism compatibility.** Extism already publishes
plugin libraries for C, Rust, Go, Zig, JavaScript, AssemblyScript, .NET, and
Haskell, with documentation. Since anyone can write plugins, adopting its
convention gives authors that head start. The cost is implementing Extism's
host side on chasm; its Java SDK does the same on the Chicory runtime and is
the reference. If the port proves too large, the fallback is a small
convention of our own (allocate, call, dispatch, log) plus a C header.

**Settled by the spike.** Extism's convention it is. The host implements the
`extism:host/env` functions natively (`ExtismKernel`) rather than running
Extism's kernel module, which would cost a second interpreted call for every
eight bytes a plugin copies. The Hammer-specific parts:

- `extism:host/user` `hammer_dispatch(request) -> reply`. The request is
  `{"operation": "...", "input": {...}}`; the reply is `{"output": ...}` or
  `{"error": {"kind": "...", "message": "..."}}`, with the kinds of
  `OperationException` plus `PermissionDenied`.
- An `export` function renders every export format the manifest declares. Its
  input is `{"format", "projectName", "language", "chapters": [{"name",
  "scenes": [markdown]}], "settings": {...}}`, the last holding every declared
  setting's current value, and its output is the file's bytes.
- HTTP imports exist, as Extism plugins expect them, and always fail. The only
  WASI imports provided are `random_get` and `clock_time_get`, which Kotlin/Wasm
  and kotlinx.serialization need and which grant no access to anything; a
  module importing any other WASI function does not load.
- `_initialize`, or else `__wasm_call_ctors`, runs once after instantiation, as
  in other Extism hosts.

### Host

A new `:plugins:wasmhost` module, depending on `:operations` and chasm:

- **Loader.** `RuntimePlugins` keeps packages in `<config>/plugins/packages/`
  and each plugin's enabled flag and granted operations in
  `<config>/plugins/_runtime-plugins.toml` (plugin ids cannot start with `_`,
  so no plugin's settings file collides with it). At startup it wraps each
  enabled package in a `WasmPlugin` before the registry is built. A package
  that fails to read, or takes a compiled-in plugin's id, is logged and
  skipped; it never stops the app starting.
- **Install.** Reads the package, checks the manifest, the settings
  declarations, and export format prefixes, and instantiates the module once,
  which checks its imports and runs its initializer. Only then is it copied in.
  Installing a package with an installed id replaces it. Install, enable,
  disable, and uninstall apply at the next start, since the registry is fixed
  for the process; Settings says so.
- **`WasmPlugin`** implements `ClientPlugin`. Its `exporters()` come from the
  manifest and render by calling the module; its operations register like any
  plugin's. The same `PluginRegistry` receives it, so everything built for
  compiled-in plugins applies unchanged.
- **Execution.** `WasmPlugin.call` runs the module on the IO dispatcher, never
  the UI thread. Export rendering, already on a background dispatcher, calls it
  blocking. An operation the module dispatches blocks its thread until done.
- **Stopping a runaway module.** chasm has no fuel, instruction limit, or
  interrupt, and its decoded module cannot be edited. So the host rewrites the
  binary before loading it (`FuelInstrumenter`): a mutable i64 global,
  decremented on every function entry and loop iteration, traps the module at
  zero. The host sets it before each call. The same pass caps linear memory
  (64 MiB) and tables (100,000 entries), and rejects SIMD, threads, and shared
  or 64-bit memories. A module cannot call itself again from inside a call.
  Setting the global to zero from another thread does not stop a running call:
  the interpreter never sees the write. User-initiated cancellation therefore
  needs either an interrupt flag in chasm itself or an injected host import the
  fuel check polls (which means renumbering every function index in the
  module). The fuel budget alone is what stops runaway code for now.

Runtime plugins stay headless: operations, exporters, and later diagnostics.
A module cannot supply Compose UI, so its settings are
[declared](#declared-settings) in `settings.toml` and the host renders the form.

### Trust

Runtime plugins are untrusted code, unlike compiled-in ones:

- **No ambient access.** No WASI filesystem, sockets, environment, or
  arguments: only randomness and clocks. Everything goes through operations, which
  carry content and never file paths.
- **Permissions checked on every call.** The host's dispatch refuses any
  operation the manifest did not request or the user did not grant. Read and
  Write operations are shown separately at install. Accepting the install
  prompt grants everything the manifest lists; a replaced package's grants are
  those of the new manifest, since the user just approved them.
- **Install and removal in Settings.** The Plugins section gains install from
  file, enable and disable, and uninstall for runtime plugins, with the
  permission prompt at install. Compiled-in plugins keep having no switch.
- **Signing** is deferred. An unsigned package is the norm at first.

### Spike results

Desktop JVM, warm, chasm 2.0.0:

| Measure | Result |
| --- | --- |
| Tight loop, 20M iterations | 358 ms bare, 738 ms with fuel checks |
| 557 KB of text (100k words) through a WAT plugin, two host calls per byte | 63 ms |
| Same text through a Kotlin/Wasm plugin, development build (612 KB) | 825 ms to load, 2.0 s to run |
| Same text through a C word frequency export | Not yet measured: needs `wasm-ld` (lld) |

- **Kotlin/Wasm works** on the same host, `wasmWasi` target, with
  `@WasmImport` for the Extism functions and nothing but `random_get` from WASI.
  It is slow to load and run next to C; a production (binaryen-optimized)
  build is untried.
- **C works freestanding.** Extism's C PDK compiles with no libc
  (`--target=wasm32-unknown-unknown -nostdlib`), so a C plugin imports nothing
  but Extism and Hammer functions.
- **Fuel costs about 2x in tight loops.** A cheaper scheme charges a basic
  block's instruction count once per block instead of one unit per loop pass;
  it is not needed yet.
- **Plugin kit.** Authors get `hammer.h` on top of Extism's `extism-pdk.h`: the
  dispatch import, `hammer_call(operation, input_json)`, and the API version.
  Typed C structs for operation inputs and outputs can later be generated from
  `ops.list`'s schemas.

Example and test plugins live in a separate `hammer-plugins` repository, next to
this one: C and Kotlin/Wasm examples, the plugin kit, and the WAT sources of the
host's test fixtures. Its fixture script writes the compiled fixtures (a few
hundred bytes each) into `:plugins:wasmhost`'s test resources, so this repo's
tests do not need that checkout.

### Platforms

The host is common code, so enabling a platform is a registration change: the
platform's entry point calls the loader. Desktop comes first. Android follows
with no new host code. iOS can run it technically; whether App Review accepts
installable plugins is a policy question to settle before shipping it there,
not an architecture one.

## Cost to `:common`

Most new code lives outside `:common`: in `:operations`, `:composeUi`,
`:desktop`, and plugin modules. Everything this note asks of `:common`:

| Change | Size | Justified without plugins? |
| --- | --- | --- |
| `openProjectScope` and `closeProjectScope` notify Koin-bound `ProjectLifecycleListener`s | A few lines | No. The one piece of pure plugin plumbing |
| `closeProjectScope` counts editors, closing the scope when the last one closes | A few lines | Yes. Two Android tasks on one project otherwise close the scope under each other |
| One string, `settings_plugins_header` | Trivial | No, but it is where all UI strings live |
| `ExportFormat` enum becomes `StoryExporterRegistry`; export moves from `components/projecthome` to the data layer | Moderate | Partly. Export logic is in the wrong layer today |
| The prose markdown parser moves out of `PdfProseMarkdown.kt` into a public `ProseMarkdown.kt` | Small | Yes. DOCX and RTF already use it, and it had nothing to do with PDF |
| `StoryChapter` keeps its scenes separate, with `markdown` joining them | Small | Yes. Manuscript format needs scene breaks too |
| A draft-save method that takes text, not only the current scene content | Small | No, but it is a natural addition |

Headless sync needs no `:common` change: the sync-all orchestration already
lives in the data layer as `SyncAccountUseCase`. Neither do runtime plugins:
they reach `:common` only through operations.

**Hidden costs.** Two assumptions could push fixes into `:common`, and both
come from running it with no UI:

- **Headless startup.** String resources and the data migrator have not been
  run without a window.
- **Koin restart per call.** Stopping and restarting the global context only
  works if project and app scopes shut down cleanly. A repository that leaves a
  coroutine running or holds static state would need fixing. `TimeLineRepository`
  was one, and now cancels its scope when the project scope closes.

Spike both before step 5: start Koin headless, run a few read operations,
stop and restart it in a loop, and watch for leaks and failures.

**Guardrail.** Every step's `:common` changes must make sense without plugins,
or be listed in the table above. Anything else gets flagged in review, and the
design is revisited rather than `:common` bent to fit.

## Rollout

1. **Seam.** The `:operations` module, `:common`'s extension points
   (`ProjectLifecycleListener` and Koin-collected contributions), `ClientPlugin`,
   `PluginUi`, `ProjectPluginContext`, both registries, both registration files,
   `PluginSettingsDatasource`, the `.plugins` directory, the Plugins section of Settings,
   and entry-point wiring. Proven by a test-only fake plugin.
2. **Pluggable export.** Move export into the data layer, replace the
   `ExportFormat` enum with `StoryExporterRegistry`, port the five built-in
   formats, then ship the [plain text exporter](#plain-text-exporter-plaintext)
   as the first real plugin.
3. **Operation registry and read operations.** Registry, `OperationContext`,
   the Read operations from the catalog, including `project.export` and
   `export.formats`. Tested directly, no front end yet.
4. **Runtime plugin spike.** Done; see [Spike results](#spike-results).
   `:plugins:wasmhost` holds the Extism host on chasm, fuel instrumentation, the
   manifest, and `WasmPlugin`.
5. **Headless CLI.** Preceded by the headless spike in
   [Cost to `:common`](#cost-to-common). Subcommands generated from the
   registry, `Dispatcher`,
   the `cliCommands()` capability, per-call Koin startup, the writer lock (held
   by the app for its lifetime too).
6. **[MCP plugin](#mcp-plugin).** `:plugins:mcp`, desktop-only registration,
   settings pane. Read-only at this point, and refuses while the app is running
   until forwarding lands.
7. **Headless sync.** The account and sync operations, with `sync.run` as a
   CLI listener over `SyncAccountUseCase`. Refuses while the app is running.
8. **Forwarding.** Local socket in the app, "Allow external tools" setting, CLI
   prefers the running app, second app instances hand off to the first.
9. **Write operations.** `scene.write` and `scene.append` first, then the rest.
   Deliberately after forwarding, so live writes always go through the app when
   it is up. Then the [style report](#style-report-style) plugin.
10. **Text diagnostics.** Define `TextDiagnosticsProvider` (text in, ranges plus
   messages plus fixes out) and add a grammar plugin against it. Migrate spell
   check onto the same interface only if the editor integration gets simpler for
   it; spell check is wired deep into editor decorations and is not a cheap
   first proof.
11. **Runtime plugins on desktop.** Built. A Kotlin/Wasm plugin from the
    `hammer-plugins` repository compiles, packages, installs, loads after a
    restart, reads its declared settings, and calls back into Hammer.
12. **Runtime plugins on Android**, then an iOS decision. Registration changes
    only.

Steps 1 and 2 restructure existing code, and step 8 changes app startup
(single-instance hand-off). The rest are additive. Step 9 is where the
concurrency design gets tested for real.

## v2

### Batch mode

`hammer batch`, described under [CLI](#cli). One process, operations in as
JSON lines, results out as JSON lines.

### Sync backends

Could a plugin sync projects through Google Drive (or Dropbox, WebDAV, a git
remote) instead of a Hammer server? Not with this design, but it can be added:

- **A sync capability.** Today sync is `SyncAccountUseCase` talking to the
  Hammer server's API. A plugin backend needs a `syncBackend()` capability
  that the sync use case calls instead: push and pull whole entities, list what
  changed since a marker. The entity journal and conflict handling stay in
  core; only the transport is the plugin's.
- **Compiled-in first.** A Drive backend needs OAuth (a browser sign-in and
  token refresh), HTTP, background scheduling, and a place for tokens. A
  compiled-in plugin has all of these through Kotlin and Koin, so an overlay
  repo could ship one once the capability exists.
- **Runtime plugins need more host.** An HTTP permission limited to hosts the
  manifest names (Extism's `http_request` already fits), an OAuth flow the
  host runs on the plugin's behalf, and secret storage, since declared settings
  are plain TOML and have no secret type. All three are worth doing only once a
  real backend needs them.
- **No shared server semantics.** Drive has no server-side merge, so the
  backend would store a journal alongside the files and resolve conflicts
  client-side, as sync already does for the Hammer server's reported changes.

### Native CLI

A Kotlin/Native build of the CLI for `linuxX64` and `linuxArm64`: a single
binary with no JVM, fast startup, and a small memory footprint on devices like
a Pi Zero.

**Dependencies are not the obstacle.** As of this writing, every dependency of
`:base` and `:common` publishes Linux native targets except these:

| Library | Use in the data layer | Resolution |
| --- | --- | --- |
| Napier | Logging in about 56 files | Swap for Kermit (has Linux targets) or a small internal facade |
| Compose resources | About 18 files; sync log messages are localized through it | Sync logs become structured events that the UI localizes |
| Decompose | 1 file | Remove the dependency from that file |
| platform-spellcheckerkt, pdfkmp | Spell check, PDF export | Excluded from the native build |

The Ktor client uses the curl engine on Linux, which requires libcurl on the
target system. The MCP Kotlin SDK also publishes Linux native targets, so the
MCP plugin can come along.

**The obstacle is module structure.** A native CLI module cannot depend on
`:common` as it is, because that would require all of `:common`'s shared code,
components and Compose resources included, to compile for Linux. The
prerequisite is extracting a UI-free `:core` module from `:common`: the data
layer and sync. `:common` keeps the components, `:operations` re-points to
`:core`, and the native CLI depends on `:operations` and `:core`. That extraction is the bulk of the work, roughly 200 files, and it matches
the layering in `ARCHITECTURE.md`, so it has value beyond the CLI.

After that:

- Linux actuals for the core module's `expect` declarations (`:common` has 32
  today, some of which stay with the UI). The iOS actuals mostly use Foundation
  and cannot be shared.
- A Linux token store, porting the desktop AES file store onto the crypto
  libraries `:base` already uses.
- The writer lock and the local socket via POSIX.
- A native test run in CI. Sync would ship on two runtimes, and TLS through
  libcurl and file locking are the likeliest places for them to differ.

**Cross-compilation is a real advantage.** Kotlin/Native builds `linuxArm64`
binaries on an x64 host. GraalVM native-image cannot cross-compile.

**Try the cheaper options first.** In order:

1. Measure JVM CLI startup on the target hardware. The figures in this note are
   estimates.
2. AppCDS (JVM class-data sharing). No code changes.
3. GraalVM native-image of the JVM CLI. Reuses the JVM code as is, but needs an
   arm64 build host and reflection and resource configuration.
4. Kotlin/Native, once the core module split is wanted for its own sake.
