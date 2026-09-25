# Client Plugins

Design note for extending the Hammer client (desktop, Android, iOS) with plugins,
and for exposing the same API as a command line interface and an MCP server.
Status: rollout steps 1 to 9, 11, and Android's half of 12 are built; the rest is a proposal. The server has its own plugin
seam (`server/.../plugin/ServerPlugin.kt`) for the hammer.ink overlay; the
client does not follow it (see below).

A client plugin is a sandboxed WebAssembly module installed from a package,
which anyone can write. Plugins do not add operations: the operation API is
Hammer's own, and a plugin is one more caller of it. Plugins live in the
`hammer-plugins` repository, next to this one.

**Compiled-in plugins, explored and dropped.** The first design also had
compiled-in plugins: Kotlin modules registered in the build, with Koin modules,
operations of their own, project lifecycle hooks, and a Compose UI half. The
plain text exporter, the style report, and the MCP server were built that way
first. Once runtime plugins could do the same jobs, compiled-in ones added
nothing a core feature or a runtime plugin could not, and each of the three
moved to a runtime plugin, checked against the compiled-in version before it
was removed. A feature that needs Kotlin and Koin belongs in core.

## Scope

**Now: headless plugins.** A plugin that adds behavior without adding a feature
surface: export formats, project actions, CLI commands, and a settings form the
host renders. Examples: an exporter, a style report, an MCP server, and later a
grammar checker.

