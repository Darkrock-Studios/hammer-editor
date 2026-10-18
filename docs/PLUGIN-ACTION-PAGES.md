# Plugin action pages, phase 2 (plan)

**Status: phase 1 is built.** Pages and their `state`, `text` formats,
`errors`, `open`, and action sessions are in the host; all three kits (C,
Rust, Kotlin) have them; every action in `hammer-plugins` replies with pages;
and the Character interview plugin uses them. The Actions section of
[CLIENT-PLUGINS.md](CLIENT-PLUGINS.md#actions) describes all of it. This
document plans what is left.

Tracked as [Multi step action plugins](https://trello.com/c/U3EaC0cr/271-multi-step-action-plugins).

## What is left

- An [`entity` field](#entity-picker): pick a character, or any other item,
  by typing.
- [`choice` tabs and `refresh`](#tabs-and-refresh), so a page can follow a
  field as it changes.
- [Sci-fi names as one action](#sci-fi-names-as-one-action), in place of
  three.
- The [First meeting](#first-meeting) plugin.
- What to do when [sync renumbers entities](#sync-renumbers-entities):
  decided later.
- [A durable plugin data store](#a-durable-plugin-data-store), on the roadmap
  rather than in this phase.

## Entity picker

A field of type `entity`. The user starts typing and picks from a list that
narrows as they type, each row showing the item's name and what it is. The
plugin limits what is offered:

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

- **Value.** `{"kind", "id"}`, a list of them with `multiple`, or null.
  `default` takes the same.
- **From the host.** The list comes from the project's own data in the host,
  not through the plugin, so it needs no operation granted and costs no call
  per keystroke. The value is a reference, not the item: the plugin reads it
  with `entry.read` and the like, which do need granting.
- **Host checks.** Beside `required`, the host checks a picked item still
  exists and matches the field's filter, as it checks a `choice`'s options.
- **Answers.** A kept run records an entity by its name, as it records scenes.
- **Shared.** The format bar's link to an encyclopedia entity wants the same
  type-to-search ([Trello](https://trello.com/c/IVuexeKY/266-format-bar-link-to-an-encyclopedia-entity)).
  Build it once, for both.

## Tabs and refresh

`choice` takes `display`: `dropdown` (the default) or `tabs`, a row of
segments across the top of the page. Any field takes `refresh = true`, which
calls the module when its value changes, so the page can follow it. Tabs with
`refresh` are how one action holds several forms.

The call needs no new key: every request has `changed`, null until then, and
every kit reads it. A refresh call has:

- `button` null and `changed` the field's key; `input` the page's values with
  the new one, and `state` the page's.
- No host checks: the user is part way through the page, not done with it.

Its reply is handled as any other, `open` aside (below). A `page` replaces
the one showing and starts at its defaults, so a plugin carries the values it
wants kept into the new page's defaults, as it does for Back. `errors` keep
the page.

To decide when building:

- A `text` or `int` field changes with each keystroke, and a call per
  keystroke is [left out](#left-out-on-purpose). Either `refresh` on those
  waits until the field loses focus, or it is refused there.
- The page can be edited while a call runs, so a change to a `refresh` field
  then either waits for the call or is dropped.
- A plugin cannot move the user with `open` unless they asked, by choosing the
  action or pressing a button. Either a field's change counts as asking, or
  `open` in reply to a refresh is refused.

## Sci-fi names as one action

`scifi-names` has three actions, characters, worlds, and things, because each
needs its own form. With a `tabs` choice of Characters, Worlds, and Things
with `refresh`, it is one: each tab's page has its own fields (a kind, how
many, and long form for characters and things; a style and how many for
worlds), and adds its names as people, places, or things.

The manifest's page holds the tabs and the Characters fields. A refresh
replies with a page of the plugin's own, which has no Run, so it carries its
own button to generate, and the host remembers only the manifest page's
values for the next run. The plugin's tests follow the action's new name.

## First meeting

A plugin that finds where two characters first meet: two `entity` fields
limited to `person` entries. It answers the same character twice with
`errors`, and otherwise reads the scenes in story order and replies with a
page naming the first scene to mention both, by name or alias, and a button
that replies with `open` for it; or a page saying they never meet. It asks
for `entry.read`, `scene.tree`, and `scene.read`.

## Sync renumbers entities

A session holding an entity's id has the problem `hammer://entity/<id>` links
have when sync renumbers an entity. A `scenes` field's value has it already,
and an `entity` field's will. The host can rewrite field values, but not
`state`, which it never reads, nor what an id means to the plugin. A session's
file name holds the id of the item the action was run from, too, so a
renumbered item would lose its session.

Decide later whether a re-id ends the sessions it touches, or the plugin is
told of it.

## A durable plugin data store

Tracked in [Durable plugin data store](https://trello.com/c/OkW01dfW/285-durable-plugin-data-store).
Nothing durable belongs to a plugin (see
[Gaps these expose](CLIENT-PLUGINS.md#gaps-these-expose)). A store a plugin
owns, for data it must not lose:

- Global, or per project, keyed by the project's id as sessions are.
- The same promises as sessions: in the config directory, written atomically,
  a failed write reported rather than dropped, kept through plugin updates,
  and not synced.
- Reached through operations or host imports, in all three kits.

Its first user would be custom questions for the Character interview: a
writer's own sets, in the format of its `resources/sets/` (a `## Heading` per
page, an optional intro paragraph, a `- ` bullet per question). Editing them
comfortably may want a list field, [left out](#left-out-on-purpose) so far.

## Left out on purpose

Rows, columns, and nesting; colors and styling; a call per keystroke; fields
that appear without a call (`refresh` does that with one). Lists the user
edits or reorders can come later as a field type if a plugin needs one.

## Work

1. **hammer-editor.** The `entity` field and its picker, shared with the
   format bar; `display` and `refresh`, with `changed` set. Update the Actions
   section of CLIENT-PLUGINS.md.
2. **hammer-plugin-sdk.** The `entity` field, `display`, and `refresh` in the
   three kits; `ActionDriver` calling on a refresh field's change.
3. **hammer-plugins.** Sci-fi names as one action, and First meeting.
4. **Later.** The data store, with its own plan, then custom interview
   questions.
