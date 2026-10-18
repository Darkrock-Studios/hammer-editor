# Plugin action pages (plan)

**Status: tabled.** Designed, not built. It waits on stable project ids
(Trello: [Project IDs](https://trello.com/c/gmZEDTok/228-project-ids) and
[Identify projects by proj file, not folder name](https://trello.com/c/JfR0MhL9/231-identify-projects-by-proj-file-not-folder-name)),
because an [action session](#sessions) is stored outside the project and has
to find its project again after a rename or a move. Today only a synced
project has an id.

Tracked as [Multi step action plugins](https://trello.com/c/U3EaC0cr/271-multi-step-action-plugins);
the [Character interview plugin](https://trello.com/c/Tu2WtEV5/272-character-interview-plugin)
is its first user.

This replaces the Actions section of [CLIENT-PLUGINS.md](CLIENT-PLUGINS.md#actions),
which describes what is built: fields declared in the manifest and asked once,
then a `message`, `document`, or `interactive` reply of markdown and buttons,
with state carried in button ids. Nothing has shipped, so there is no old form
to keep.

## Why

- The sci-fi names plugin needed three actions to keep each form clean. With a
  selector on one form it would be one.
- A character interview, stepping through questions and writing an
  encyclopedia entry at the end, cannot be built: a plugin can ask only once.
- A plugin cannot close its dialog and take the user to what it made.
- A plugin cannot say an input is wrong, and has no picker for a character or
  any other entity, and no rich text input.

## Pages

A plugin cannot supply Compose UI, and the host does not offer it layout. It
says what to ask and what to show, and the host decides how that looks. A
**page** is:

```json
{
  "title": "Interview: 2 of 6",
  "markdown": "What does **Mara** want more than anything?",
  "fields": [
    {"key": "want", "type": "text", "format": "rich", "label": "Wants"},
    {"key": "fear", "type": "text", "format": "rich", "label": "Fears"}
  ],
  "buttons": [
    {"id": "back", "label": "Back"},
    {"id": "next", "label": "Next", "primary": true}
  ],
  "state": {"step": 2, "answers": {"name": "Mara"}}
}
```

Every part is optional. The host draws the title, the markdown, the fields in
order, and the buttons in the dialog's footer; a button with `"inline": true`
sits under the markdown instead, for one that acts on an item in it.

The manifest's `[[actions.field]]` tables are the first page, so an action
that asks once and answers needs no code for its form. An action with none
calls the module straight away.

## The call

Opening an action, pressing a button, or changing a field marked `refresh`
calls the module's `action` export:

```json
{"action": "interview", "project": "Storm", "settings": {...},
 "context": {"place": "entry", "id": 12},
 "input": {"want": "To go home.", "fear": ""},
 "button": "next", "changed": null,
 "state": {"step": 2, "answers": {"name": "Mara"}},
 "resumed": false}
```

- `input` holds the values of the page the user was on. `button` is the id
  pressed, or null when a `refresh` field changed, when `changed` names it.
- `state` is whatever the last page carried, handed back untouched. The host
  never reads it. It replaces carrying state in button ids, and a plugin must
  not rely on its module instance surviving between calls.
- `resumed` is true on the first call after a [session](#sessions) is restored.

## The reply

```json
{"page": {...}, "message": "Added Mara.", "errors": {"b": "Pick two different characters."},
 "open": {"place": "entry", "id": 12}}
```

- `page` replaces what the dialog shows. A reply with no `page`, `errors`, or
  `open` ends the action and closes the dialog.
- `message` is a brief line, shown either way.
- `errors` keeps the user on the page they were on, with what they typed, and
  shows each message under its field; the key `""` is a message for the whole
  page. See [Validation](#validation).
- `open` ends the action, closes the dialog, and navigates to an item: `place`
  is one of the action places, `id` the scene, note, entry, or event (none for
  `project`). An id that does not exist is a failure, shown as one. It follows
  a button press, so a plugin cannot move the user without their asking.

A page with markdown and no fields is what `document` and `interactive` were:
the host still offers Copy and Save as note for it.

## Fields

All fields take `key`, `label`, `hint`, and `required`.

| Type | Value | Notes |
| --- | --- | --- |
| `bool`, `int`, `choice` | As [declared settings](CLIENT-PLUGINS.md#declared-settings) | Checked as settings are |
| `text` | A string | `format` picks the editor, below; `multiline` for `plain` |
| `entity` | `{"kind", "id"}`, a list of them with `multiple`, or null | A filter-as-you-type picker, below |
| `scenes` | A list of scene ids, or one id or null without `multiple` | The scene tree, for picking by structure |

`choice` takes `display`: `dropdown` (the default) or `tabs`, a row of
segments across the top of the page. Any field takes `refresh = true`, which
calls the module when its value changes so the page can follow it. Tabs with
`refresh` are how one action holds several forms, as the sci-fi names' three
kinds do, in place of three actions.

**Text formats.** `text` replaces `string`, and the plugin picks the editor:

| `format` | Editor | Value |
| --- | --- | --- |
| `plain` | An ordinary text field | The text |
| `rich` | The scene editor's, with its format bar | Markdown |
| `rich-extended` | The same with the extended bar: highlight, task lists, tables | Markdown |

Rich values are the markdown the editor already stores, so a plugin can pass
one straight to `entry.create` or `note.create`.

**Entity picker.** The user starts typing and picks from a list that narrows
as they type, each row showing the item's name and what it is. The plugin
limits what is offered:

```toml
[[actions.field]]
key = "a"
type = "entity"
label = "First character"
kinds = ["entry"]          # scene, note, entry, event; all four when left out
entry_types = ["person"]   # person, place, thing, event, idea
tags = ["pov"]             # entries carrying any of these
required = true
```

The list comes from the project's own data in the host, not through the
plugin, so it needs no operation granted and costs no call per keystroke. The
value is a reference, not the item: the plugin reads it with `entry.read` and
the like, which do need granting. Finding where two characters first meet is
two of these and a button.

## Validation

The host checks what it can before calling the module: `required`, a number's
`min` and `max`, a `choice` being one of its options, an entity still existing
and matching its filter. The button is not disabled for these; pressing it
shows what is missing, since a disabled button does not say why.

Everything else is the plugin's rule, and it answers with `errors`. Two entity
fields holding the same character is one:

```json
{"errors": {"b": "Pick two different characters."}}
```

The page, its values, and its `state` stay as they were; the plugin does not
send them again. A key that is not a field on the page is shown with the page's
own message, so a mistake in a plugin is still seen.

## Sessions

An **action session** is one run of an action, from opening it to its end: the
action, its context, the page showing, its `state`, and the field values. (Not
a scene draft, which is a named snapshot of a scene's text.)

What the user types into a session is their work, and the app does not lose
it. The host saves the session as it changes (after a short pause, as scene
buffers are) and again when the dialog closes or the project does.

- **Closing keeps it.** Closing the dialog, by its button, Escape, or the
  window going away, keeps the session. Nothing asks "discard?", because
  nothing is discarded.
- **Opening the action again** from the same place shows the session where it
  was left, with a Start over button that asks before it clears anything. The
  next call carries `resumed: true`.
- **Ending clears it.** A reply that ends the action, or one with `open`,
  removes the session: the work is in the project now.
- **A failed call keeps it.** A trap, a refused operation, or Stop leaves the
  user on their page with what they typed.
- **A changed plugin.** A session records the plugin's version. If it differs,
  the old `state` may mean nothing to the new module, so the host shows the
  saved answers as text to copy and starts the action afresh.

**Where.** With the plugin's own storage, beside its [cache](CLIENT-PLUGINS.md#cache) but not
in it. The cache is the wrong place as it stands: it sits in the platform
cache directory, the OS may clear it, reinstalling the plugin clears it, and
its writes are best effort. Sessions get a second store with the opposite
promises, `<config>/plugins/<id>/sessions/`, one file per project, action, and
context. The project is named by its id, not its folder, so a renamed or moved
project keeps its sessions:

- Durable: in the config directory with the plugin's settings, written
  atomically, and a failed write is reported, not dropped.
- The host's, not the plugin's: the module cannot read or write it except
  through the `state` it is handed, so a plugin cannot lose a session either.
- Kept through an update of the plugin, and through an uninstall until the
  user has been offered the saved answers as text.
- Not synced: a half-filled form is this device's.

A session is at most 4 MiB; `state` past 1 MiB traps the call, as an oversized
cache value does.

## Running

- **Progress.** A run that takes a moment shows a dialog with a Stop button,
  and a bar and a line the plugin sets through `hammer_progress`; without
  reports the bar is indeterminate. Stop ends the wait and the plugin at once.
- **One at a time.** Each project window runs one action at a time. A failure
  shows a short message; the log has the rest.

## Left out on purpose

Rows, columns, and nesting; colors and styling; a call per keystroke; fields
that appear without a call (`refresh` does that with one). Lists the user
edits or reorders can come later as a field type if a plugin needs one.

## Examples

- **Sci-fi names:** one action. A `tabs` choice of Characters, Worlds, and
  Things with `refresh`; each tab's page has its own kind, count, and names.
- **Character interview:** each Next returns the next page with the answers so
  far in `state`; Back is a button like any other. The last page's button
  calls `entry.create` and replies with `open` for the new entry.
- **First meeting:** two `entity` fields limited to `person` entries. The
  plugin answers the same character twice with `errors`, and otherwise reads
  the scenes in story order and replies with a page naming the scene, and a
  button that opens it.

## Before building

- **Project ids.** Sessions are keyed by project id. Until every project has
  one, there is nowhere safe to keep them outside the project.
- **The entity picker** is the same type-to-search the format bar's entity
  link wants ([Trello](https://trello.com/c/IVuexeKY/266-format-bar-link-to-an-encyclopedia-entity)).
  Build it once.
- **Sync renumbers entities.** A session holding an entity's id, in a field or
  in `state`, has the problem `hammer://entity/<id>` links have. Field values
  the host can rewrite; `state` it cannot read. Decide whether a re-id ends
  affected sessions or the plugin is told.
- **Limits.** 1 MiB of `state` and 4 MiB a session are guesses.

## Work

1. **hammer-editor.** `ActionField` gains `text` formats, `entity`, `display`,
   and `refresh`; `PluginAction` and `WasmPlugin` take the new call and reply;
   `PluginActionRunner` and `PluginActionDialogs` draw pages, errors, and
   `open`; a session datasource and its restore on open. Update the Actions
   section of CLIENT-PLUGINS.md from this document.
2. **hammer-plugin-sdk.** The C, Rust, and Kotlin kits' action request and
   reply types, page and field builders, `errors`, `open`, and `state`; the
   Kotlin kit's fake Hammer for tests; the `hello` example.
3. **hammer-plugins.** Move every action to pages: `style`, `name-checker`,
   and the name generators, with the sci-fi names' three actions becoming one.
   Their button-id state moves to `state`.
4. **New plugins.** The character interview, and first meeting.