**Now: one API, several front ends.** The operations (list projects, read a
scene, write a scene, link an entry) are defined once and exposed to the CLI
and to plugins. MCP is not core: it is a plugin, built on plugin commands (see
[MCP plugin](#mcp-plugin)). The CLI also runs sync, so any machine that can
run the JVM can be a headless sync client.

**Later: feature plugins.** A plugin that adds a whole new kind of thing, with its
own data type, screen, sync, and search integration (brainstorming cards, say).
Out of scope for this note. The only requirement is that nothing here makes it
impossible. See [Keeping the door open](#keeping-the-door-open).

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

New code goes in a new `:operations` module between `:common` and
`:composeUi`, rather than growing `:common`.

```
:base <- :common <- :operations <- :plugins:wasmhost <- :composeUi <- :android, :desktop
```

| Module | Holds |
| --- | --- |
| `:common` | Same role as today. Gains only pluggable export (below) |
| `:operations` | `Operation`, `OperationRegistry`, the core operations, `ClientPlugin`, `PluginRegistry`, `PluginSettingsDatasource` |
| `:plugins:wasmhost` | The [runtime plugin](#runtime-plugins-wasm) host: chasm, the package loader, `RuntimePlugins`, and `WasmPlugin`. All platforms |
| `:composeUi` | `PluginUiRegistry`, the Plugins section of Settings, and the plugins' project actions in the project menu |
| `:desktop` | The CLI adapter and `Dispatcher`, socket forwarding, the writer lock |

`:wear` depends only on `:common` and gets no plugins.

`:operations` and the host target the same platforms as `:common` (Android,
desktop JVM, iOS), because the host is common code.

**`:common` does not know about plugins.** Its one extension point is export:
`StoryExporterRegistry` always holds the built-in formats, then asks each
`ExporterSource` bound in Koin for more on every lookup. `PluginRegistry` is
one, so plugin formats come and go as plugins do.

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

	suspend fun run(context: OperationContext, input: I): O
}

enum class Access { Read, Write, Destructive }

/** What an operation reaches into. A plugin can be granted every Read or Write operation in a scope at once. */
enum class OperationScope { Content, Account }

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
  CLI. An update replaces the body, so the body is required there too; the
  other fields keep their value when left out. An optional body would leave
  `hammer note update --tags x` waiting on stdin.
- **Read** operations change nothing. **Write** operations change content.
  **Destructive** operations delete or overwrite in a way a draft cannot undo;
  the CLI requires `--confirm`, and a plugin only gets one by naming it.
- Each operation declares its **scope**: `content` (projects and everything in
  them) or `account` (credentials and the sync server). Nothing in the
  operation API knows about agents; a plugin that serves them, like the MCP
  plugin, offers what its grants allow.
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
| `project.rename` | Write | Refused while the project is open, and while the app runs (not forwarded) |
| `project.delete` | Destructive | Refused while the project is open, and while the app runs (not forwarded) |
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
clobbering it. It first saves the text it replaces, including an open editor's
unsaved edits, as a draft named "Before external edit", so a live write is
undoable and stays a Write. Edits the editor typed but had not yet debounced
into its buffer are dropped rather than landing on top. Neither a live write nor
`scene.append` credits the writer's activity. Both refuse archived scenes, and
scenes with unsaved edits left by a session that did not close, since saving
would discard them; opening the project in Hammer restores those first.

`draft.apply` replaces the text the same way, backup draft included. Deleting
or archiving a scene drops its in-memory buffer, so an editor's save-all cannot
recreate it; archiving saves the buffer first.

Input that edits scene text in place is marked `@LiveEdit`: the whole input of
`scene.append` and `draft.apply`, and `scene.write`'s `live` mode. The schema carries it as
`x-hammer-live`. The MCP plugin leaves marked operations out, and marked enum
values out of its tool schemas (refusing them if sent anyway), unless its
"Let AI agents change scenes directly" setting is on. Only top-level fields are
checked.

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

These are the `account` scope, so a plugin granted `content:read` and
`content:write`, such as the MCP plugin, cannot reach them.

### Deliberately excluded

- **Account creation and terms acceptance** stay in the GUI.
- **Server admin** is the server's web UI.
- **Global settings** are not exposed, nor are plugin settings.
- **Reference confirmation** is an editor interaction, not an API.
- **Partial scene edits** (`scene.patch`, find and replace within a scene) are
  likely wanted by agents but wait until full-text writes have settled.

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

	/** Items added to a project's menu. */
	fun projectActions(): List<ProjectAction> = emptyList()
}

class ProjectAction(
	val label: String,
	/** Runs off the main thread on the named project; returns what to tell the user, if anything. */
	val run: suspend (project: String) -> String?,
)

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
  existing `StoryImporterRegistry` (see [Modules](#modules)). It lists the
  built-in formats first, in their menu order, then plugin ones by id.
- **All five built-in formats become `StoryExporter`s**, with ids `epub`,
  `docx`, `rtf`, `pdf`, and `markdown`, so built-in and plugin formats take the
  same path.
- **Export moves to the data layer.** `ExportStoryUseCase` and the renderers
  move from `components/projecthome` to `data/export`, next to the importers,
  where operations and a future core module can reach them.
- **Labels.** Built-in labels stay localized in `:composeUi`. A plugin format
  shows its manifest label, or its file extension in upper case. The Android
  save picker takes the exporter's MIME type, and the desktop one its extension.

Importers could follow the same pattern (`ImportFormat` is the same kind of
closed enum). Not in v1.

### The registry and the UI

`PluginRegistry` (`:operations`) holds the active plugins as a `StateFlow`.
`RuntimePlugins.activate` adds the enabled ones at startup, in the app and in
each CLI process alike, and from then on applies every install, enable,
disable, and uninstall to it at once. A plugin is checked as it is added: a
valid id, prefixed export formats, valid settings, and no CLI command another
plugin has or that shadows an operation. One that fails is refused with nothing
changed. A replaced plugin gets a new settings store for its own declarations.

`PluginUiRegistry` (`:composeUi`) follows the registry:

- **Settings.** The Plugins section of Settings lists installed plugins, each
  with an enable toggle, Uninstall, and, when it has declared settings or CLI
  commands, a Settings button that opens them in a dialog: the declared form,
  then how to run each command.
- **Project actions.** A plugin's actions appear in the project home's
  overflow menu. The home screen's component runs one in the app's scope, off
  the main thread, so a plugin needs no UI state of its own. What it returns
  is toasted, or, for an action declaring `output = "document"`, shown as
  markdown in a result dialog; a failure is toasted.
- **Result dialog.** Shows an action's document, titled with the action's
  label, with Copy and Save as note. Hammer runs both, so saving needs no
  permission from the plugin. A document longer than a note allows is saved cut
  at a line break, ending in "…", and the toast says so.

These are the only UI slots for now. Future ones (a scene editor toolbar
action) are added when a plugin needs them, not speculatively.

## CLI

The CLI is the normal desktop binary with subcommands, not a separate target.
Every package format (Flatpak, Snap, MSIX, the Mac bundle) already ships one
launcher, and a second target would double that work. A first argument that
does not start with `-` is a CLI call (`Cli`); anything else launches the app as
today, parsed by the Clikt command in `DesktopLaunchArgs.kt`. The CLI parses its
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
  starting Hammer.
- Exit codes: 0 success, 1 failure, 3 not logged in or login rejected, 4 not
  found, 5 another Hammer process holds the writer lock, and 64 (sysexits'
  EX_USAGE) for usage errors and invalid input. An operation can report partial
  failure through `Operation.exitCode`: `sync.run` returns 2 when a project needs
  resolution.
- A field marked `@FromStdin` is read from stdin when not given as an option; a
  secret one (the login password) is never accepted as an option, and can also
  come from an environment variable (`HAMMER_PASSWORD`).
- Plugin `cliCommands()` are added alongside the generated subcommands. A plugin
  command cannot shadow a generated one.
- With a subcommand, `main` starts Koin without opening a window, runs the
  command, and exits (`HeadlessSession`). Logs go only to the log file. Tried
  against real data: the data migrator, string resources, and export all work
  without a window, and a call takes about 1.5 seconds including JVM start.
- JVM startup plus Koin init is on the order of a second per call on a desktop,
  several on a Pi Zero. Deferred to v2: `hammer batch`, one process reading
  operations as JSON lines on stdin and writing one JSON result line per
  operation to stdout. It is a few lines on top of `Dispatcher`, the same loop
  the MCP plugin runs with MCP framing.
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
when the script's directory is not on PATH yet.

**Windows needs a console launcher.** jpackage gives the app one launcher,
`hammer.exe`, built as a GUI program, which a terminal does not wait for and
whose output it never shows. The build adds `hammer-cli.exe` beside it: the
same binary with its PE subsystem set to console, as `editbin
/SUBSYSTEM:CONSOLE` would, plus its own copy of `app/hammer.cfg`, since the
launcher finds its config by its own name (`addWindowsConsoleLauncher` in
`buildSrc`). The installers and the MSIX are built from that app image. Store
apps cannot be run from their install folder, so the alias is also what MCP
clients use there. Not yet run on Windows: the console launcher, the alias, and
the PATH change (PowerShell writing `HKCU\Environment` and broadcasting
`WM_SETTINGCHANGE`) need one check there before release. `hammer.cmd` is read
in the OEM code page, so an install path with non-ASCII characters breaks it,
and uninstalling Hammer leaves the script and PATH entry behind.

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
answer, the app could not open it, and the CLI fails saying Hammer is running.

Forwarding is always on; there is no setting for it. Anything that can reach
the socket runs as the same user, who could edit the project files directly, and
the socket's directory is theirs alone. What an agent may do is decided where it
is installed: the MCP plugin's granted operations and its live edits setting.
Refusing while the app is open would only push tools to wait for it to close,
or to edit files under it.

**Multiple app instances.** A second launch of the app finds the writer lock
held by the app, hands its launch arguments to the first over the socket, and
exits, so the app is effectively single-instance. The first comes to the front
and acts on them as its own: a `--project` (with any `--scene`, `--note`,
`--entry`, or `--timeline-event`) opens that project, going straight there if it
is already open, or first closing the open one the usual way, with its
unsaved-changes prompts; declining the prompt drops the request. A launch that
cannot hand off (no answer, or a
CLI call holding the lock) starts its own window without the lock, as before.
Windows jump-list items, which relaunch the executable, go through the same
path, as do the Linux quicklist and macOS Dock menu in-process.

Nucleus's own single-instance mode (`enableSingleInstance`) stays off: its lock
lives in the temp directory under one app id, so a `--dev` window and a normal
one would block each other, and it hands over only a deep-link URI, not launch
flags. The writer lock and socket are already per config directory.

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

MCP is a runtime plugin, in hammer-plugins' `kotlin/mcp`, written in
Kotlin/Wasm. The host provides only generic pieces: plugin commands, scope
grants, and `ops.list` filtered to the grants.

Its manifest requests `content:read` and `content:write`, and declares a
`mcp` command. An MCP client launches `hammer mcp` as a child process and
speaks JSON-RPC over stdio, one message per line; the host hands each line to
the plugin (see [Commands](#commands)). The plugin answers `initialize`,
`ping`, `tools/list`, and `tools/call`, and ignores notifications.

Its tools are what `ops.list` returns it, which is exactly what it may call.
Tool names replace the operation's dots with underscores (`scene_read`), since
some clients reject dots. A failed call, including one refused because Hammer
is busy, is a tool error the agent can read, not a protocol error. Every call
goes through the CLI's `Dispatcher`, so it works against the running app's live
state or, with no app running, directly against the files. No network port and
no token management.

**Live edits.** Its one setting, "Let AI agents change scenes directly", is
off by default. While off, it drops tools whose input is marked
[`@LiveEdit`](#operations) (`x-hammer-live` in the schema), drops live values
from enum fields such as `scene.write`'s `mode`, and refuses them if sent
anyway, so agents' scene edits land as drafts. Settings are read for every
message, so a change applies to a running server.

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

- **Already in place.** `SyncAccountUseCase` in the data layer holds the
  sync-all orchestration. It reports through a `SyncAccountListener`, takes a project filter,
  and returns a per-project `ProjectSyncOutcome`. `sync.run` is a CLI listener
  plus that call; no refactor needed.
- **Conflicts.** `--on-conflict abort|local|server`, default `abort`. Abort is
  what bulk sync already does: the project stops, is reported as needing
  resolution, and other projects continue. `local` and `server` apply to idea
  conflicts only, for now: `SyncAccountUseCase` always stops a conflicted
  project, and resolving one headless needs the project sync to accept a
  choice instead of aborting.
- **Output.** A JSON summary with each project's outcome, and the sync log
  lines, goes to stdout. (Streaming the log to stderr as it happens needs a
  progress channel operations do not have yet.)
- **Exit codes.** 0 all synced, 1 failure, 2 at least one project needs
  resolution, 3 unauthorized (run `account login` again).
- **Login** takes the server, email, and password (from stdin). A server that
  asks for terms acceptance is refused with a message to log in from the app
  once, since accepting terms stays in the GUI. These map from
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
`PluginSettingsDatasource` that replaces the file atomically, so an interrupted
write cannot reset it. (It is a datasource by name because `:common`'s
architecture rule keeps raw TOML I/O in datasource files.) This keeps plugin
schemas out of the core settings migrations.

Plugins have no storage of their own beyond settings; see
[Gaps](#gaps-these-expose).

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

- **Types.** `bool` (toggle), `int` with optional `min` and `max`, `string`
  with optional `multiline`, and `choice` (dropdown). More types are added when
  a plugin needs one. There is no secret type: plugin settings are plain TOML,
  so credentials do not belong in them.
- **Where it lives.** `settings.toml` in the package, parsed into
  `SettingDeclaration`s (`:operations`, no Compose types).
- **Storage.** Values go in the plugin's existing `plugins/<id>.toml`, one key
  per setting. On load, each value is checked against its declaration; a
  missing, mistyped, or out-of-range value falls back to its default, and keys
  no longer declared are dropped on the next write.
- **Reading values.** A plugin receives its current settings as JSON with
  every call, so it never needs a host call to read them. A command reads them
  afresh for each line, so a change applies to a running `hammer mcp`.
- **Rendering.** `:composeUi` turns a declaration into Hd components: a toggle
  row, a number field, a text field, or a dropdown, in the plugin's settings
  dialog. Labels are plain strings in the declaration.
- **Access.** `PluginRegistry.settings(pluginId)` is the plugin's
  `DeclaredSettingsStore`: current values as a `StateFlow<JsonObject>`,
  `decode(serializer)` into the plugin's own class, and `set(key, value)`,
  which ignores undeclared keys and invalid values.

## Strings

A plugin's name, labels, and setting text are plain strings in its manifest and
`settings.toml`, not localized. Localized manifests can come later. Operation
descriptions are plain English strings too: they are read by agents and shown
in CLI help, neither of which is localized today.

## Example plugins

Three plugins, all real features rather than test fixtures, chosen so that
between them they exercise every slot: the plain text exporter, the style
report, and the [MCP plugin](#mcp-plugin).

### Plain text exporter (`plaintext`)

Built, as a runtime plugin in C: `c/plaintext` in the `hammer-plugins`
repository, an 8 KB package. Exports a story as plain text for pasting into
submission forms, which want text with their own conventions for scene breaks
and italics. Settings: scene break marker (`#`, `* * *`, or a blank line),
italics (underscores, asterisks, or removed), paragraphs (blank line between,
or indented), and chapter headings.

| Exercises | How |
| --- | --- |
| Exporter capability | The manifest declares one exporter, format id `plaintext.txt`, with `input = "prose"` |
| Declared settings | Three choices and a toggle in `settings.toml`, kept in `plugins/plaintext.toml` as before, so saved choices carry over |
| Operations for free | `hammer project export --format plaintext.txt`, and export through MCP, with no plugin code |

A fresh install of Hammer has no plain text export until the plugin is
installed.

**Manuscript format moves into core.** Standard Manuscript Format (12 point
Times or Courier, double spacing, a surname, title, and page number header, a
rounded word count, `#` scene breaks) was the first example plugin. It fits the
built-in DOCX better: writers mostly want DOCX to send to agents, editors, and
contests, all of whom expect manuscript format, and EPUB and PDF already cover
a styled book. Converting the built-in DOCX is a separate change, pending where
the page-one contact block comes from.

### Style report (`style`)

Built, as a runtime plugin in C: `c/style` in `hammer-plugins`, a 16 KB
package. A "Style report" item in the project menu reports, per scene and for
the whole story, Flesch reading ease and grade level, adverbs per thousand
words, the share of dialogue, and repeated words and phrases, and shows it in
the result dialog, from which it can be saved as a note. The rules are English
only. Dialogue is text in
double quotes or curly single quotes; straight single quotes are too often
apostrophes to count.

| Exercises | How |
| --- | --- |
| Project actions | The manifest declares one action with document output; the host calls the module's `action` export on the project and shows the markdown it returns |
| Plugin as API consumer | Reads through `scene.tree` and `scene.read`, the two operations it asks for by name, and nothing else: saving is the user's choice, in the dialog |
| A full-book job in C | Counts in the module's own hash maps and arenas, reused scene to scene, so a novel fits the 64 MiB memory cap |
| The plugin cache | Keeps each scene's counts under a hash of its text, so a run counts only scenes that changed |

The whole story's figures are the sum of its scenes' counts, so they need no
second pass. Its repeated phrases are those repeated within a scene. On a
300,000-word book a first run takes about 7
seconds and a run with nothing changed about 1, the reading of every scene
that remains.

### Gaps these expose

- **No content-change events.** A plugin that wants to react to edits (live
  stats, a background linter) has nothing to subscribe to. Deferred until a
  plugin needs it.
- **No plugin-defined buttons.** The result dialog's buttons are Hammer's own
  (Copy, Save as note). A plugin may later want buttons of its own on a result,
  such as "Apply suggestion" or "Open scene". The likely shape: a new
  `output = "interactive"` kind whose output is JSON, `{"markdown", "buttons":
  [{"id", "label"}]}`, a click calling the `action` export again with the
  button's id, and the reply a message or a new document. That needs an output
  schema, a second kind of call, a way to carry state between the calls (the
  plugin rebuilding it, the host echoing the document back, or the plugin
  cache), a rule for whether the dialog updates or closes, and a button doing
  only what the plugin's grants allow. Plain `document` output stays as it is,
  so this is additive. Deferred until a plugin needs one.
- **No per-project settings.** Plugin settings are global. Neither example
  plugin needs per-project ones yet.

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

1. Nothing in `ClientPlugin` or the operation API references any of the types
   above.
2. Plugin ids are stable and match the server plugin id convention, so a future
   feature plugin can be one id with a client half and a `ServerPlugin` half.
3. Any per-plugin storage is explicitly *not* synced. Owning synced entities
   becomes a separate, opt-in capability when the entity model is opened up,
   rather than something plugins fall into by storing data.

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

Plugins are supported in two languages: Kotlin/Wasm, for the most comfortable
way to write one (a kit with typed requests, a `hammer.plugin` Gradle plugin that
does the build, and unit tests on the JVM against a fake Hammer), and Rust, for
speed (about half C's speed on a whole-book report, where Kotlin runs at about
a fifth). Zig, C, AssemblyScript, and Go (through TinyGo) were tried too.
hammer-plugins' `LANGUAGES.md` compares them all. Kotlin/Wasm's objects live in
the host's Java heap, not the module's capped linear memory, so the host needs a
limit on them before Kotlin support ships.

### Package

A `.hammerplugin` file is a zip holding `manifest.toml`, `plugin.wasm`, and,
if the plugin has settings, a [`settings.toml`](#declared-settings).

```toml
id = "wordfreq"               # lowercase, directory-safe
name = "Word Frequency"
version = "1.0.0"
api = 1                       # host API version the plugin was built against

[permissions]
operations = ["project.info", "scene.tree", "scene.read"]

[[exporters]]
format = "wordfreq.csv"       # prefixed with the id
extension = "csv"
mime = "text/csv"
label = "Word frequency (CSV)"

[[actions]]                   # optional; an item in each project's menu
name = "report"
label = "Word frequency report"

[[commands]]                  # optional; see Commands
name = "wordfreq"
help = "Counts words in stdin."
```

Each `permissions.operations` entry is an operation's name, or a scope with
`read` or `write`, such as `content:read`, which covers every operation of
that scope and access. A scope never covers a Destructive operation; a plugin
that needs one names it.

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
  `OperationException`, `PermissionDenied`, and `Failed` for anything else,
  such as Hammer being busy. The host answers `ops.list` itself, listing
  only the operations the plugin may call, so it works even while Hammer is
  busy.
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
- HTTP imports exist, as Extism plugins expect them, and always fail. The only
  WASI imports provided are `random_get` and `clock_time_get`, which Kotlin/Wasm
  and kotlinx.serialization need and which grant no access to anything; a
  module importing any other WASI function does not load.
- `extism:host/user` `hammer_cache_get(key) -> value` and
  `hammer_cache_set(key, value)`: the plugin's [cache](#cache). A get returns
  0 for a key with no value; a set with 0, or an empty value, removes the key.
- An `action` export runs a project action the manifest declares. Its input is
  `{"action", "project", "settings"}`, and its output, if any, is shown to the
  user when it finishes: as a brief message, or, when the action's manifest
  entry sets `output = "document"`, as markdown in the result dialog.
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

A new `:plugins:wasmhost` module, depending on `:operations` and chasm:

- **Loader.** `RuntimePlugins` keeps packages in `<config>/plugins/packages/`,
  each plugin's enabled flag and granted operations in
  `<config>/plugins/_runtime-plugins.toml`, and caches under `<cache>/plugins/` (plugin ids cannot start with `_`,
  so no plugin's settings file collides with it). At startup `activate` wraps
  each enabled package in a `WasmPlugin` and adds it to the registry. A package
  that fails to read, or clashes with another plugin, is logged and skipped; it
  never stops the app starting.
- **Install.** Reads the package, checks the manifest, the settings
  declarations, and export format prefixes, and instantiates the module once,
  which checks its imports and runs its initializer. Only then is it copied in.
  Installing a package with an installed id replaces it. Install, enable,
  disable, and uninstall apply at once, and one the registry would refuse,
  such as a command clash, is refused before anything changes.
- **Hot plugins.** Everything a plugin adds is looked up live (see
  [The registry and the UI](#the-registry-and-the-ui)), so changes need no
  restart. An export already running on a removed plugin finishes on its old
  instance.
- **`WasmPlugin`** implements `ClientPlugin`. Its exporters, commands, and
  project actions come from the manifest and call the module.
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

Plugins stay headless. A module cannot supply Compose UI, so its settings are
[declared](#declared-settings) in `settings.toml` and the host renders the form.

### Trust

Plugins are untrusted code:

- **No ambient access.** No WASI filesystem, sockets, environment, or
  arguments: only randomness and clocks. Everything goes through operations, which
  carry content and never file paths.
- **Permissions checked on every call.** The host's dispatch refuses any
  operation no grant covers that the manifest requested and the user granted.
  Reads and changes are shown separately at install, scope grants described in
  words, along with any commands the plugin adds. Accepting the install
  prompt grants everything the manifest lists; a replaced package's grants are
  those of the new manifest, since the user just approved them.
- **Install and removal in Settings.** The Plugins section has install from
  file, enable and disable, and uninstall, with the permission prompt at
  install.
- **Signing** is of the [plugin index](#plugin-index), not of packages: the
  signed index pins each package's hash. A package installed from a file is
  unsigned.

### Plugin index

A public, curated list of plugins the app can browse and install from. It holds
listings, not packages.

**Submitting.** A plugin gets one file in a `hammer-plugin-index` repository,
added by pull request. `hammer-plugins` stays the first-party plugins' source
and is the index's first submitter, attaching each package to a GitHub release.

```toml
# plugins/style.toml
id = "style"
name = "Style Report"
summary = "Sentence length, adverbs, and repeated words, per scene."
source = "https://github.com/Wavesonics/hammer-plugins/tree/main/c/style"
license = "MIT"
maintainers = ["Wavesonics"]

[[releases]]
version = "1.2.0"
url = "https://github.com/Wavesonics/hammer-plugins/releases/download/style-1.2.0/style.hammerplugin"
sha256 = "9f2c..."
```

Packages stay where their authors host them: a `.wasm` is not reviewable in a
diff, and the pinned hash keeps an author from swapping one after review. A
listing needs a public source link.

**Checks on each pull request.** CI downloads each new release and checks:

- Its hash, and that it passes the install checks the app runs, through a
  `hammer plugin check <file>` command authors can run too.
- Its id is the listing's, its version is above the last, and its `api` is one
  a released app supports.
- For an existing listing, the pull request's author is one of its
  `maintainers`.

**Review.** Plugins have no ambient access (see [Trust](#trust)), so a plugin
cannot send anything anywhere; review asks whether it could damage a project
and whether it does what it says. A person approves new listings, releases that
request more permissions than the last, and any Destructive operation. Other releases merge
once the checks pass.

**Publishing.** On merge, CI builds `index.json` and publishes it, with its
signature, at a fixed URL on a Hammer domain, so hosting can move. Permissions,
commands, actions, and exporters in it are read from each package's manifest,
never from the listing, so a listing cannot misstate what a plugin requests.

**Signing.** CI signs `index.json` with an Ed25519 key held as a repository
secret; the app ships the public keys it accepts, and a rotation ships in an app
release. The app refuses an index whose signature fails, and one generated
before the newest it has seen, so an old index cannot be replayed to hide an
update. The signature covers the index and the index pins every package hash,
so packages need no signatures of their own.

**In the app.** The Plugins section of Settings gets a Browse tab. It fetches
the index only when opened, or when checking installed plugins for updates,
lists the releases whose `api` the app supports, and installs one by
downloading it, checking its hash, and passing it to the usual install and its
permission prompt. An update that requests more asks again, as any
[replaced package](#trust) does. Install from file stays for plugins outside
the index.

**Later.** Verified builds: CI builds a release from source at a pinned commit
and marks the listing when the result matches the package. It needs a toolchain
per language, so it is a mark, not a requirement. A browse page on hammer.ink
can read the same `index.json`.

### Spike results

Desktop JVM, warm, chasm 2.0.0:

| Measure | Result |
| --- | --- |
| Tight loop, 20M iterations | 358 ms bare, 738 ms with fuel checks |
| 557 KB of text (100k words) through a WAT plugin, two host calls per byte | 63 ms |

The same two jobs in C and Kotlin/Wasm, on that text. The Kotlin production
build is optimized by binaryen; the development build is not:

| Job | C | Kotlin production | Kotlin development |
| --- | --- | --- | --- |
| Upper-case it | 35 ms | 680 ms | 1.8 s |
| Word count export of it | 121 ms | 1.95 s | 4.5 s |
| Load the module (after the first, which warms chasm itself) | under 25 ms | 25 to 135 ms | 0.4 to 0.5 s |
| Module size | 1 to 4 KB | 12 to 325 KB | 0.6 to 1.9 MB |

- **Kotlin/Wasm works** on the same host, `wasmWasi` target, with
  `@WasmImport` for the Extism functions and only `random_get` and
  `clock_time_get` from WASI. Optimized, it is 15 to 20 times slower than C
  here: part is doing more (Unicode case rules, kotlinx.serialization), most is
  its GC-heavy standard library under an interpreter. Fine for occasional jobs;
  C, Rust, Zig, or AssemblyScript suit exports that run over a whole book.
  Plugin authors should ship the production build.
- **C works freestanding.** Extism's C PDK compiles with no libc
  (`--target=wasm32-unknown-unknown -nostdlib`), so a C plugin imports nothing
  but Extism and Hammer functions. `hammer.h` supplies the `memset`, `memcpy`,
  and `strlen` clang emits calls to even then.
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
with no new host code. Play's rule against downloaded code exempts code run in
an interpreter, which covers installing from the [plugin index](#plugin-index);
confirm before shipping the Browse tab there. iOS can run it technically; whether App Review accepts
installable plugins is a policy question to settle before shipping it there,
not an architecture one.

## Cost to `:common`

Most new code lives outside `:common`: in `:operations`, `:composeUi`,
`:desktop`, and plugin modules. Everything this note asks of `:common`:

| Change | Size | Justified without plugins? |
| --- | --- | --- |
| `closeProjectScope` counts editors, closing the scope when the last one closes | A few lines | Yes. Two Android tasks on one project otherwise close the scope under each other |
| One string, `settings_plugins_header` | Trivial | No, but it is where all UI strings live |
| `StoryExporterRegistry` asks Koin-bound `ExporterSource`s on each lookup | A few lines | No. The one piece of pure plugin plumbing |
| `ExportFormat` enum becomes `StoryExporterRegistry`; export moves from `components/projecthome` to the data layer | Moderate | Partly. Export logic is in the wrong layer today |
| The prose markdown parser moves out of `PdfProseMarkdown.kt` into a public `ProseMarkdown.kt` | Small | Yes. DOCX and RTF already use it, and it had nothing to do with PDF |
| `StoryChapter` keeps its scenes separate, with `markdown` joining them | Small | Yes. Manuscript format needs scene breaks too |
| A draft-save method that takes text, not only the current scene content | Small | No, but it is a natural addition |
| `ProjectHome.runProjectAction` toasts an action's message or shows its document, which can be saved as a note | Small | No. The project action slot's state lives in the home screen's component |

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

Both were spiked before step 5: `HeadlessRestartTest` starts the real Koin
graph five times, runs migration and read operations each time, and fails on
any work that outlives a stop. It passes, at about 0.2 seconds per warm cycle.

**Guardrail.** Every step's `:common` changes must make sense without plugins,
or be listed in the table above. Anything else gets flagged in review, and the
design is revisited rather than `:common` bent to fit.

## Rollout

1. **Seam.** The `:operations` module, `ClientPlugin`, the registries,
   `PluginSettingsDatasource`, the Plugins section of Settings, and entry-point
   wiring. (Built first for compiled-in plugins, since dropped.)
2. **Pluggable export.** Move export into the data layer, replace the
   `ExportFormat` enum with `StoryExporterRegistry`, port the five built-in
   formats, then ship the [plain text exporter](#plain-text-exporter-plaintext)
   as the first real plugin, now a runtime one.
3. **Operation registry and read operations.** Registry, `OperationContext`,
   the Read operations from the catalog, including `project.export` and
   `export.formats`. Tested directly, no front end yet.
4. **Runtime plugin spike.** Done; see [Spike results](#spike-results).
   `:plugins:wasmhost` holds the Extism host on chasm, fuel instrumentation, the
   manifest, and `WasmPlugin`.
5. **Headless CLI.** Built: subcommands generated from the registry,
   `Dispatcher`, the `cliCommands()` capability, per-call Koin startup, and the
   writer lock, which the app takes at startup and holds for its lifetime. The
   holder writes `writer.owner` beside the lock, so nobody waits on the app,
   while a CLI call waits briefly for another to finish. A second app window
   that cannot take the lock runs without, as before; forwarding (step 8) is
   what makes the app single-instance. [Getting `hammer` onto
   PATH](#getting-onto-path) is a Settings button on each package format that
   allows it.
6. **[MCP plugin](#mcp-plugin).** Built, as a runtime plugin on plugin
   commands and scope grants.
7. **Headless sync.** Built: `account.status`, `account.login`,
   `account.logout`, `sync.status`, and `sync.run` over `SyncAccountUseCase`.
   Refuses while the app is running, through the writer lock. Tested with fakes
   only so far, not against a live server.
8. **Forwarding.** Built: the app listens on
   `run/hammer.sock` in the config directory (`run/` is owner-only) while it
   holds the writer lock, refuses account and sync operations since it runs
   those itself, and the CLI and MCP try the socket before running headless. A second app launch hands its arguments to the first window and
   exits (see [Multiple app instances](#concurrency)).
9. **Write operations.** Built: every write operation in the catalog, the MCP
   plugin's live edits setting, and the CLI's `--confirm` and `--in`. Project
   create, rename, and delete go through `ProjectsService`, shared with the
   projects list, so they queue for account sync the same way. Deliberately
   after forwarding, so live writes always go through the app when it is up.
   Then the [style report](#style-report-style) plugin, now a runtime one with
   the project action slot it needed.
   Then the plugin [cache](#cache), so a style report counts only changed
   scenes, and the result dialog, where the style report now appears.
10. **Text diagnostics.** Define `TextDiagnosticsProvider` (text in, ranges plus
   messages plus fixes out) and add a grammar plugin against it. Migrate spell
   check onto the same interface only if the editor integration gets simpler for
   it; spell check is wired deep into editor decorations and is not a cheap
   first proof.
11. **Runtime plugins on desktop.** Built. Plugins from the `hammer-plugins`
    repository compile, package, install without a restart, read their
    declared settings, and call back into Hammer. Compiled-in plugins are gone.
12. **Runtime plugins on Android**, then an iOS decision. Android is built:
    registration only, through the same `RuntimePlugins.inConfigDirectory`
    as desktop. iOS needs only the same line in `HammerAppInit`, once App
    Review's stance on installable plugins is settled.
13. **[Plugin index](#plugin-index).** `hammer plugin check`, then the
    `hammer-plugin-index` repository with its checks and signed `index.json`,
    with the first-party plugins as its first listings, then the Browse tab and
    update checks.

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
- **Core first.** A Drive backend needs OAuth (a browser sign-in and token
  refresh), HTTP, background scheduling, and a place for tokens, all of which
  core Kotlin has. It would start in core once the capability exists.
- **Plugins need more host.** An HTTP permission limited to hosts the
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
target system. chasm publishes Linux native targets too, so plugins, the MCP
plugin included, can come along.

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
