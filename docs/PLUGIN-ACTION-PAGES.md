# Plugin action pages, phase 2 (plan)

**Status: phase 1 is built, and part of phase 2.** Pages and their `state`,
`text` formats, `errors`, `open`, and action sessions are in the host; all
three kits (C, Rust, Kotlin) have them; every action in `hammer-plugins`
replies with pages; and the Character interview plugin uses them. The Actions
section of [CLIENT-PLUGINS.md](CLIENT-PLUGINS.md#actions) describes all of it.

Of phase 2, the `entity` field, with its picker, and the First meeting plugin
are built: CLIENT-PLUGINS.md describes the field under
[Fields](CLIENT-PLUGINS.md#fields) and the plugin under
[First meeting](CLIENT-PLUGINS.md#first-meeting-first-meeting). This document
plans what is left.

Tracked as [Multi step action plugins](https://trello.com/c/U3EaC0cr/271-multi-step-action-plugins).

## What is left

- [`choice` tabs and `refresh`](#tabs-and-refresh), so a page can follow a
  field as it changes.
- [Sci-fi names as one action](#sci-fi-names-as-one-action), in place of
  three.
- What to do when [sync renumbers entities](#sync-renumbers-entities):
  decided later.
- [The format bar's entity link](#the-format-bars-entity-link), sharing the
  entity picker.
- [A durable plugin data store](#a-durable-plugin-data-store), on the roadmap
  rather than in this phase.

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

## Sync renumbers entities

A session holding an entity's id has the problem the format bar's
[`hammer://entity/<id>` links](#the-format-bars-entity-link) will have when
sync renumbers an entity. A `scenes` field's value has it, and so does an
`entity` field's `{"kind", "id"}`. A kept run's answers do not, since they
record scenes and entities by name. The host can rewrite field values, but not
`state`, which it never reads, nor what an id means to the plugin. A session's
file name holds the id of the item the action was run from, too, so a
renumbered item would lose its session. First meeting keeps no session, so it
is not touched.

Decide later whether a re-id ends the sessions it touches, or the plugin is
told of it.

## The format bar's entity link

Tracked in [Format bar: link to an encyclopedia entity](https://trello.com/c/IVuexeKY/266-format-bar-link-to-an-encyclopedia-entity).
The extended format bar's link to an encyclopedia entity wants the
type-to-search an `entity` field has. `EntityPickerDialog`, in `composeUi`'s
`compose/entitypicker/`, is not tied to plugins: it takes the project's items,
a filter, and what is picked, and reports picks, so the link can open it on
entries and set `hammer://entity/<id>` on the selection for the one picked.
The link gets a plan of its own.

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

1. **hammer-editor.** `display` and `refresh`, with `changed` set. Update the
   Actions section of CLIENT-PLUGINS.md.
2. **hammer-plugin-sdk.** `display` and `refresh` in the three kits;
   `ActionDriver` calling on a refresh field's change.
3. **hammer-plugins.** Sci-fi names as one action.
4. **Later.** The format bar's entity link, and the data store, each with its
   own plan; then custom interview questions.
