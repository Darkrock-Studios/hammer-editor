# Story Ideas

Account-level feature for quickly jotting down story ideas. Ideas are rich-text (markdown) blobs
with tags, living outside any project. An idea can be **promoted** into a new project, seeding the
idea's content as that project's first Note.

Lives as a new tab in the Project Selection window (a new `ProjectSelection.Locations` entry).

## Design Principles

Same as the rest of Hammer:

- **Offline-first, single-client first.** The feature is fully usable with no server. Files are
  human-readable and self-contained.
- **Minimal bookkeeping, none required.** Sync metadata is derived/optional; losing it degrades
  gracefully (baseline-less uploads, backfill on next sync) but never loses content.
- **Shape-agnostic server.** The server stores idea content as an opaque blob with a
  client-supplied hash. Adding fields to the model is a client-only change.

## Data Model

```kotlin
@Serializable
data class StoryIdea(
    val id: Uuid,                      // client-generated, the identity
    val created: Instant,
    val updated: Instant,
    val title: String? = null,         // display falls back to first line of content
    val content: String,               // markdown, max 10,000 characters (client-enforced)
    val tags: Set<String> = emptySet(),
    val promoted: Instant? = null,     // set when a project was created from this idea
    val archived: Instant? = null,     // archived ideas are hidden from the main list, never purged
)
```

- **IDs are client-generated UUIDs**, not the per-project sequential ints entities use. Ideas are
  islands — nothing references an idea by ID, and they don't participate in the Entity Update
  Sequence — so none of the re-ID machinery (`CoalateIdsOperation`, `last_id` watermarks) applies.
  Two devices can create ideas offline with zero coordination. Precedent: projects themselves are
  keyed by a server UUID (`ProjectId`) at account scope.
- **`promoted` / `archived` are nullable timestamps, not booleans** — one field is both the flag
  and the date. They are independent: a promoted idea can later be archived.
- **No link to the created project.** Promotion records only the timestamp. A project-name or
  `ProjectId` reference would be brittle (renames, deletion, never-synced projects have no
  `ProjectId`) and rename-awareness isn't worth the machinery.
- **Rich text is a markdown `String`**, same convention as Notes/Scenes, edited with the existing
  `MarkdownEditField` / `ComposeRichText` stack.

## Storage (client)

Ideas live in a new top-level directory **inside the projects root** (sibling of project
directories): `.ideas/`. `ProjectsRepository.getProjects()` already filters dot-directories, so
the directory is invisible to the project list with no extra guarding. Precedent for account-level
data at the root: `sync.json`.

One file per idea: `.ideas/idea-<uuid>.md`, **markdown with TOML front matter** delimited by
`+++` fences (the Hugo TOML front-matter convention). The front matter is parsed with tomlkt
(already a dependency); the body after the closing fence is `content`.

```markdown
+++
id = "0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c"
title = "The Lighthouse Keeper's Daughter"
created = "2026-07-03T14:22:05Z"
updated = "2026-07-03T14:31:48Z"
tags = ["gothic", "coastal", "generational"]
+++

What if the light itself was the inheritance...
```

Absent optionals (`title`, `promoted`, `archived`) are omitted from the block entirely.

The at-rest format is a client-local decision — the wire format is the JSON-serialized
`StoryIdea`, so the file format can change later without touching sync.

### Size limit

- **Client:** 10,000 characters of `content`, enforced at the editor/repository layer with UI
  feedback.
- **Server:** a byte cap on the opaque blob (64 KB), analogous to `MAX_ENTITY_CONTENT_LENGTH`.
  The server is shape-agnostic and cannot count characters; two limits, two layers.

## Client Architecture

Standard spine, **global (account) scope — not `ProjectDefScope`**:

- `IdeasDatasource` — Okio file I/O against `.ideas/` (template: `NotesDatasource`, minus re-ID)
- `IdeasRepository` — list flow, CRUD, dirty tracking, char-limit enforcement
- `StoryIdeas` / `StoryIdeasComponent` / `StoryIdeasUi` — new `ProjectSelection.Locations` entry

### Promotion flow

"Create project from idea":

