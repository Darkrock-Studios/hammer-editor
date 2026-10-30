# Client Plugins

How the Hammer client (desktop, Android, iOS) is extended with plugins, and how
the same API is exposed as a command line interface and an MCP server. The
server has its own plugin seam (`server/.../plugin/ServerPlugin.kt`) for the
hammer.ink overlay; the client does not follow it.

A client plugin is a sandboxed WebAssembly module installed from a package,
which anyone can write. Plugins do not add operations: the operation API is
Hammer's own, and a plugin is one more caller of it. Hammer's own plugins live
in the `hammer-plugins` repository, next to this one, built on the kits in
`hammer-plugin-sdk`; `hammer-plugin-development` holds the same few plugins in
six languages, for comparing them.

There are no compiled-in plugins: a feature that needs Kotlin and Koin belongs
in core.

## Scope

**Headless plugins.** A plugin adds behavior without adding a feature surface:
export formats, actions, CLI commands, text checks, and a settings form the
host renders. It cannot add a new kind of thing with its own data type and
screen; see [Feature plugins](#feature-plugins-not-supported).

**One API, several front ends.** The operations (list projects, read a scene,
write a scene, link an entry) are defined once and exposed to the CLI and to
plugins. MCP is not core: it is a plugin, built on plugin commands (see
[MCP plugin](#mcp-plugin)). The CLI also runs sync, so any machine that can
run the JVM can be a headless sync client.

## Architecture

```
          runtime plugins (Wasm)       hammer <command>        hammer mcp
                    |                        |              (a plugin's
                    |                   CLI adapter          CLI command)
                    |                        \                    /
                    |                         running app? --yes--> forward over local socket
                    |                              | no                    |
                    v                              v                       v
               +---------------------------------------------------------------+
               |                Operation registry (:operations)               |
               +---------------------------------------------------------------+
                                              |
                                repositories, services, Koin
```

External tools call operations through the CLI, or through a plugin's CLI
command such as `hammer mcp`. The only code loaded at runtime is sandboxed
WebAssembly, and it reaches Hammer only through operations, within the grants
the user approved (see [Runtime plugins](#runtime-plugins-wasm)).

## Modules

```
:base <- :common <- :operations <- :plugins:wasmhost <- :composeUi <- :android, :desktop
```

| Module | Holds |
| --- | --- |
| `:common` | The data layer, with the few seams plugins reach it through (below) |
| `:operations` | `Operation`, `OperationRegistry`, the core operations, `ClientPlugin`, `PluginRegistry`, `PluginSettingsDatasource`, `ActionSessionStore` |
| `:plugins:wasmhost` | The [runtime plugin](#runtime-plugins-wasm) host: chasm, the package loader, `RuntimePlugins`, and `WasmPlugin`. All platforms |
| `:composeUi` | `PluginUiRegistry`, the Plugins section of Settings, and the plugins' actions: their menu items, and the runner that shows their pages and progress and keeps their runs |
| `:desktop` | The CLI adapter and `Dispatcher`, socket forwarding, the app, window, and writer locks |

`:wear` depends only on `:common` and gets no plugins.

`:operations` and the host target the same platforms as `:common` (Android,
desktop JVM, iOS), because the host is common code.

**`:common` holds no plugin types.** What plugins need of it is a few seams:

- `StoryExporterRegistry` holds the built-in formats, then asks each
  `ExporterSource` bound in Koin for more on every lookup. `PluginRegistry` is
  one, so plugin formats come and go as plugins do.
- `ProjectPluginsRepository` keeps the plugins each project turns off.
- `SecretDatasource` keeps `secret` settings.
- `ProjectIdChangeListener` and `EntityIdChangeListener` report the ids
  `ProjectsRepository` and sync change, which action sessions follow.
- `EntityCatalog` lists a project's items for an `entity` field's picker.

Anything else `:common` gains for plugins must make sense without them, or
the design is revisited rather than `:common` bent to fit.

## Operations

An operation is a named, typed unit of the public API.

```kotlin
package com.darkrockstudios.apps.hammer.operations

interface Operation<I, O> {
	/** Dotted, e.g. `scene.read`. */
	val name: String
	/** English. Used for CLI help and by plugins that describe operations to others. */
	val description: String
	val input: KSerializer<I>
	val output: KSerializer<O>
	val access: Access
	val scope: OperationScope
	/** Overridden only when valid values are known at runtime, such as `project.export`'s formats. */
	fun inputSchema(): JsonObject = jsonSchema(input.descriptor)
	/** For front ends that report success as a number, like the CLI: nonzero for a partial failure. */
	fun exitCode(output: O): Int = 0

	suspend fun run(context: OperationContext, input: I): O
}

enum class Access { Read, Write, Prose, Destructive }

/** What an operation reaches into. A plugin can be granted every Read or Write operation in a scope at once. */
enum class OperationScope { Content, Account, Plugins }

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

Most operations are written with the `operation<I, O>(name, description, access,
scope) { ... }` builder. Errors the caller caused are `OperationException`s with a kind
(`NotFound`, `InvalidInput`, `Unauthorized`, `Busy`); anything else thrown is a bug. `dispatch(name,
JsonElement)` is what front ends call: unknown input fields, missing fields, and
wrong types are `InvalidInput`. Binary content is base64 in JSON.

`OperationRegistry` holds the core operations, and is the single place every
front end dispatches through; plugins do not add to it. Project-scoped
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

## Operation catalog

Conventions:

- Names are `noun.verb`. The CLI maps dots to words: `scene.read` is
  `hammer scene read`.
- Project-scoped operations take `project`, either the project name or its
  server project id.
- Entity ids are the project's own ids. Sync can give an item made on this
  device a new id ([Re-ID](SYNCING-PROTOCOL.md#terminology)).
- Body text (scene, note, entry, timeline event, idea) is read from stdin on the
  CLI. An update replaces the body, so the body is required there too; the
  other fields keep their value when left out. An optional body would leave
  `hammer note update --tags x` waiting on stdin.
- **Read** operations change nothing. **Write** operations change content
  other than a scene's prose. **Prose** operations put text into a scene's
  prose; a plugin only gets one by naming it, but the CLI runs them without
  `--confirm`, since it is the writer's own tool. **Destructive** operations
  delete or overwrite in a way a draft cannot undo; the CLI requires
  `--confirm`, and a plugin only gets one by naming it.
- Each operation declares its **scope**: `content` (projects and everything in
  them), `account` (credentials and the sync server), or `plugins` (the
  runtime plugins installed). Nothing in the
  operation API knows about agents; a plugin that serves them, like the MCP
  plugin, offers what its grants allow.
- Operations whose input or output is a file (import, export, entry images)
  carry the bytes. The CLI maps them to `--in FILE` and `--out FILE`, or to
  stdin and stdout.

### Projects

| Operation | Access | Notes |
| --- | --- | --- |
| `project.list` | Read | Name, server project id, word count, last modified |
| `project.info` | Read | Metadata, statistics, sync linkage |
| `project.create` | Write | |
| `project.rename` | Write | Refused while the project is open, and while the app runs (not forwarded) |
| `project.delete` | Destructive | Refused while the project is open, and while the app runs (not forwarded) |
| `project.export` | Read | Format id and scene subset as in `ExportOptions`; returns the file's bytes |
| `export.formats` | Read | Available export formats: id, file extension, MIME type |
| `project.import` | Prose | Format and split options as in `ImportOptions`, plus the file's bytes |
| `backup.list` | Read | |
| `backup.create` | Write | |

### Scenes and drafts

| Operation | Access | Notes |
| --- | --- | --- |
| `scene.tree` | Read | Nested groups and scenes: ids, names, order, word counts |
| `scene.read` | Read | Markdown plus metadata |
| `scene.read.many` | Read | Story-order scenes (all, a group's, or by id) with markdown, metadata, and group path, paged by `maxWords` with an `after` cursor; leaves out archived scenes |
| `scene.write` | Prose | Replaces the text; `mode` is required, see below |
| `scene.append` | Prose | Appends to the live text; for capture devices and scripts |
| `scene.create` | Write | Parent, name, position, scene or group |
| `scene.rename` | Write | |
| `scene.move` | Write | New parent and index |
| `scene.archive` | Write | |
| `scene.unarchive` | Write | |
| `scene.archived` | Read | |
| `scene.delete` | Destructive | |
| `scene.meta.read` | Read | Outline, notes, tags, and references: entries confirmed as in the scene, and dismissed |
| `scene.meta.write` | Write | Outline, notes, tags |
| `scene.references.set` | Write | Confirm, dismiss, or clear entries the scene references |
| `draft.list` | Read | Drafts of one scene |
| `draft.read` | Read | |
| `draft.create` | Write | Snapshots the scene's current text under a name |
| `draft.apply` | Prose | Replaces the scene's text with the draft |
| `draft.delete` | Destructive | |

`scene.write` has no default mode. `mode: draft` saves the new text as a named
draft and leaves the scene alone. `mode: live` replaces the scene text through
the same service the editor uses, so an open editor sees the change instead of
clobbering it. It first saves the text it replaces, including an open editor's
unsaved edits, as a draft named "Before external edit", so a live write is
undoable and is Prose rather than Destructive. Draft mode is Prose too, since
a draft is prose. Edits the editor typed but had not yet debounced into its
buffer are dropped rather than landing on top. Neither a live write nor
`scene.append` credits the writer's activity. Both refuse archived scenes, and
scenes with unsaved edits left by a session that did not close, since saving
would discard them; opening the project in Hammer restores those first.

`draft.apply` replaces the text the same way, backup draft included. Deleting
or archiving a scene drops its in-memory buffer, so an editor's save-all cannot
recreate it; archiving saves the buffer first.

Input that edits scene text in place is marked `@LiveEdit`: the whole input of
`scene.append` and `draft.apply`, and `scene.write`'s `live` mode. The schema carries it as
`x-hammer-live`, so a client can tell an edit a writer reviews from one that
lands in place.

### Notes, encyclopedia, timeline

| Operation | Access | Notes |
| --- | --- | --- |
| `note.list` | Read | Optional tag filter |
| `note.read` | Read | |
| `note.create` | Write | |
| `note.update` | Write | |
| `note.delete` | Destructive | |
| `entry.list` | Read | Each entry's aliases; optional type and tag filters |
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

These are the `account` scope. A plugin may read it, through `account:read` or
by naming `account.status` or `sync.status`, but can never log in, log out, or
sync: they are listed in `PLUGIN_WITHHELD_OPERATIONS`, and a test keeps every
operation in this scope but its reads on that list. A package that requests
`account:write`, or names `account.login`, `account.logout`, or `sync.run`, is
refused at install, and dispatch refuses them whatever a plugin was granted.
Logged in to a server of its choosing, a plugin with no HTTP hosts could still
sync every project there. Account changes belong to the app's UI, which applies
safeguards these operations do not, such as refusing to change servers while a
project is open, whose sync state the change would leave stale. The CLI runs
all five, since it is the writer's own tool, but only while the app is closed:
the app runs its own sync and login, and refuses these over the socket (see
[Concurrency](#concurrency)).

### Plugins

| Operation | Access | Notes |
| --- | --- | --- |
| `plugin.list` | Read | Each installed plugin: id, name, version, enabled, and what it may use (operations, HTTP hosts, and the setting holding an address it may reach) |
| `plugin.install` | Destructive | An absolute path to a `.hammerplugin`; grants every permission its manifest asks for, which the reply lists. Applies at once in a running app |
| `plugin.uninstall` | Destructive | Removes the package, its cache, and its secrets; its other settings stay for a reinstall |

These are the `plugins` scope. Installing grants a plugin its permissions with
no prompt, so both changes are Destructive: the CLI asks for `--confirm`, and a
plugin only gets them by naming them. The operations live in `:operations`
behind `PluginInstaller`, which `RuntimePlugins` implements, since
`:operations` cannot depend on `:plugins:wasmhost`. The path must be absolute,
because a forwarded call resolves it in the app, not the shell.

### Deliberately excluded

- **Account creation and terms acceptance** stay in the GUI.
- **Server admin** is the server's web UI.
- **Global settings** are not exposed, nor are plugin settings.

## Plugin interface

### `ClientPlugin` (`:operations`)

What the rest of Hammer sees of a plugin. `WasmPlugin` is the one
implementation; the interface keeps `:operations`, `:common`, and the UI free
of the host.

```kotlin
package com.darkrockstudios.apps.hammer.operations.plugin

interface ClientPlugin {
	/** Stable, lowercase, directory-safe. Keys this plugin's settings file. */
	val id: String
	val name: String? get() = null

	/** Export formats this plugin adds, each prefixed with the plugin id, e.g. `smf.docx`. */
	fun exporters(): List<StoryExporter> = emptyList()

	/** Typed settings the host renders as a form. See Declared settings. */
	fun settings(): List<SettingDeclaration> = emptyList()

	/** Extra top-level CLI commands, such as `hammer mcp`. Desktop only. */
	fun cliCommands(): List<CliCommand> = emptyList()

	/** Items added to the menus of a project's screens. */
	fun actions(): List<PluginAction> = emptyList()

	/** Checks the editor runs over the text being written, such as grammar. */
	fun textDiagnostics(): List<TextDiagnosticsProvider> = emptyList()
}

class PluginAction(
	val pluginId: String,
	val name: String,
	val label: String,
	/** The screens whose menus it is in: project, scene, note, entry, event. */
	val places: Set<ActionPlace>,
	/** Its first page, which the host shows before calling it; with none, it is called at once. */
	val fields: List<ActionField>,
	/** Whether a run outlives its dialog being closed part way; see Sessions. */
	val keep: Boolean = false,
	/** The plugin's version; a kept run resumes only on the same one. */
	val pluginVersion: String? = null,
	/** Runs off the main thread with the project, place, item, the page's values and state, and the pressed button, reporting progress as it goes. */
	val run: suspend (ActionCall) -> ActionReply,
)

class TextDiagnosticsProvider(
	val label: String,
	/** Whether it gets every paragraph of the scene each time, rather than only changed ones. */
	val wholeScene: Boolean = false,
	/** The issues in each paragraph, as UTF-16 ranges plus a message, fixes, and severity. */
	val diagnose: suspend (TextDiagnosticsRequest) -> List<List<TextDiagnostic>>,
)

class TextDiagnosticsRequest(val paragraphs: List<String>, val language: String?, val project: String)

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

Plugins have no lifecycle hooks and no Koin access. Everything a plugin adds is
looked up while the app runs, which is what lets plugins be installed, enabled,
disabled, and uninstalled without a restart.

### Exporters

```kotlin
package com.darkrockstudios.apps.hammer.common.data.export

interface StoryExporter {
	/** Stable id, e.g. `epub`. Plugin formats are prefixed with the plugin id, e.g. `plaintext.txt`. */
	val formatId: String
	val fileExtension: String
	val mimeType: String
	/** Shown in the export dialog; the built-in formats' labels are localized in `:composeUi` instead. */
	val label: String? get() = null
	/** False only for formats that ignore project data (such as Markdown), which skips loading it. */
	val needsProjectData: Boolean get() = true

	fun render(sink: BufferedSink, input: ExportInput)
}

class ExportInput(
	val projectName: String,
	val projectData: ProjectData?,
	/** One per top-level node of the scene tree. */
	val chapters: List<StoryChapter>,
	val treatTopLevelAsChapters: Boolean,
	val options: ExportOptions,
	val language: String,
	val strings: ExportStrings,
) {
	fun requireProjectData(): ProjectData
	/** [chapters], or one chapter named after the project when top-level nodes are not chapters. */
	fun bookChapters(): List<StoryChapter>
}
```

- **Built-in formats.** The five built-in formats are `StoryExporter`s too,
  with ids `epub`, `docx`, `rtf`, `pdf`, and `markdown`, so built-in and plugin
  formats take the same path. `ExportOptions.format` is a format id.
- **`StoryExporterRegistry`** (in `:common`'s `data/export`, with
  `ExportStoryUseCase` and the renderers) lists the built-in
  formats first, in their menu order, then the others by id (see
  [Modules](#modules)). `ExportStoryUseCase` takes each format's renderer and
  file extension from it, and the Android save picker its MIME type.
- **Labels.** Built-in labels are localized in `:composeUi`. A plugin format
  shows its manifest label, or its file extension in upper case.

Importers have no plugin seam: `ImportFormat` is a closed enum.

### The registry and the UI

`PluginRegistry` (`:operations`) holds the active plugins as a `StateFlow`.
`RuntimePlugins.activate` adds the enabled ones at startup, in the app and in
each CLI process alike, and from then on applies every install, enable,
disable, and uninstall to it at once. A plugin is checked as it is added: a
valid id, prefixed export formats, valid settings, and no CLI command another
plugin has or that shadows an operation. One that fails is refused with nothing
changed. A replaced plugin gets a new settings store for its own declarations.

Install, permissions, the enable toggle, and settings are app-wide. A project
can also turn an enabled plugin off for itself, on this device only: the ids
are kept by project id in `<config>/project_plugins.toml`
(`ProjectPluginsRepository`), never in the project, so they are not synced.
The entry follows its project when `ProjectsRepository` changes the project's
id, and goes when it deletes the project.
Every enabled plugin not listed is on, including ones installed later. The project's settings
list each enabled plugin with a toggle. A plugin turned off for
a project shows no actions, text checks, or export formats in it, and
`project.export` refuses its formats there. CLI commands run outside any
project and ignore this. Plugins stay loaded either way; each lookup filters by
the open project (`PluginRegistry.forProject`, and the `disabledPlugins`
arguments of `PluginUiRegistry` and `StoryExporterRegistry`).

`PluginUiRegistry` (`:composeUi`) follows the registry:

- **Settings.** A Manage plugins button, in the Plugins section of Settings and
  in each project's settings, opens a dialog listing installed plugins, each
  with an enable toggle, Uninstall, and, when it has declared settings or CLI
  commands, a Settings button that opens them in a dialog: the declared form,
  then how to run each command. Plugins are installed and set up for every
  project, but reaching them from a project saves leaving it. The button is
  absent where runtime plugins are not registered.
- **Actions.** A plugin's actions appear in the overflow menus of the screens
  their manifest names. `PluginActionHost`, at the project root, gives those
  menus a `PluginActionRunner` for the open project, which calls an action off
  the main thread and shows the pages it replies with, so a plugin needs no UI
  state of its own. Messages and failures are toasted. See
  [Actions](#actions).
- **Action dialog.** Shows an action's page, titled with the page's title or
  the action's label, and Copy and Save as note on the pages
  [Buttons](#buttons) names. Hammer runs both, so saving needs no permission
  from the plugin.

- **Text diagnostics.** The scene editor and focus mode run every active
  plugin's checks, underlining what they find: a mistake with a wavy blue
  line, apart from spell check's red, and a suggestion, such as a style
  issue, with a quieter dotted gold one. A right-click or tap on an underline
  shows its message and fixes; picking a fix replaces the text. The editor
  library does the work (`TextDiagnosticsState` in ComposeTextEditor's
  spellcheck module): it keeps results by paragraph text, so after an edit
  only changed paragraphs go to a check, and each underline carries its
  diagnostic, so it moves with the text. A check whose issues depend on other
  paragraphs, such as a word echoed a paragraph later, declares scene scope
  and gets every paragraph on every check instead; the library runs it
  through `TextDiagnosticsChecker.checkText`. A change to a checking plugin's
  settings checks the whole text again. The plugins get the editor's text,
  markdown already rendered away, the project's language, and the project's
  name, so a check can read the project through operations it has permission
  for, such as the encyclopedia's names. Spell check is separate: it loads
  suggestions only when a word is clicked and re-checks single words, where
  diagnostics carry their fixes up front and re-check whole paragraphs.

These are the only UI slots.

## CLI

The CLI is the normal desktop binary with subcommands, not a separate target.
Every package format (Flatpak, Snap, MSIX, the Mac bundle) already ships one
launcher, and a second target would double that work. A first argument that
does not start with `-` is a CLI call (`Cli`); anything else launches the app,
parsed by the Clikt command in `DesktopLaunchArgs.kt`. The CLI parses its
own arguments, since every command and option comes from an operation's schema
at runtime.

```
hammer project list
hammer scene read --project "My Novel" --id 12
hammer scene write --project "My Novel" --id 12 < revised.md
hammer mcp        # the MCP plugin's command
```

- Subcommands are generated from the operation registry: `scene.meta.read` is
  `hammer scene meta read`. Each input field becomes a kebab-case option typed
  by its schema; list fields repeat the option, booleans may omit `true`, and
  `--json` passes the whole input instead. A body text field, such as
  `scene.write`'s `markdown`, is read from stdin when not given. `hammer help` lists everything,
  and `--help` after a command lists its options.
- Output is the operation's output type as JSON. `--out FILE` writes an
  output's one binary field (an export's bytes) to a file and prints the rest,
  and `--out -` writes the bytes alone to stdout. `--in FILE` (or `--in -`)
  fills an input's one binary field, such as an entry image, the same way.
- `--dev` before the command, as in `hammer --dev project list`, uses
  development data, as it does for the app, and so reaches a `--dev` window.
- Destructive operations need `--confirm`; without it the CLI refuses before
  starting Hammer. Prose operations do not, since the CLI is the writer's own
  tool. Allowing an agent to run `hammer *` includes the account and sync
  commands, which no plugin can be granted.
- Exit codes: 0 success, 1 failure, 3 not logged in or login rejected, 4 not
  found, 5 another Hammer command holds the writer lock, the open app is not
  answering, is not ready for calls, or refuses one it keeps to itself (see
  [Concurrency](#concurrency)), or the project is syncing, and 64 (sysexits'
  EX_USAGE) for usage errors and invalid input. An
  operation can report partial failure through `Operation.exitCode`: `sync.run` returns 2 when a project needs
  resolution.
- A field marked `@FromStdin` is read from stdin when not given as an option; a
  secret one (the login password) is never accepted as an option, and can also
  come from an environment variable (`HAMMER_PASSWORD`).
- Plugin `cliCommands()` are added alongside the generated subcommands. A plugin
  command cannot shadow a generated one.
- With a subcommand, `main` starts Koin without opening a window, runs the
  command, and exits (`HeadlessSession`). Logs go only to the log file.
- JVM startup plus Koin init is on the order of a second per call on a desktop,
  several on a Pi Zero.
- Getting `hammer` onto PATH differs per package; see
  [Getting onto PATH](#getting-onto-path).

### Getting onto PATH

Desktop Settings has a Command line section. It shows the launcher, the
command an MCP client or script runs (`cliLauncher()`), and, where Hammer can,
a button that adds a `hammer` command to PATH, or updates or removes it:

| Package | `hammer` comes from |
| --- | --- |
| Snap | The package: the app is `hammer-editor` in `/snap/bin`. A `hammer` alias needs a Snap Store request |
| Flatpak | A script in `~/.local/bin` running `flatpak run studio.darkrock.hammer`; `--filesystem=home` lets the app write it |
| AppImage | A script in `~/.local/bin` running the `.AppImage` file (`$APPIMAGE`), since the mounted image moves each run |
| deb, rpm | A script in `~/.local/bin` running `/opt/hammer/bin/hammer`. jpackage's post-install scripts are not reachable through Compose Desktop |
| Mac DMG, pkg | A script in `/usr/local/bin`, written through macOS's own administrator prompt when that directory is not writable |
| Mac App Store | The sandbox keeps the app from writing it, so Settings shows a command to paste into Terminal |
| Windows MSI, EXE | `hammer.cmd` in `%LOCALAPPDATA%\Hammer\bin` running `hammer-cli.exe`, with that folder added to the user's own PATH |
| Microsoft Store | An app execution alias, `hammer.exe`, on a second, hidden app entry for `hammer-cli.exe` |

Each script carries a marker line, so Hammer only ever replaces or removes its
own; a `hammer` someone else put there is left alone. In a `--dev` window, the
full command shown in Settings and in plugin dialogs ends in `--dev`, so what
is set up from it reaches that window; the PATH script never does. On Linux, Settings says
when the script's directory is not on PATH.

**Windows needs a console launcher.** jpackage gives the app one launcher,
`hammer.exe`, built as a GUI program, which a terminal does not wait for and
whose output it never shows. The build adds `hammer-cli.exe` beside it: the
same binary with its PE subsystem set to console, as `editbin
/SUBSYSTEM:CONSOLE` would, plus its own copy of `app/hammer.cfg`, since the
launcher finds its config by its own name (`addWindowsConsoleLauncher` in
`buildSrc`). The installers and the MSIX are built from that app image. Store
apps cannot be run from their install folder, so the alias is also what MCP
clients use there. The PATH change is PowerShell writing `HKCU\Environment` and
broadcasting `WM_SETTINGCHANGE`.

Known limitations on Windows: `hammer.cmd` is read in the OEM code page, so an
install path with non-ASCII characters breaks it, and uninstalling Hammer
leaves the script and PATH entry behind.

## Concurrency

The running app keeps scene buffers and the next entity id in memory, writes
projects that are not open in a window (sync-all, backups, renames from the
project list), and refreshes the account token, which the server rotates on
every refresh for a given install id. A second process doing any of that at the
same time collides on ids, clobbers buffers, desyncs the sync journal, or
invalidates the other process's token. So there is exactly one writer at a
time for the whole data set, not per project.

**Locks.** Three OS file locks in the config directory (next to the global
settings, outside the projects directory, so backups, renames, and restores
never touch them). OS locks release on process death, so there are no stale lock
files to clean up.

- `window.lock` marks open app windows. Every window holds a shared lock on it
  for its whole run, including a second window that could not hand off.
- `app.lock` picks the one window that serves the socket. It takes it first
  thing and holds it for its whole run.
- `writer.lock` is the one writer. A headless call holds it for the duration of
  that call. The app takes it right after `app.lock`, or, if a headless call
  already has it, as soon as that call finishes, without holding up the window.
  The holder writes `App` or `Cli` to `writer.owner` beside it, so a headless
  call that finds it held knows whether waiting can help: held by the app, the
  call goes to the app's socket instead; held by another headless call, it
  waits up to three seconds, then fails.

A headless call checks for windows only once it holds `writer.lock`, by trying
`window.lock` and `app.lock` exclusively for a moment, and gives the writer lock
back if a window is open. So no new headless call starts while any window is
open. One already running may finish: a window that opens during it starts as
usual, and serves CLI calls once the writer lock is free. On close, the app
lets forwarded calls in flight finish before it releases its locks.

On macOS and Linux, closing any channel to a file drops every lock the process
holds on it, so a window never opens a lock file it holds a second time.

**Forwarding to a running app.** The app, as the `app.lock` holder, listens on a
Unix domain socket, `run/hammer.sock` in the config directory (supported by the
JDK on Windows 10+ as well). `run/` is owner-only, so the socket is private from
the moment it exists; on Windows it inherits the user profile's per-user ACL. A
stale socket left behind is replaced on startup. It binds before starting Koin
or migrating data, and holds calls
until it has the writer lock and has migrated, refusing them as not ready if
that takes more than half a minute. On a system without Unix domain sockets the app
runs without one, so CLI calls refuse while it is open, and a second launch
opens its own window. Every CLI call tries the socket
first. If the app answers, the CLI sends the operation name and input, the app
runs it against its live state, and the CLI prints the result. If nothing
answers and no app is open, the CLI takes the writer lock and runs the operation
in-process. If an app is open but its socket does not answer, the CLI retries
for up to ten seconds, then fails saying Hammer is not taking commands yet; it
never runs headless under an open window.

The app refuses a few operations over the socket, and the CLI exits 5 with a
message to use the app or close it first:

- every `account.*` and `sync.*` operation, since the app runs its own sync and
  login, which a forwarded call must not race;
- `project.rename` and `project.delete`, since moving a project's directory
  would race anything in the app that opens it, such as a sync.

Forwarding is always on; there is no setting for it. Anything that can reach
the socket runs as the same user, who could edit the project files directly, and
the socket's directory is theirs alone. What an agent may do is decided where it
is installed: the MCP plugin's granted operations and its settings. Every other
operation is forwarded rather than refused while the app is open, since
refusing would only push tools to wait for it to close, or to edit files under
it.

**Multiple app instances.** A second launch of the app finds `app.lock` held,
hands its launch arguments to the first over the socket, and exits, so the app
is effectively single-instance. The first takes them at once, even while it is
still starting. The first comes to the front
and acts on them as its own: a `--project` (with any `--scene`, `--note`,
`--entry`, or `--timeline-event`) opens that project, going straight there if it
is already open, or first closing the open one the usual way, with its
unsaved-changes prompts; declining the prompt drops the request. A launch that
cannot hand off retries for up to ten seconds, in case the first is still starting,
then starts its own window, with `window.lock` but without the other two, which
it takes once the first window closes.
Windows jump-list items, which relaunch the executable, go through the same
path, as do the Linux quicklist and macOS Dock menu in-process.

Nucleus's own single-instance mode (`enableSingleInstance`) is off: its lock
lives in the temp directory under one app id, so a `--dev` window and a normal
one would block each other, and it hands over only a deep-link URI, not launch
flags. Hammer's locks and socket are per config directory.

**No state between calls.** Each headless call, including each dispatch from a
long-running plugin command like `hammer mcp`, starts Koin, runs, and stops it.
Nothing loaded for one call (id allocator state, repositories, settings, the
cached auth token) survives to the next, and every call re-checks the socket, so
an app launched mid-session is picked up on the next call.

Repositories resolve through Koin's global context, so this is a stop and
restart of the global context, and calls within one process run one at a time.
That is why `CliCommand` reaches Hammer only through `Dispatcher`: anything it
resolved from Koin would be dead after the first call. It also means project
and app scopes must shut down cleanly on `stopKoin`: `HeadlessRestartTest`
starts the real Koin graph several times, running migration and read
operations each time, and fails on any work that outlives a stop.

Forwarding and locking are desktop only. Android and iOS have no CLI and are
single process.

**Writes during a sync.** Within the process, a project's sync and the write
operations on it exclude each other through `ProjectSyncGateRepository`, keyed by
the project's path. Every project sync (the sync dialog, Sync all, the Android
widget's background sync, `sync.run`) goes through
`ClientProjectSynchronizer.execute`, which waits for writes already running on the
project, then holds it to the end. Any operation but a Read in the
`content` scope holds each project it names until it returns, and one that names
a project mid-sync fails at once as `Busy`: "Project '…' is syncing; try again
when it finishes." The CLI exits 5, and the MCP plugin returns a tool error.
It does not wait, since a sync can sit on a conflict for as long as the writer
takes to resolve it, and a write that never waits cannot deadlock against
anything a sync triggers. Writes run alongside each other, so a write a sync is
waiting on can still run the operations it is built on. Reads are never held.
Account and plugin operations are not gated; `sync.run` is a sync itself.

## MCP plugin

MCP is a runtime plugin, in hammer-plugins' `mcp`, written in
Kotlin/Wasm. The host provides only generic pieces: plugin commands, scope
grants, and `ops.list` filtered to the grants.

Its manifest requests `content:read` and `plugins:read`, then by name the writes
that organize a project, the deletes of everything inside one, and plugin
install and uninstall, and declares a `mcp` command. An MCP client launches `hammer mcp` as a child process and
speaks JSON-RPC over stdio, one message per line; the host hands each line to
the plugin (see [Commands](#commands)). The plugin answers `initialize`,
`ping`, `tools/list`, and `tools/call`, and ignores notifications.

Its tools are what `ops.list` returns it, which is exactly what it may call,
less `ops.list` itself: `tools/list` already describes each tool, and only it
reflects the plugin's settings.
Tool names replace the operation's dots with underscores (`scene_read`), since
some clients reject dots. A failed call, including one refused because Hammer
is busy, is a tool error the agent can read, not a protocol error. Every call
goes through the CLI's `Dispatcher`, so it works against the running app's live
state or, with no app running, directly against the files. No network port and
no token management.

**No prose.** Hammer's stance on AI is a tool for the writer, not a
replacement, so the plugin requests no operation that writes a scene's text:
not `scene.write`, `scene.append`, `draft.apply`, or `project.import`. An agent
can read the whole story and help around it, with the encyclopedia, timeline,
notes, outlines, and structure, but the prose stays the writer's. The host
enforces this for every plugin, not only this one: those operations are Prose,
which no scope grant covers, so a plugin reaches one only by naming it and the
writer sees it at install under its own heading. The CLI still writes scenes
for the writer's own scripts.

**Settings.** Three settings, all off by default, gate what it does request.
"Let AI agents make changes" offers the writes: creating, renaming, moving, and
archiving scenes, scene outlines, tags, and references, drafts as snapshots, notes,
encyclopedia entries and their images, the timeline, ideas, projects, and
backups. "Let AI agents delete things" offers the deletes, with MCP's
`destructiveHint`, so a client can warn before running one; `project.delete` is
never requested. "Let AI agents install and remove plugins", a `danger`
setting, offers `plugin.install` and `plugin.uninstall`; `plugin.list` is
always offered. While a setting is off, its tools are not tools at all.
Granting by name at install, the setting, and the client's own prompt before a
tool call stand in for the CLI's `--confirm`. Settings are read for every
message, so turning one off applies to a running server; tools a setting adds
appear once the client lists tools again, which most do on reconnecting.

Installing and enabling the plugin is what turns MCP on. Settings shows the
command line to give an MCP client.

## Headless sync

`hammer sync run` does what the "Sync all" button does: the account phase
(project creates, renames, deletes, and ideas), then every linked project,
skipping ones the server reports unchanged.

```
hammer account login --url https://hammer.ink --email me@example.com < password.txt
hammer sync run
hammer sync run --project "My Novel" --on-conflict server
```

- **Orchestration.** `sync.run` is a CLI listener on `SyncAccountUseCase`, the
  data layer's sync-all code, which reports through a `SyncAccountListener`,
  takes a project filter, and returns a per-project `ProjectSyncOutcome`.
- **Conflicts.** `--on-conflict abort|local|server`, default `abort`. Abort is
  what bulk sync does: the project stops, is reported as needing resolution,
  and other projects continue. `local` and `server` apply to idea conflicts
  only: `SyncAccountUseCase` always stops a conflicted project, which is then
  resolved in the app.
- **Output.** A JSON summary with each project's outcome, and the sync log
  lines, goes to stdout when the sync ends; the log is not streamed.
- **Exit codes.** 0 all synced, 1 failure, 2 at least one project needs
  resolution, 3 unauthorized (run `account login` again). These map from
  `ProjectSyncOutcome`: `Failed` is 1, `NeedsResolution` is 2, and `Success`,
  `Unchanged`, and `Skipped` are 0. `onUnauthorized` gives 3.
- **Login** takes the server, email, and password (from stdin). A server that
  asks for terms acceptance or a sign-in code is refused with a message to log
  in from the app once, since both stay in the GUI.
- **Credentials.** The desktop token store is an AES-encrypted file keyed on
  the user name and home directory. It needs no keyring or desktop session, so it
  works on a bare Linux box.
- **App running.** The app refuses `sync.run`, and every other account and
  sync operation, over the socket (see [Concurrency](#concurrency)), so only
  the app syncs while it is open: its sync state stays coherent and only one
  process ever refreshes the token. The CLI exits 5 with a message to use the
  app or close it first.

What this enables: a cron job on a NAS or Pi acting as an always-on sync peer
and backup mirror, the Typewriter device syncing without the GUI's involvement,
and scripted pipelines such as sync then export.

**Hardware floor.** Anything that runs a 64-bit JVM, which starts around a
Pi Zero 2 W (512 MB, Cortex-A53). There, startup is several seconds per call,
which is fine for cron sync and slow for chatty tooling.

## Settings

Plugins do not add fields to `GlobalSettings`. Each gets its own file in the
settings directory, `plugins/<id>.toml`, read and written through a
`PluginSettingsDatasource` that replaces the file atomically, so an interrupted
write cannot reset it. (It is a datasource by name because `:common`'s
architecture rule keeps raw TOML I/O in datasource files.) This keeps plugin
schemas out of the core settings migrations.

Plugins have no durable storage of their own beyond settings; see
[Limitations](#limitations).

### Declared settings

A plugin's settings are a handful of typed values, which it declares in its
package's `settings.toml`; the host renders the form, since a module cannot
supply Compose UI.

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

- **Types.** `bool` (toggle) with optional `danger`, `int` with optional `min`
  and `max`, `string` with optional `multiline`, `secret`, and `choice`
  (dropdown).
- **Danger.** A `bool` with `danger = true` turns on something risky, such as
  letting agents install plugins; the form shows a warning under it.
- **Secrets.** A `secret` is a password or API key: masked in the form, never
  written to `plugins/<id>.toml`, and kept in the platform's encrypted store;
  see [Secrets](#secrets). It has no default, and the plugin gets it as a
  string, empty when unset. Actions cannot ask for one.
- **Where it lives.** `settings.toml` in the package, parsed into
  `SettingDeclaration`s (`:operations`, no Compose types).
- **Storage.** Values go in the plugin's existing `plugins/<id>.toml`, one key
  per setting, apart from secrets. Only values the user set are written, so a
  default stays the plugin's: an update that changes one changes it for anyone
  who never set it. On load, each value is checked against its declaration; a
  missing, mistyped, or out-of-range value falls back to its default, and keys
  the declaration lacks are dropped on the next write.
- **Reading values.** A plugin receives its current settings as JSON with
  every call, so it never needs a host call to read them. A command reads them
  afresh for each line, so a change applies to a running `hammer mcp`.
- **Rendering.** `:composeUi` turns a declaration into Hd components: a toggle
  row, a number field, a text field, a password field, or a dropdown, in the plugin's settings
  dialog. Labels are plain strings in the declaration.
- **Access.** `PluginRegistry.settings(pluginId)` is the plugin's
  `DeclaredSettingsStore`: current values as a `StateFlow<JsonObject>`,
  `decode(serializer)` into the plugin's own class, and `set(key, value)`,
  which ignores undeclared keys and invalid values.

## Strings

Hammer's own words around plugins (Run, Cancel, the install dialog) are string
resources, translated with the rest of the app. A plugin's own words come in
two kinds, each translated by the plugin's author:

- **Declared words.** The manifest's name, description, and labels, command
  help, and `settings.toml`'s labels, hints, and option labels. A package may carry
  translations of them in `locales/<tag>.toml`, one file per language, keyed
  as the manifest keys them:

  ```toml
  name = "Générateur de noms"
  description = "Propose des noms dans un style choisi."

  [actions.names]
  label = "Générer des noms"

  [actions.names.fields.style]
  label = "Style"
  options = { everyday = "Anglais courant", norse = "Nordique" }

  [settings.greeting]
  label = "Salutation"

  [exporters."names.txt"]
  label = "Noms (TXT)"
  ```

  Hammer reads them when it loads the plugin, and for the install dialog, and
  takes each string from the most specific file for the UI's language (`fr-CA`,
  then `fr`), falling back to the manifest's. Ids, keys, and values never
  change. Install refuses a file that does not parse or is not named for a
  language tag.
- **Replies.** Messages, action pages (their labels and buttons included), and
  check messages a plugin writes at run time. Every call carries `locale`,
  the UI's BCP 47 tag, so the plugin can write them in the user's language.
  It is not the project's `language`, which says what the story is written
  in: a French writer may write in English.

The UI's language is the system's, as for the rest of Hammer, so a plugin's
declared words are chosen once, when it loads. Operation descriptions are plain
English strings: they are read by agents and shown in CLI help, neither of which
is localized.

## Example plugins

Hammer's own plugins, in `hammer-plugins`, are real features rather than test
fixtures. Between them they use every slot:

| Plugin | Language | Shows |
| --- | --- | --- |
| `plaintext` | C | An exporter, `plaintext.txt`, reading each scene as Hammer parses it (`input = "prose"`), with declared settings. `hammer project export --format plaintext.txt`, and export through MCP, need no plugin code |
| `style` | C | A project action whose first page is a `scenes` field, with progress, a document page, the [cache](#cache) keyed by each scene's text, per-language [resources](#resources), and translations |
| `reading-level` | Kotlin | A project and scene action with a scene picker page of its own, resources, and the cache |
| `simple-grammar` | C | A text check, with byte offsets the host turns into the editor's UTF-16 ranges, written on the C kit's `hammer_diagnose.h` |
| `harper-grammar` | Rust | A text check pre-initialized with Wizer: a module of over 100 MB declaring `limits.memory = 256` |
| `name-checker` | Rust | A text check and a document-page action in one plugin. The check reads the encyclopedia through `entry.list`, using the request's `project` |
| `languagetool` | Rust | [HTTP](#http) to declared hosts, `userHosts` for a self-hosted server, and `secret` settings for the Premium API key and the server address |
| `fantasy-names`, `real-names`, `scifi-names` | Kotlin | Action fields remembered for the next run, inline buttons, `state` across calls, and page-level `errors` |
| `character-interview` | Kotlin | A run of pages with Back (`validate = false`), `errors`, `open`, and a kept [session](#sessions) (`keep = true`) |
| `first-meeting` | Kotlin | `entity` fields, one defaulting to the entry it was run from, `open`, and References read through `entry.read`'s `referencedBy` |
| `mcp` | Kotlin | Plugin commands and scope grants; see [MCP plugin](#mcp-plugin) |

Hammer ships with none of them installed.

## Limitations

- **No content-change events.** A plugin that wants to react to edits (live
  stats, a background linter) has nothing to subscribe to.
- **Settings are global.** A project can turn a plugin off, but a plugin's
  settings are the same in every project.
- **No durable store of a plugin's own.** A plugin keeps its settings and a
  cache the OS may clear. A kept action's `state` is the host's to keep, and
  only for that run.
- **References are only as accurate as the writer keeps them.** First
  meeting, and any plugin built on `entry.read`'s `referencedBy`, counts a
  character in a scene when it is in the scene's References. Hammer adds an
  entry to a scene's References when the scene's text names it, as the scene
  is saved in the editor or the entry is created, renamed, or given an alias,
  and never takes one away. So a character only mentioned is counted until
  the writer dismisses it in Scene Info, and one there but never named is
  missed until the writer adds it. `scene.meta.read` and
  `scene.references.set` let a tool correct them; the MCP plugin's
  `true-up-references` skill does so with the writer's approval.

## Feature plugins (not supported)

A feature plugin would add a whole new kind of thing, with its own data type,
screen, sync, and search integration (brainstorming cards, say). Plugins
cannot. A new entity type touches every one of:

- `EntityType` and `ApiProjectEntity` in `:base` (shared with the server)
- `IdAllocator` and its per-type `IdDatasource`s
- `EntitySynchronizers` and a client and server synchronizer pair
- `SearchProjectUseCase`
- `ProjectRootRouter.Config` and the navigation rail
- `DataMigrator`

The rules that keep feature plugins possible are:

1. Nothing in `ClientPlugin` or the operation API references any of the types
   above.
2. Plugin ids are stable and match the server plugin id convention, so a
   feature plugin can be one id with a client half and a `ServerPlugin` half.
3. Any per-plugin storage is explicitly *not* synced. Owning synced entities
   would be a separate, opt-in capability of an opened-up entity model, rather
   than something plugins fall into by storing data.

## Runtime plugins (WASM)

Runtime plugins let anyone write a plugin and let users install it, by running
WebAssembly in a sandbox. `WasmPlugin` presents each as a `ClientPlugin`.

### Runtime

[chasm](https://github.com/CharlieTap/chasm), a WebAssembly interpreter written
in Kotlin Multiplatform, MIT and Apache licensed. It is published for JVM,
Android, iOS, macOS, Linux, and Windows, so one host in `commonMain` covers
every target with no native libraries to package. It never generates code at
runtime, which iOS forbids. It supports Wasm 3.0 plus GC, exception handling,
and typed function references (what Kotlin/Wasm output needs), and WASI
Preview 1 through a companion library. It does not support SIMD or Memory64.

### Languages

Plugins are supported in three languages: Kotlin/Wasm, for the most comfortable
way to write one (a kit with typed requests, a `hammer.plugin` Gradle plugin that
does the build, and unit tests on the JVM against a fake Hammer, with an
`ActionDriver` that steps through an action's pages as Hammer does); Rust, for
speed (about half C's speed on a whole-book report, where Kotlin runs at about
a fifth); and C, the fastest and smallest, written by hand with native unit
tests. The kits for all three are in `hammer-plugin-sdk`. hammer-plugin-development
has the same few plugins in Zig, AssemblyScript, and Go (through TinyGo) as
well, and its `LANGUAGES.md` compares them all.

Kotlin plugin authors should ship the production build, which binaryen
optimizes: it runs about five times faster than the development build and is a
fraction of its size. Kotlin/Wasm's objects live in
chasm's guest heap rather than the module's linear memory, so that heap has a cap
of its own: 1 GiB, or half the JVM's heap if that is less, beyond which a call
fails with "ran out of memory". Hammer runs chasm's `ARENA` collector, which frees
a call's garbage when the call returns, so a call holds all it allocates: the
reading level report needs 192 MiB on a 100,000-word novel and 384 MiB on a
300,000-word one, and hammer-plugin-development's Kotlin style report on the
longer needs 768 MiB with a 22-word vocabulary and over 1 GiB with an
8,000-word one. chasm's `TRADITIONAL` collector, which runs during a call,
corrupts live Kotlin/Wasm objects in chasm 2.0.0; it is not used on 2.1.0, the
version Hammer pins. The desktop JVM's default heap is a quarter of the machine's
memory, so an 8 GB machine allows about 1 GiB; Android's app heap, 192 to 512 MB,
allows 96 to 256 MiB. The heap never gives pages back, so a plugin left holding more than 64 MiB
after a call is dropped and loaded afresh when next used. The cap needs the JVM:
on iOS the heap is uncapped.

Rust has a kit of its own in `hammer-plugin-sdk` (`rust/hammer`): Hammer's
imports, operations, and the cache, the `diagnose` request and reply types,
character to byte offsets, an optional `getrandom` backend on the host's
`random_get`, panics reported as the call's error, and an allocator that
zeroes freed memory. Its `tools/finish.py` strips what crates written for
the browser leave behind (wasm-bindgen's exports, and a trap in place of any of
its imports still reachable) and fails the build if the module imports
anything Hammer does not offer. A plugin whose start-up is slow can run it at
build time under [Wizer](https://github.com/bytecodealliance/wizer), which
saves the memory start-up leaves into the module; the zeroing allocator keeps
what start-up threw away out of it.

The C kit (`c/include`) builds freestanding (`--target=wasm32-unknown-unknown
-nostdlib`), so a C plugin imports nothing but Extism and Hammer functions.
On top of Extism's `extism-pdk.h`, `hammer.h` has the dispatch import,
`hammer_call(operation, input_json)`, the API version, and the `memset`,
`memcpy`, and `strlen` clang emits calls to even then. Beside it,
`hammer_json.h` reads JSON, `hammer_out.h` writes output, `hammer_text.h` finds
words, `hammer_action.h` reads an action's request and writes its reply (a
message, a page of fields and buttons, errors, or an item to open), and
`hammer_diagnose.h` does a `diagnose` export's reading and writing, leaving a
checker only its rules; `hammer_progress` reports a long action's progress.
With `HAMMER_NATIVE`, a plugin builds for the machine instead, and
`hammer_test.h` unit tests it there.

The host's own tests do not need these repositories: their fixtures are WAT, in
`:plugins:wasmhost`'s `src/desktopTest/wat`, whose `build.sh` writes the
compiled fixtures (a few hundred bytes each) into its test resources.

### Words

The kits' `words()` (`String.words()` in Kotlin, `words` in Rust, `hammer_words`
in C) split text the same way Hammer counts words, so a plugin's counts match
the app's. The kits are in `hammer-plugin-sdk`, and each must follow the rules
here; Hammer's own implementation is `WordSegmentation.kt` in `base`'s
`markdown` package.

The rules are Unicode 17's default word boundaries from UAX #29, rules WB1 to
WB999, with no dictionary and no locale tailoring. A word is a segment between
two boundaries that holds at least one letter or digit: a letter is a code
point of General_Category L (Lu, Ll, Lt, Lm, or Lo) or of Word_Break ALetter,
Hebrew_Letter, or Katakana, and a digit is one of Word_Break Numeric. So
"don't" and "rock’n’roll" are one word each, "U.S.A." gives `U.S.A` (the inner
periods stay, the last does not), "3.14" and "1,000" are one word each, and
"well-known" is two. `_` joins letters and digits, so `hello_world` is one
word, but `_` alone is not a word. Each Han ideograph and each Hiragana
character is a word of its own, while a run of Katakana stays together. Emoji
and punctuation are not words. Thai, Lao, Khmer, and Myanmar need a dictionary
to find their words, which the default rules do not have, so each of their
letters, with its marks, is a word of its own.

A Kotlin word's `start` and `end` index its String (UTF-16 chars), as the kit's
`Diagnostic` takes them; the kit turns them into a `diagnose` reply's UTF-8
byte offsets. Rust's and C's words are byte offsets already. Hammer's own word
spans are UTF-16 chars.

`scripts/generate_word_break_table.py` writes Hammer's table,
`WordBreakProperties.kt`, from Unicode's `WordBreakProperty.txt`,
`emoji-data.txt` (for Extended_Pictographic, which WB3c needs), and
`UnicodeData.txt` (for the letter categories). A kit needs the same three facts
about each code point: its Word_Break class, whether it is Extended_Pictographic,
and whether it is a letter. It can generate its own table from the same files.

A kit's tests must pass two files. `base/src/commonTest/resources/WordBreakTest.txt`
is Unicode's conformance file, as published: each line is a case, its code
points in hex, with `÷` where there is a boundary and `×` where there is none.
`base/src/commonTest/resources/words.txt` is the shared case file, and its copy
in `hammer-plugin-sdk`, `tests/words.txt`, must stay identical. Each line is a
case: the text, a tab, and the words it holds separated by spaces, with
`\u{...}` for a code point. Lines starting with `#` are comments, and blank
lines are skipped.

### Package

A `.hammerplugin` file is a zip holding `manifest.toml`, `plugin.wasm`, and,
if the plugin has them, a [`settings.toml`](#declared-settings),
translations in `locales/`, and [resources](#resources) in `resources/`.

```toml
id = "wordfreq"               # lowercase, directory-safe
name = "Word Frequency"
version = "1.0.0"
api = 1                       # host API version the plugin was built against
description = "Counts how often each word appears, as a spreadsheet."  # optional, up to 1,000 characters; links are clickable
languages = ["en"]            # optional: the project languages it works with

[permissions]
operations = ["project.info", "scene.tree", "scene.read"]
http = ["api.example.com"]    # optional: hosts it may reach, over https only; see HTTP

[permissions.userHosts]       # optional: an address the user enters; see HTTP
setting = "server"            # a string or secret setting, with no default
toggle = "allowServer"        # optional: a danger bool, off by default, that must be on too

[[exporters]]
format = "wordfreq.csv"       # prefixed with the id
extension = "csv"
mime = "text/csv"
label = "Word frequency (CSV)"

[[actions]]                   # optional; see Actions
name = "report"
label = "Word frequency report"
places = ["project", "scene"] # where its menu item is; project by default
keep = false                  # whether a run closed part way is kept

[[actions.field]]             # optional; the action's first page
key = "scenes"
type = "scenes"               # bool, int, choice, text, scenes, or entity
label = "Scenes"
multiple = true

[[commands]]                  # optional; see Commands
name = "wordfreq"
help = "Counts words in stdin."

[limits]                      # optional
memory = 64                   # MiB of linear memory, 1 to 1024; 64 by default
```

The install dialog, and the Info button beside each installed plugin, show its
description, version, package size, `languages`, the languages its
`locales/` translate it into, what it may use, and what it adds. `languages`
is only shown: a project in another language still gets the plugin's checks
and actions, which decide for themselves what to do with it. A description's links, bare
`https://` URLs or `[label](https://...)`, can be clicked; nothing else in it
is formatted.

A module may be as large as its memory limit plus 32 MiB, since a
pre-initialized module carries its memory's data. The install dialog says when
a plugin asks for more memory than the default.

Each `permissions.operations` entry is an operation's name, or a scope with
`read` or `write`, such as `content:read`, which covers every operation of
that scope and access. A scope never covers a Prose or Destructive operation;
a plugin that needs one names it. No entry may grant a change to the sync
account: `account:write`, `account.login`, `account.logout`, and `sync.run` are
refused at install (see [Account and sync](#account-and-sync)).

The install dialog lists what a plugin may use by level: Read, Change, "Change
the text of your scenes" for named Prose operations, and "Delete, which cannot
be undone". A scope grant is described in words; `content:write` reads
"anything in your projects but the text of your scenes, and never delete", and
`account:read` "which sync server and account you use, and each project's sync
status". A named operation this version of Hammer does not have is listed with the
changes.

There is no platforms field: a WASM module runs anywhere the host does.
Labels are plain strings in the manifest; see [Strings](#strings) for
translating them.

### Calling convention

Hammer uses [Extism](https://extism.org)'s calling convention, so plugin
authors can start from Extism's plugin libraries for C, Rust, Go, Zig, JavaScript,
AssemblyScript, .NET, and Haskell. The host implements the `extism:host/env`
functions natively (`ExtismKernel`) rather than running Extism's kernel module,
which would cost a second interpreted call for every eight bytes a plugin
copies.

Everything crosses the boundary as JSON in the plugin's linear memory, so the
host needs no knowledge of plugin types:

- **Plugin to host:** `hammer_dispatch`, which runs an operation as the CLI's
  [`Dispatcher`](#clientplugin-operations) does, within the plugin's grants,
  and host functions for logging, progress, the cache, resources, and HTTP.
- **Host to plugin:** an export per capability the manifest declares:
  `export`, `action`, `diagnose`, and `command`.

The Hammer-specific parts:

- `extism:host/user` `hammer_dispatch(request) -> reply`. The request is
  `{"operation": "...", "input": {...}}`; the reply is `{"output": ...}` or
  `{"error": {"kind": "...", "message": "..."}}`, with the kinds of
  `OperationException`, `PermissionDenied`, and `Failed` for anything else,
  such as Hammer being busy. A reply never has another key, so a kit may
  take a large output's text as it is, without parsing it. The host answers
  `ops.list` itself, listing only the operations the plugin may call, so it
  works even while Hammer is busy.
- Every export's input also has `locale`, the UI's BCP 47 tag or null; see
  [Strings](#strings).
- An `export` function renders every export format the manifest declares. Its
  input is `{"format", "projectName", "language", "topLevelAsChapters",
  "chapters": [{"name", "scenes": [markdown], "prose": []}], "settings": {...}}`,
  the last holding every declared setting's current value, and its output is
  the file's bytes. An exporter whose manifest entry sets `input = "prose"`
  gets each scene in `prose` instead of `scenes`: the host's own parse of its
  markdown, the one the built-in formats render from, as blocks (`paragraph`,
  `blank`, `heading`, `list`, `quote`, `code`, `rule`, `table`) of styled
  spans. A plugin then needs no markdown parser, and reads text exactly as the
  built-in formats do. The C kit's `hammer_json.h` reads either form.
- A function fails by returning non-zero or by trapping. Either way, the
  message it set with `error_set` is what the user sees, if it set one:
  AssemblyScript's `abort` sets one and then traps.
- `extism:host/env` `http_request(request, body) -> response` and
  `http_status_code()`, Extism's HTTP imports, send a request to a host the
  plugin may reach (see [HTTP](#http)) and return its body and status;
  `http_headers` returns none. A refused request, or one with no response,
  traps. The only
  WASI imports provided are `random_get` and `clock_time_get`, which Kotlin/Wasm
  and kotlinx.serialization need and which grant no access to anything; a
  module importing any other WASI function does not load.
- `extism:host/user` `hammer_cache_get(key) -> value` and
  `hammer_cache_set(key, value)`: the plugin's [cache](#cache). A get returns
  0 for a key with no value; a set with 0, or an empty value, removes the key.
- `extism:host/user` `hammer_resource(name) -> bytes`: a file from the
  package's [resources](#resources), by its path under `resources/`, or 0
  when there is none by that name.
- An `action` export runs an action the manifest declares; see
  [Actions](#actions). Its input is `{"action", "project", "settings",
  "locale", "context": {"place", "id"}, "input", "button", "changed", "state",
  "resumed"}`, and its output a JSON reply: a page to show, a message, errors,
  or an item to open.
- `extism:host/user` `hammer_progress(report)`: how far an action has got,
  `{"fraction": 0.5, "message": "Scene 3 of 6"}`, either key optional. The
  host shows it while the action runs. Once the user has stopped the run, the
  report traps, ending the action there, though stopping interrupts the module
  anyway. Outside an action it does nothing.
- A `diagnose` export runs a text diagnostics check the manifest declares
  under `[[diagnostics]]` (`name`, `label`, and `scope`: `paragraph`, the
  default, for only the paragraphs whose text the check has not seen, or
  `scene`, for every paragraph of the scene each time, blank ones included).
  Its input is `{"diagnostics", "paragraphs": [text], "language", "project",
  "settings"}`, `language` a BCP 47 tag or null and `project` the project's
  name, and its output `{"diagnostics": [{"paragraph", "start", "end",
  "message", "fixes": [...], "severity"}]}`, `start` and `end` UTF-8 byte
  offsets into the paragraph, which is how C and Rust index text. A fix is its
  replacement, shown to the user as itself, or `{"replacement", "label"}`
  where the replacement alone would not say what it does, such as removing a
  word. `severity` is `error`, the default, or `suggestion`, marked more
  quietly. The host converts the offsets,
  and drops an issue whose paragraph does not exist or whose offsets fall past
  the end or inside a character. A module that fails, or replies with nothing,
  leaves the text unmarked.
- `_initialize`, or else `__wasm_call_ctors`, runs once after instantiation, as
  in other Extism hosts.

### Cache

Each plugin has a key-value cache of bytes, for work worth keeping between
runs, such as the style report's per-scene counts. It is one store per plugin,
not per project: a plugin that wants per-project entries puts the project in
the key, and one keyed by content, as the style report is, shares entries
across projects for free.

It lives in the platform cache directory (`<cache>/plugins/<id>/`), one file
per key named for the key's SHA-256, so it is never synced or backed up and the
OS may clear it. Keys are at most 1 KiB and values at most 1 MiB; a larger one
traps the call. Past 32 MiB for a plugin, the host drops the oldest written
entries down to three quarters of that. Installing a plugin again or
uninstalling it clears its cache, since a new version may keep different
values under the same keys. A call still running on the old version can write
after the clear, so plugins put a format version in their keys. Everything about it is best effort: a failed read
is a miss and a failed write is dropped. A CLI command and the app may use one
plugin's cache at once; each write is atomic, and the last one wins.

### Resources

A package's `resources/` holds files the plugin reads at run time, such as
dictionaries and word lists, so they need not be compiled into the module. At
most 1,000 files and 64 MiB in all, checked whenever the package is read. A
plugin reads one with `hammer_resource`, which reads it out of the package then
and hands the plugin all its bytes, so a file the plugin reads has to fit its
memory, and one it uses often is worth keeping rather than reading again: a C
plugin keeps its with `hammer_alloc_keep`. Each read also takes the file's size
from what a call has for passing data to and from Hammer (256 MiB, or a
sixteenth of the JVM's heap if that is less), until the call ends. Only files
under `resources/` can be read, and an empty one reads as missing.

### Actions

A manifest's `[[actions]]` add items to the overflow menus of the screens in
their `places`. An action shows **pages** in a dialog, one at a time. A module
cannot supply Compose UI, so a page says what to ask and what to show, and the
host decides how it looks. Pages leave out, on purpose, rows, columns, and
nesting; colors and styling; a call per keystroke; and fields that appear
without a call.

```toml
[[actions]]
name = "interview"
label = "Interview a character"
places = ["project"]
keep = true

[[actions.field]]
key = "name"
type = "text"
label = "Name"
required = true
```

| Key | Meaning |
| --- | --- |
| `name` | Unique in the plugin, and lowercase and directory-safe as a plugin id is; the call's `action` |
| `label` | The menu item, and the dialog's title on a page without one |
| `places` | The screens whose menus it is in: `project` (Project Home, the default), `scene` (the scene editor), `note`, `entry`, and `event` (a timeline event) |
| `keep` | Whether a run outlives its dialog being closed part way; false by default. See [Sessions](#sessions) |

The `[[actions.field]]` tables are the action's first page, which the host
shows with a Run button, so an action that asks once and answers needs no code
for its form. An action without any is called as soon as it is chosen. For an
action without `keep`, the first page starts with the values it was last run
with, for as long as the project is open, where its fields still take them. A
manifest whose action sets `output` is refused: every action replies with
pages.

**The call.** Choosing the action, Run, and each button pressed call the
module's `action` export. Every key is always present:

```json
{"action": "interview", "project": "Storm", "settings": {}, "locale": "en",
 "context": {"place": "project", "id": null},
 "input": {"q1": "Mara, or Captain.", "q2": "", "q3": ""},
 "button": "next", "changed": null,
 "state": {"name": "Mara", "set": "quick", "step": 1}, "resumed": false}
```

| Key | Holds |
| --- | --- |
| `action` | The action's `name` |
| `project` | The project's name |
| `settings` | Every declared setting's value |
| `locale` | The UI's BCP 47 tag, or null; see [Strings](#strings) |
| `context` | The screen it was run from as `place`, and the id of the item shown there as `id`: null on Project Home |
| `input` | The values of the page the user was on, a key for each of its fields; `{}` for a page without fields, or when the action has none |
| `button` | The id of the button pressed; null on the first call, when the action opened without fields or Run was pressed on its first page |
| `changed` | Always null; reserved for fields that call the action when they change |
| `state` | The `state` of the page the user was on, handed back untouched; null on the first call |
| `resumed` | True on the first call from a page restored from a [session](#sessions) |

The host never reads `state`. A plugin keeps everything a run needs in it,
rather than in its module, which may be loaded afresh between calls; a kept run
may pick up again days later.

**The reply.** A JSON object, every key optional; empty output is the same as
`{}`.

```json
{"page": {"title": "Mara: Review", "markdown": "## Who they are\n\n...",
          "buttons": [{"id": "back", "label": "Back", "validate": false},
                      {"id": "create", "label": "Create entry", "primary": true}],
          "state": {"name": "Mara", "set": "quick", "step": 5}}}
```

| Key | What the host does |
| --- | --- |
| `page` | Shows it next, in place of the page the user was on. Every part is optional: `title`, `markdown` (shown first), `fields` (in order, under it), `buttons`, and `state` (any JSON). Its fields start at their defaults |
| `message` | Shows it briefly, whatever else the reply does. One over 200 characters is cut |
| `errors` | `{key: message}`. Keeps the user on the page they were on, with what they entered and the page's own `state`, and shows each message under the field with that key. `""`, or a key no field on the page has, is a message for the whole page, shown above its fields |
| `open` | `{place, id}`. Ends the run, closes the dialog, and shows an item: `place` is one of the action places, `id` the scene, note, entry, or event. `project` shows Project Home and needs no `id` |

`errors` wins over `open`, and `open` over `page`. A reply with none of the
three ends the run and closes the dialog, without a word unless it has a
`message`. Beyond that:

- `errors` in reply to a call made with no page showing, the first call of an
  action without fields, have no page to stay on: the run ends, and the
  messages are shown as a message.
- An `open` whose item is not in the project, or that has no `id` for a place
  other than `project`, ends the run all the same, and says so.
- Every call follows something the user did, choosing the action or pressing a
  button, so a plugin cannot move the user with `open` without their asking.
- A blank `title`, `markdown`, or `message` counts as none.
- A reply fails the call, as a trap does, when it is not JSON of this shape
  (a button without an `id`, say), has a `state` over 1 MiB encoded, has an
  `open` with an unknown place, or has a field the host cannot show: an
  unknown `type` or `format`, a key used twice on the page, a `choice` without
  options, an `entity` with an unknown kind or entry type, or a `default` the
  field would not take. Unknown keys are ignored.

#### Fields

Fields take the same keys in a manifest's `[[actions.field]]` and in a page's
`fields`: `key`, `type`, `label`, and optionally `hint`, `default`, and
`required`.

| `type` | Shown as | Value in `input` | Extra keys | Without a `default` |
| --- | --- | --- | --- | --- |
| `bool` | A toggle | `true` or `false` | | false |
| `int` | A number field | A whole number | `min`, `max` | `min`, or 0 |
| `choice` | A dropdown | The chosen option's `value` | `options`: `[{value, label}]`, at least one | The first option |
| `text` | The editor its `format` picks | A string, markdown for the rich formats | `format`, `multiline` | `""` |
| `scenes` | A button opening the scene tree to pick from, the pick shown in brief beside it | A list of scene ids, or with `multiple = false`, one id or null | `multiple`, true by default | Nothing picked |
| `entity` | The pick as chips, and a button opening a search by name; see Entities below | `{"kind", "id"}`, or null; with `multiple = true`, a list of them | `kinds`, `entry_types`, `tags`, `multiple`, false by default | Nothing picked |

| `format` | Editor |
| --- | --- |
| `plain`, the default | An ordinary text field: one line, or several with `multiline = true` |
| `rich` | The scene editor's, with its format bar, growing from 3 lines to 12 before it scrolls |
| `rich-extended` | The same with the extended format bar: highlight, task lists, and tables |

- **`default`** is the value a field has when its page shows. A plugin that
  shows a page again, on Back say, passes what was entered as the defaults.
- **Rich values** are the markdown the editor stores, so a plugin can pass one
  straight to `entry.create` or `note.create`.
- **`required`** applies to `text`, which must not be blank, and `scenes` and
  `entity`, which must not be empty. The other types ignore it.
- **`string`**, a settings type, is refused: an action asks for text with
  `text`.
- **Words.** The labels, hints, and option labels of the manifest's fields are
  translated in `locales/`, as declared words are. A page's are the plugin's
  own, written for the call's `locale`.

**Entities.** An `entity` field picks items of the project by name, as the
two characters the `first-meeting` plugin compares (see
[Example plugins](#example-plugins)):

```toml
[[actions.field]]
key = "who"
type = "entity"
label = "Character"
kinds = ["entry"]          # scene, note, entry, event; all four when left out
entry_types = ["person"]   # person, place, thing, event, idea; every type when left out
tags = ["pov"]             # only items carrying one of these; any when left out
required = true
```

Its value is `{"kind": "entry", "id": 12}`, or null when nothing is picked;
with `multiple = true`, a list of them, empty when nothing is. `default` takes
the same shape. Picking one is the common case, so `multiple` is false by
default, unlike a `scenes` field's. Tags match ignoring case and a leading
`#`. A field is refused with an unknown kind or entry type, an empty `kinds`,
`entry_types` when `kinds` leaves out `entry`, or a blank tag.

- **The picker.** Choose opens a search with a tab for each kind when there
  are several. It matches names and entry aliases anywhere in them, ignoring
  case, as they are typed. The search has the focus, and Enter picks the first
  match; with `multiple`, Enter adds it and clears the search for the next,
  and Done closes. The first 100 matches are listed, then how many more.
- **Names.** Scenes are listed in story order, named as in the tree with their
  groups beside them; archived scenes are not offered. Entries and notes are
  listed by name, a note named by its first line, and events in timeline
  order, each named by its date, or its first line without one.
- **Read afresh.** The items are read each time a page with an entity field
  shows, and again at a press the host checks when something is picked:
  entries from disk, so any created, renamed, or deleted since are taken in,
  and scene tags from the tag index, waiting for its first build when the
  project has only just opened.
- **Picks dropped.** Once the items are read, a pick on the page that names
  an item no longer in the project, or one the field does not take, shows as
  nothing picked rather than as an error: a `default` (the entry an action was
  run from, say, in a field of people when it is a place), a pick the first
  page keeps from the last run, or one restored from a session. One gone by
  the press fails the [host checks](#buttons). If the items cannot be read,
  picks go to the plugin unchecked, so a plugin should still answer an item it
  cannot read with `errors`.

#### Buttons

A page's `buttons` are `{id, label, primary, inline, validate}`:

| Key | Default | Meaning |
| --- | --- | --- |
| `id` | | Sent as `button` when it is pressed |
| `label` | | |
| `primary` | false | Emphasised, and last in the footer |
| `inline` | false | Under the markdown, in a row that wraps, rather than in the footer: for a button that acts on an item in the markdown, such as a name to add |
| `validate` | true | False skips the host's checks, for Back or Cancel |

The footer holds, in order: Start over on a restored page, the plugin's other
footer buttons, Copy and Save as note, its primary buttons, and Run on the
first page.

**Host checks.** Before a call from Run or a button with `validate`, the host
checks the page: a `required` text not blank, `required` scenes or entities not
empty, an `int` within its `min` and `max`, a `choice` one of its options, and
each entity picked still in the project, as it is at the press, and of a kind,
entry type, and tag its field takes. What fails is shown under its field, and
the module is not called. The button is not disabled for these, since a
disabled button does not say why. Changing a field clears its message. Any
other rule is the plugin's, answered with `errors`.

**Documents.** A page with markdown and no fields offers Copy. A page with no
buttons either is a **document**, and offers Save as note too. Hammer runs
both, so saving needs no permission from the plugin. A document longer than a
note allows is saved cut at a line break, ending in "…", and the toast says
so. Saving ends the run and closes the dialog, which is why a page with
buttons, whose run has further to go, does not offer it.

#### Running

- **Progress.** While a call runs, its page stays and can still be typed in,
  its buttons disabled, and what is typed then is kept if the reply is
  `errors`. A call that takes more than 400 ms swaps the footer for a bar, a
  line the plugin sets through `hammer_progress`, and a Stop button; without
  reports the bar is indeterminate. A call made with no page showing gets a
  dialog of its own for these. The dialog cannot be closed while a call runs.
- **Stop** interrupts the module at once (see [Host](#host)).
- **Failures.** A call that traps, replies with something the host refuses, or
  is stopped shows a short message; the log has the rest. A kept run goes
  back to the page it was called from, as the user left it. Any other run
  ends.
- **Closing** the dialog ends a run that is not kept.
- **One at a time.** Each project window runs one action at a time; choosing
  another while a call runs says so.

#### Sessions

An **action session** is a kept run of an action: the page showing, with its
`state`, and what has been entered. Only an action whose manifest sets
`keep = true` has them, since most actions, a report or a list of names, cost
nothing to run again. It is set on the action, not on a page, so a page with
no fields, such as the interview's review, is kept with the rest: its `state`
holds every answer.

What the user enters in a kept run is their work, and closing the dialog does
not lose it. Nothing asks "discard?", because nothing is discarded.

- **Where.** One file per action, project, and place the action was run from:
  `<config>/plugins/_sessions/<plugin id>/<project id>/<action>.<place>[.<item id>].json`,
  the item id there for an action run from a scene, note, entry, or event. It
  is in the config directory with the plugin's settings, not in the cache, so
  the OS never clears it and reinstalling the plugin does not either. A plugin
  id cannot start with `_`, so no plugin's files collide with `_sessions`. The
  module cannot read or write a session except through the `state` it is
  handed.
- **What.** The plugin's version; the page, with its `state`; the values on
  it; and the answers: the label and value of each field submitted in the run,
  a choice by its option's label and scenes and entities by name, which the
  host keeps for itself and never sends the plugin. At most 4 MiB.
- **When.** A session is saved when the plugin replies with a page or
  `errors`, when a button is pressed, half a second after the user stops
  typing, when a failed or stopped call goes back to its page, when the dialog
  closes, and when the project does, before the window goes. Each write is
  staged, then moved into place atomically. A write that fails is logged, and
  the user told, once a run.
- **Resuming.** Choosing the action again from the same place shows the page
  where it was left, with its values, and a Start over button, which asks
  before it clears the session and starts the action afresh. The next call
  from that page carries `resumed: true`.
- **Ending.** A reply that ends the run removes its session, as does `open`,
  since the work is in the project. So does Save as note on a document, and
  closing a page with no fields and no buttons, which has nothing left to do.
  Closing the first page as it opened, with nothing entered, leaves no session
  to resume. `errors`, a failed call, and Stop keep it.
- **A changed plugin.** If the plugin's version is not the session's, its
  `state` may mean nothing to the module, so the host does not hand it back.
  It shows the answers instead, with what was on the page, to copy or save as
  a note, and Start afresh, which asks before clearing them. Closing leaves
  the session for next time. A session whose page the host can no longer show
  is treated the same.
- **Unreadable files.** A session file that cannot be read, or is in a format
  this Hammer does not know, is moved aside to `<name>.json.unreadable` (then
  `-2`, `-3`, and so on), so nothing writes over it. The user is told, and the
  action starts afresh. If it cannot be moved, the action does not start.
- **A new project id.** Sessions are keyed by the project's id, so a renamed
  or moved project keeps them. When the id changes (adopting a server
  project's on sync, a clashing one minted anew, or a backup restored), its
  sessions are copied to the new id. They are copied rather than moved, since
  a project copied on disk is given a new id while the original keeps the old.
  A session the new id already has is left as it is.
- **A renumbered item.** Sync gives an item made on this device a new id when
  the server has given its id to another ([Re-ID](SYNCING-PROTOCOL.md#terminology)), so
  the old id then names that other item. Once a sync has made its changes,
  `EntityIdChangeListener`s are told of them all, and sessions follow the new
  ids in one pass: a run from a renumbered item is filed under its new id, and
  picks of one in `scenes` and `entity` fields take it. An open project's
  window holds its own saves during the pass, then follows the new ids on the
  page showing and in a first page's remembered values; a save it asked before
  that is given them as it is written. A session already filed under a new id,
  from a deleted item whose id was handed out again, is moved aside to
  `<name>.json.superseded`. The host cannot rewrite `state`, so a run whose
  page's `state` may hold an old id is not carried on: a number equal to it,
  the item picked as `{"kind", "id"}`, or a string without spaces ending in it
  after a separator, such as `open:12`. Keys and strings with spaces are not
  read, so a question's number or an answer's is not taken for an id. Such a
  page showing is shown as entered, as for a changed plugin, with Start
  afresh; one a call was made from is not gone back to, so `errors`, a failure,
  or Stop end the run. Its kept session is marked so either way.
- **Uninstalling** a plugin leaves its sessions, so installing it again brings
  them back.
- **Not synced.** A half-filled form is this device's.

### Commands

A manifest's `[[commands]]` add top-level CLI commands, desktop only. For
`hammer <name> [args]`, the host reads standard input a line at a time and
calls the module's `command` export with `{"command", "args", "settings",
"line"}`, `settings` read afresh from the plugin's settings file each time. A
non-empty output is written to standard output as one line; the loop ends at
the end of input. The module never touches stdio itself, and its dispatches go
through the CLI's `Dispatcher`, forwarded to the running app or run headless.
Only a plugin the user installed and enabled runs, and only when its command is
invoked. A command may not shadow `help` or an operation's first word, and a
plugin adding a command another already has does not load. If the module
cannot load, the command exits with an error rather than reading input.

### Host

`:plugins:wasmhost` depends on `:operations` and chasm:

- **Loader.** `RuntimePlugins` keeps packages in `<config>/plugins/packages/`,
  each plugin's enabled flag and granted operations in
  `<config>/plugins/_runtime-plugins.toml`, kept action runs under
  `<config>/plugins/_sessions/` (see [Sessions](#sessions)), and caches under
  `<cache>/plugins/` (plugin ids cannot start with `_`, so no plugin's settings
  file collides with them). At startup `activate` wraps each enabled package
  in a `WasmPlugin` and adds it to the registry. A package that fails to read
  in any way, or clashes with another plugin, is logged and skipped; it never
  stops the app starting. A state file that cannot be parsed is moved aside to
  `_runtime-plugins.toml.unreadable` and read as empty. Each TOML text, in a
  package or the state file, is refused if it nests more than 64 levels deep,
  since the parser would overflow the stack on it. In the app, every plugin
  load writes that plugin's id to `_runtime-plugins.loading` and removes it
  once loaded, so a process that dies loading one (an `Error` such as running
  out of memory) leaves its id behind. The next launch turns that plugin off,
  loads the rest, and shows in Settings why it is off; turning it on again
  tries it again. CLI calls skip this guard, since one may load while the app
  starts. On desktop, `--no-plugins` starts the app without activating runtime
  plugins, to uninstall or disable the one at fault; it has no effect on a
  launch handed to an open window.
- **Install.** Reads the package, checks the manifest, the settings
  declarations, and export format prefixes, and instantiates the module once,
  which checks its imports and runs its initializer. Only then is it copied in.
  The initializer runs without network here, before the user has agreed to
  anything, so an `http_request` from it traps and the package is refused.
  Installing a package with an installed id replaces it. Install, enable,
  disable, and uninstall apply at once, and one the registry would refuse,
  such as a command clash, is refused before anything changes.
- **Hot plugins.** Everything a plugin adds is looked up live (see
  [The registry and the UI](#the-registry-and-the-ui)), so changes need no
  restart. An export already running on a removed plugin finishes on its old
  instance.
- **`WasmPlugin`** implements `ClientPlugin`. Its exporters, commands, and
  actions come from the manifest and call the module.
- **Execution.** `WasmPlugin.call` runs the module on the IO dispatcher, never
  the UI thread. Export rendering, already on a background dispatcher, calls it
  blocking. An operation the module dispatches blocks its thread until done.
- **Stopping a runaway module.** Each module runs in a chasm store that meters
  fuel: a check at every function entry and loop iteration takes a unit, and
  the call traps when none are left. The host sets the budget before each call;
  instantiation and the module's initializer get one of their own. chasm does
  not bound memory, so the host rewrites the binary before loading it
  (`ModuleLimiter`) to cap linear memory (the manifest's `limits.memory`,
  64 MiB by default) and tables (100,000 entries), and rejects shared or 64-bit
  memories. A module cannot call itself again from inside a call. The store is
  interruptible too: cancelling the coroutine waiting on a call, such as a
  stopped action or a text check the editor no longer wants, interrupts the
  module at its next function entry or loop iteration, or once the host
  function it is in returns. chasm makes one check for both, so interrupting
  adds little to metering, which costs about 20% in tight loops.
- **Host memory.** What a plugin can make the host hold is capped per plugin:
  - The kernel (Extism's shared memory, holding a call's input, output, and host
    function results) at 256 MiB, or a sixteenth of the JVM's heap if that is
    less: 16 MiB on a 256 MB Android heap. Growing it copies it, briefly holding
    half as much again, so two plugins growing at once take under a fifth of the
    heap. An allocation past it traps. A call's kernel is released when it ends.
  - Variables (`var_set`) at 1 MiB in all, Extism's own default, and 1,024 of
    them; a `var_set` past either traps.
  - Logging at 4 KiB a line, cut short past that, and 64 KiB a call, past which
    the rest of the call's log is dropped with a warning.
  - The guest heap, as under [Languages](#languages).

  Running out of memory anyway, in a call, at load, or in a host function, fails
  that call or load as a plugin failure ("ran out of memory"), never the app. The
  instance is dropped, freeing what it held, and the next call loads the module
  afresh. Only the plugin path catches it; anywhere else it still ends the app.
- **Loading a large module.** Each load reads the module from the package.
  `ModuleLimiter` writes its rewrite into one array sized exactly, copying the
  sections it leaves alone straight from the original, which is let go once
  that is done; chasm's decoded module and the module's linear memory remain.
  So a pre-initialized module needs a JVM heap several times its size while it
  loads: the 104 MB `harper-grammar` module loads in a 352 MB heap but not in
  320, and holds about 90 MB once loaded. Short of that, the load fails as out
  of memory, and a text check it provides leaves the text unmarked. It is
  untried on Android's app heap.
- **iOS.** Kotlin/Native has no heap limit to read, so the kernel's cap is a fixed
  256 MiB there, and chasm cannot be given a capped guest heap on native, so a
  Kotlin/Wasm plugin's is uncapped. Most failed allocations abort the process
  rather than throw, so running out of memory there is contained only in the
  few cases Kotlin/Native throws `OutOfMemoryError`. The variable and log caps
  apply on every platform.

Plugins stay headless. A module cannot supply Compose UI, so its settings are
[declared](#declared-settings) in `settings.toml` and the host renders the form.

### HTTP

A plugin reaches the network only through `http_request`, and only hosts it
names:

- **Declared hosts.** `permissions.http` lists host names, matched exactly:
  `api.example.com` covers neither `example.com` nor `eu.api.example.com`, and
  there are no wildcards. Any path, port, and method on a declared host is
  allowed, over `https` only.
- **An entered host.** `permissions.userHosts` names a `string` or `secret`
  setting holding a URL, such as a self-hosted server's. Its host is reachable
  over `http` too, since a server on the user's own network often has no
  certificate. With a `toggle`, a `danger` bool off by default, only while that
  is on. Both are read on every request, so a change applies at once. The
  setting may have no default, since the install prompt names only the
  setting: a host known in advance belongs in `permissions.http`, where the
  prompt lists it. Only a value the user chose counts, so one equal to the
  declared default, of the setting or its toggle, opens nothing.
- **Never.** Other schemes, URLs with user info, which could disguise their
  host, and redirects, which are returned to the plugin rather than followed.
- **Limits.** 10 seconds to connect and 30 to read on the JVM; 30 seconds idle
  and 60 in all on iOS. A response over 8 MiB fails. The call blocks the
  module, and an interrupt waits for it to return, so the timeouts bound a
  stopped check.
- **Platforms.** `PluginHttp`'s actual is `HttpURLConnection` on desktop and
  Android, and an ephemeral `NSURLSession` on iOS (untested).
- **Shown at install.** The install prompt and details list the declared hosts,
  and the entered host's setting, as sending what the user writes.

### Secrets

`secret` settings live in `SecretDatasource` (`:common`), keyed
`plugin/<id>/<key>`, never in `plugins/<id>.toml`:

- **Desktop.** One AES-GCM file, `secrets.enc` in the config directory, under a
  key derived from the OS user name and home directory (`MachineBoundCipher`,
  shared with the sync token store). It keeps secrets from disk scraping,
  backups, and copies to other machines, not from malware running as the user.
- **Android.** `EncryptedSharedPreferences` under its own Keystore master key,
  so recovering an unreadable keyset never touches the sync tokens'.
- **iOS.** The Keychain, one item per key.

A secret setting's value found in the settings file, as when a plugin update
turns a setting into a secret, is moved to the store when next read.
Uninstalling removes a plugin's secrets.
Commands read them too: `RuntimePlugins` takes the store.

### Trust

Plugins are untrusted code:

- **No ambient access.** No WASI filesystem, sockets, environment, or
  arguments: only randomness and clocks. Everything goes through operations, which
  carry content and never file paths, and [HTTP](#http) to the hosts it names.
- **Permissions checked on every call.** The host's dispatch refuses any
  operation no grant covers that the manifest requested and the user granted.
  Reads, changes, prose, and deletes are shown separately at install, scope
  grants described in words, along with any commands the plugin adds. Accepting the install
  prompt grants everything the manifest lists; a replaced package's grants are
  those of the new manifest, since the user just approved them.
- **Never the sync account.** No plugin logs in, logs out, or syncs, whatever
  its grants; see [Account and sync](#account-and-sync). An installed package
  that asks to does not load, and Settings lists it as unreadable.
- **Install and removal.** Manage plugins, from Settings or a project's
  settings, has install from file, enable and disable, and uninstall, with the
  permission prompt at install. `hammer plugin install` and the MCP plugin's
  tool install with no prompt, so they need `--confirm` or a `danger` setting.
- **Unsigned packages.** A package is not signed: what protects the user is
  the sandbox and the permissions they approve at install.

### Platforms

The host is common code, so enabling a platform is a registration change: the
platform's entry point calls `RuntimePlugins.inConfigDirectory` and activates
the plugins into its `PluginRegistry`. Desktop (`Main.kt`) and Android
(`HammerApplication`) do. Google Play's rule against downloaded code exempts
code run in an interpreter, as plugins are.

iOS does not load runtime plugins: `HammerAppInit` registers an empty
`PluginRegistry`. Enabling them is the same registration line there; whether
App Review accepts installable plugins is a policy question, not an
architecture one. iOS's HTTP client and secret store are written but untested.