1. Create the project via `ProjectsRepository` (name pre-filled from `title` / first line).
2. Within the new project's scope, write the idea's `content` as the project's first Note
   (a straight markdown copy; note gets the project's first entity ID as normal).
3. Stamp `promoted` on the idea.

The seeded Note is an ordinary entity from that point on — it syncs with its project and has no
back-reference to the idea.

## Sync

Ideas sync as a **new phase inside the existing account sync session** — same `syncId`, same
2-minute sliding expiry, same same-install reclaim rules. No third session type.

1. Within the account session, the client fetches the server's idea state: a list of
   `{ uuid, hash }` plus deleted-idea tombstones.
2. Deletions reconcile in both directions (tombstones win over stale copies).
3. Client downloads ideas it is missing or holds a different hash of; uploads dirty ideas and
   ideas the server doesn't know (plain UUID set difference — no update sequence needed).

### Deletion propagation

Only the **server** keeps tombstones. The client keeps no deletion memory at all — except a
short-lived *pending operation* while a delete awaits its next sync, which is not a tombstone
but an outbox entry.

Why the pending record is required on synced clients: at sync time, "the server has an idea
the client doesn't" is ambiguous between *created elsewhere* (must download) and *deleted
here* (must propagate). No ID scheme can encode the difference; an unrecorded delete is
re-downloaded and silently resurrects on every sync.

- **Client — pending operation only.** `pendingDeletes: Set<Uuid>` in `.ideas/sync.json`,
  written at delete time, erased on server ack (precedent: `projectsToDelete` in the root
  `sync.json`). Nothing permanent is ever needed: UUIDs are never reused, so the reason the
  entity `SyncJournal` keeps `deletedIds` forever (allocator protection) does not apply.
  Mirroring `SyncJournal.recordNewId`, deletes are only recorded on clients that have synced
  ideas before — a never-synced client deletes the file and that is genuinely all.
- **Server — permanent tombstones.** `deleted_idea (user_id, uuid)` rows, written when a
  delete lands. Every device prunes local copies matching the tombstone list on each sync.

Migrating to a fresh server needs no tombstones on either side — clients upload everything
they hold, and deleted ideas are not part of that. Caveat (shared with entity sync): a stale
device still holding a deleted idea can resurrect it on a fresh server, since the old
server's tombstones died with it. Accepted cost of "no required bookkeeping."

**Delete-vs-edit race:** deletion wins, matching entity sync — a device that edited an idea
offline while another device deleted it loses the edit when the tombstone is applied.

**Degradation:** losing `.ideas/sync.json` loses not-yet-synced deletions, so those ideas
resurrect on the next sync — annoying but lossless, the same failure mode as the entity
journal. Consistent with "no bookkeeping is actually required."

### Conflicts

Clone of the entity scheme:

- The client persists a per-idea **conflict baseline** (the hash last agreed with the server) and
  a dirty set in `.ideas/sync.json` (mirrors the root `sync.json`). The baseline is the locked
  last-agreed hash, never re-derived from current content (same phantom-conflict reasoning as
  entities). Losing the sidecar degrades to baseline-less uploads with backfill on next sync.
- Upload sends the baseline as `X-Original-Hash`. Server hash mismatch → `409` with the server's
  copy → user resolves in UI → force-upload the resolution (which may be a merge).
- Conflict UI reuses `ConflictCommon.kt` (side-by-side local/remote panes); a new `IdeaConflict`
  composable modeled on `NoteConflict.kt`.

### Hashing

Client-supplied hash over the `StoryIdea` fields, following the established evolution rules:

1. New fields get serialization defaults so old data deserializes.
2. Absent/empty values contribute **zero bytes** to the hash (collections sorted with a size
   prefix), so existing baselines and server rows stay byte-identical.
3. Covered by `EntityHashSensitivityTest`-style guards.

The idea set should eventually fold into the **sync probe** (an account-level ideas hash alongside
the per-project hashes) so an unchanged ideas set costs zero round-trips; optimization, not v1.

## Server

New account-scoped tables mirroring `StoryEntity.sq`, minus `project_id`, keyed by UUID:

```sql
CREATE TABLE story_idea (
    user_id BIGINT NOT NULL,
    uuid    UUID   NOT NULL,
    content TEXT   NOT NULL,   -- serialized StoryIdea JSON, stored verbatim, never decoded
    hash    TEXT   NOT NULL,   -- client-supplied
    cipher  TEXT,              -- same at-rest encryption path as entities
    UNIQUE(user_id, uuid)
);

CREATE TABLE deleted_idea (
    user_id BIGINT NOT NULL,
    uuid    UUID   NOT NULL,
    UNIQUE(user_id, uuid)
);
```

Endpoints under `/api/ideas/{userId}/…`, all gated on the account sync session's syncId header:

| Endpoint | Verb | Purpose |
| --- | --- | --- |
| `/state` | POST | `{ ideas: [{id, hash}], deletedIdeas: [id] }` |
| `/idea/{ideaId}` | GET | Download one idea (opaque blob + hash) |
| `/idea/{ideaId}` | POST | Upload; `409` + server copy on baseline mismatch, `410` if tombstoned, `413` over the 64 KiB cap |
| `/idea/{ideaId}/delete` | POST | Delete + write the permanent tombstone |

Idea blobs are encrypted at rest with the same content-encryption path as entities, and are
covered by the key-rotation convergence job and the key-prune in-use scan.

## Lifecycle

- **Archive** hides an idea from the main list (visible under a filter). Archived ideas are
  **never purged** — archive is not a deletion path.
- **Delete** is explicit and separate; it writes a tombstone so the deletion propagates on sync
  and cannot be resurrected by a stale device.

## Implementation Phasing

1. **Phase 1 — local-only feature.** Datasource, repository, model + front-matter serialization,
   the Story Ideas tab (list, editor, tags, archive, delete), promotion flow. Fully usable
   offline; no server or protocol changes.
   - The browse UI reuses Notes' vocabulary: first lift `BrowseNotesUi`'s private molecules
     (`NoteCard`, `TagFilterBar`, `ActiveFiltersStrip`, `CollapsingStrip` — the last already
     earmarked for `HdCollapsingStrip` in the design README) into `Hd*` design-system
     components, re-point Notes at them with no visual change, then build the Ideas screen
     from the same pieces. The extraction lands as its own commit before new screen code.
2. **Phase 2 — sync.** ✅ Complete. Server tables + routes (`story_idea`/`deleted_idea`,
   migration 5→6), `IdeaHasher` + golden-pin sensitivity tests, the client ideas phase inside
   `ClientAccountSynchronizer`, the `.ideas/sync.json` sidecar, and `IdeaConflictUi` in the
   account sync dialog (editable local pane for manual merges).
3. **Later:** sync-probe integration; tag-based filtering/search niceties.
