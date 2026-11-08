# Co-authoring

Design for letting a second account sync a project. The owner invites another user of the same
sync server from the website; once accepted, the co-author's devices sync the project exactly as
the owner's other devices do. There is no real-time editing: conflicts are detected and merged by
the existing hash protocol, one scene at a time. Only the owner can delete the project or manage
who is on it; a co-author can leave.

The clients need almost nothing new. The work is on the server, which today assumes the caller
owns every project it touches.

## Where ownership lives today

| Concern | Where | Keyed on |
|---|---|---|
| Project row | `project` in `Project.sq`; `uuid` is globally unique, `(name, user_id)` unique | `user_id` |
| Entities, tombstones, project data, writing activity | `story_entity`, `deleted_entity`, `project_data`, `writing_activity` | `(user_id, project_id, …)` in every unique key and every query |
| Encryption at rest | `ProjectEntityDatabaseDatasource.storeEntity` / `loadEntity` encrypt with a key salted by `account.cipher_secret` of the `userId` passed in | caller |
| API auth | `Security.kt`: the `{userId}` path segment must equal the bearer token's user | caller |
| Project lookup on a route | `RouteHelpers.requireProjectDef(userId)` → `getProject(userId, uuid)`, `410 Gone` otherwise | caller |
| Sync locks | `PROJECTS_SYNC_MANAGER` keyed by `userId`; `PROJECT_SYNC_MANAGER` keyed by `ProjectSyncKey(userId, projectDef)` | caller |
| Project delete tombstones | `deleted_project(user_id, uuid)` | caller |
| Groups and placement | `project_group` per user; one `project.group_id` per project | owner |
| Website | `findProjectByUrlSegment(session.userId, …)` on every `/story/…` route; dashboard lists `getProjectsWithSyncDate(userId)` | session user |
| Public pages, reviews, kudos | run as `project.user_id`; kudos refuses `giverId == project.user_id` | owner |
| Client identity | `ServerSettings(userId, email, tokens)`; one install id per device, used for tokens and as the writing-activity `deviceId` | one user |

Nothing in `common/` or `base/` knows about more than one user. Every project URL carries the
signed-in user's own id, projects are matched by `ProjectId` and, as a fallback, by name within
the account, and anything not tied to a device is "the same user on another device".

## Decisions

1. **Membership is a server table; storage stays owner-scoped.** A `project_member` row grants a
   user access to a project. Every route resolves the project by `uuid`, checks membership, and
   then runs the datasources as the **owner**. Entities keep being encrypted with the owner's
   `cipher_secret`, so nothing is re-encrypted and the owner can always read every row. The
   `{userId}` in client URLs keeps meaning "the caller" and is no longer used to find the project.
2. **The client learns one fact per project: who owns it.** `ApiProjectDefinition` gains an
   optional `owner`. A co-author's client uses it to offer *Leave* instead of *Delete*, to skip
   name-based adoption, and to label the project. Nothing else in the protocol changes shape.
3. **A server-side action never deletes a co-author's local data.** Being removed, or the owner
   deleting the project, turns the local copy into a local-only project with a clear log line.
   Re-uploading it as the co-author's own project is an explicit choice, not the current silent
   re-create.
4. **Owner only: delete, rename, invite, remove, publish, share links, review requests.**
   Co-authors sync content, place the project in their own groups, and see it on the website.
5. **Invite by pen name, accept on the website.** Both accounts must be active and on the
   whitelist. Membership is ignored for an inactive or deleted account, so losing whitelist
   access revokes it too.
6. **Attribution is metadata outside the synced blobs.** The server stamps who uploaded an entity
   or a writing-activity slot and returns it on download and conflict responses. Entity shapes
   and hashes do not change.
7. **Ships in 4.0.0 under protocol version 4.** Protocol enforcement is exact-match, so every
   4.0.0 client understands the new optional fields. If this slips to a point release, bump
   `HAMMER_PROTOCOL_VERSION` so an older client never sees a shared project in its list (it would
   adopt it by name).

## Phase 1: Server membership and owner-scoped storage

### Schema

New tables in `server/src/main/sqldelight/…`, mirrored in `migrations/12.sqm`:

```sql
CREATE TABLE project_member (
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  group_id BIGINT REFERENCES project_group(id) ON DELETE SET NULL,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (project_id, user_id)
);

CREATE TABLE project_invite (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  invitee_id BIGINT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  invited_by BIGINT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (project_id, invitee_id)
);

CREATE TABLE project_member_removed (
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  removed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (project_id, user_id)
);
```

The owner is still `project.user_id`; it is not duplicated into `project_member`. `group_id` on
the member row is the co-author's placement (see Account sync). `project_member_removed` is the
co-author's counterpart of `deleted_project`: it tells the client "you had this, you no longer do"
without a tombstone that would delete local data. A row is written on leave and on removal, and
cleared if the user is invited again.

For attribution (Phase 3), `story_entity` gains nullable `updated_by BIGINT` and
`updated_at TIMESTAMPTZ`, and `writing_activity` gains nullable `uploaded_by BIGINT`. They are
written on every store and never hashed.

### Project resolution

`requireProjectDef(userId)` becomes `requireProjectAccess()` returning

```kotlin
data class ProjectAccess(val projectDef: ProjectDefinition, val ownerId: Long, val isOwner: Boolean)
```

It looks the project up by `uuid` alone (`project_uuid_unique` already exists), then checks
`ownerId == caller` or a `project_member` row, else `410 Gone` as today. The owner's account must
be active; a project whose owner is soft-deleted answers `410` to members until the purge removes
it.

Every project-level route then passes `access.ownerId` where it passes `userId` today:
`upload_entity`, `download_entity`, `delete_entity`, `project_data`, `writing_activity`,
`begin_sync`, `end_sync`. The datasource interfaces (`ProjectEntityDatasource`,
`ProjectDataDatasource`, `ServerWritingActivityRepository`) keep their `userId` parameter; it now
always receives the owner. Rename the parameter to `ownerId` where it is cheap, so the invariant
is visible.

`ProjectDao.updateSyncData` is keyed by `(user_id, name)` and must move to `uuid`, since the
session key below no longer carries the owner's name.

### Sync sessions

`ProjectSyncKey(userId, projectDef)` becomes `ProjectSyncKey(projectId: ProjectId)`. One session
per project regardless of who holds it; same-install reclaim keeps working because install ids
are global UUIDs. `beginProjectSync` and `validateSyncId` currently refuse while the **caller's**
account session is active; they must also refuse while the **owner's** account session is active,
since that is the session that can rename or delete the project. Symmetrically, the owner's
`delete` and `rename` refuse (`409`) while a project session is live, so a co-author mid-sync is
not pulled out from under.

`ProbeProjectChangesUseCase` and `ReviewRepository.withInternalSyncSession` use the same key.

### Account sync

`begin_sync` for user U returns owned projects plus every project U is a member of:

```kotlin
data class ApiProjectDefinition(
	val name: String,
	val uuid: ProjectId,
	val groupId: GroupId? = null,
	/** Pen name of the owner when the caller is a co-author; null for the caller's own projects. */
	val owner: String? = null,
)

data class BeginProjectsSyncResponse(
	…,
	/** Projects the caller was a member of and no longer is. The client keeps its copy. */
	val removedProjects: Set<ProjectId> = emptySet(),
)
```

`groupId` for a shared project comes from `project_member.group_id`, so each member places the
project in their own tree. `/move` writes `project.group_id` for the owner and the member row for
a co-author. Group upsert and delete are unchanged (groups are still per user).

`removedProjects` lists `project_member_removed` rows for the caller. The row is deleted once the
client has acknowledged it, which it does by calling `/delete` with that id: for a member or
ex-member `/delete` means "leave" (see below), so the same request serves both.

`create` with a `projectId` the caller is a member of answers `alreadyExisted = true` with that id
(idempotent, as for owned projects). `create` by name does not match shared projects: adoption by
name is per account and only among owned projects.

`sync_probe` for a shared project works as for an owned one, with the owner's rows.

### Create, rename, delete, leave

| Request | Owner | Co-author |
|---|---|---|
| `create` | as today | idempotent on a project they are a member of; otherwise creates their own |
| `rename` | as today; refused while a project session is live | `403` |
| `delete` | as today; also writes `project_member_removed` for every member, so their next account sync reports it under `removedProjects` | **leave**: delete the member row, write `project_member_removed`, `200` |
| `move` | writes `project.group_id` | writes `project_member.group_id` |

The owner's `delete` must drop member rows before the `deleted_project` tombstone is written, so a
member never sees the project under `deletedProjects` (which deletes the local folder).

Owner account deletion cascades the project and its members. Co-authors keep their local copies;
on their next sync the project is in neither list and the uuid is free, so the existing path
re-creates it under their own account. That is acceptable and documented, not special-cased.

### Website

- **Invite:** on the story page, owner only, a dialog that takes a pen name and creates
  `project_invite`. One pending invite per invitee per project. Invites are listed with the
  members on the same page, each with a revoke/remove control.
- **Accept/decline:** a dashboard notice for pending invites; accept inserts `project_member`,
  deletes any `project_member_removed` row, and deletes the invite.
- **Dashboard:** `getProjectsWithSyncDate(userId)` unions member projects, labelled "shared by
  {owner}". `findProjectByUrlSegment` resolves member projects too, so `/story/{segment}` and
  `/read` work for co-authors with the owner's rows.
- **Owner-only on the story page:** publish, share links, kudos toggle, review requests, delete,
  rename. Co-authors see a *Leave* control. Kudos: `giverId` is also refused when it is a member
  of the project.
- **Admin:** project counts in the admin user list stay owner-based; the project list shows the
  member count.
- Invite emails are optional (`email/project-invite.mustache`) and off for the first pass; the
  dashboard notice is enough.

### Tests

- Membership resolution: owner, member, non-member (`410`), soft-deleted owner, inactive member.
- Entity round trip by a co-author decrypts for the owner, and the reverse.
- Two users cannot hold a project session at once; same-install reclaim still works.
- Owner `delete` while a member's project session is live is refused.
- Owner `delete` yields `removedProjects`, never `deletedProjects`, for the member.
- Co-author `delete` leaves; `rename` is `403`; `move` touches only the member row.
- `create` by a co-author with a same-named own project does not adopt the shared one.
- Existing `ProjectRoutes`/`ProjectsRoutes` tests pass unchanged with `{userId}` = owner.

## Phase 2: Clients

### What the client learns

`ProjectDef` gains `owner: String?` carried from `ApiProjectDefinition` through the account sync
baseline (`ProjectBaseline` in the account `sync.json`), not into `project.toml`: it is a fact
about the server relationship, like the baseline name. `ProjectsRepository` exposes it so the
project list, project home and conflict UI can show "Shared by Alice".

### Leave vs delete

`ProjectsService.deleteProject` on a shared project queues the same `/delete`, but the UI text
changes: the confirm dialog says *Leave project*, explains that the owner's copy is untouched and
that the local copy will be removed. The server interprets `/delete` from a member as leave. A
co-author cannot rename a shared project in the UI, and `ProjectAttributeReconciler` never emits
`RenameOnServer` for one: a folder renamed outside the app is renamed back with `RenameLocally`
to the server's name. Moves still reconcile both ways, since placement is per member.

### Name collisions and adoption

Account sync adopts a server project onto a same-named local folder with no id match
(`ClientAccountSynchronizer`, around the `adopt` step). For a shared project this would merge an
unrelated local story into it through a full conflict check. Rules:

- A project with `owner != null` is never adopted by name and never adopts an unmarked folder.
- When the shared project's name collides with a local folder, the local folder for it is
  `"{name} ({owner})"`. A project's local name is its folder name, so the two differ for that
  project: `ProjectBaseline.name` keeps holding the server's name, and the reconciler's "did the
  local name change" check for a shared project compares against the disambiguated form rather
  than the raw baseline. A later rename on the server re-derives the folder the same way. The
  suffix is only applied on collision, so most co-authors never see it.

### Removed projects

For each id in `removedProjects`: clear the project's `sync.json`, drop the project data
baseline, log `sync_log_account_project_removed` ("You are no longer a co-author of %1$s. Your
copy is kept as a local project; sync it again to upload it as your own."), and send `/delete`
to acknowledge. The project then has an id the server does not know; the existing "neither
listed nor tombstoned" path offers nothing special, so the next account sync would create it
under the user's own account. To make that the explicit choice in decision 3, the client marks
the project `localOnly` in the account baseline and skips it in `createProjectsOnServer` until
the user re-enables sync for it from the project list (a small per-project toggle, which is also
useful on its own).

### Tests

- Account sync lists a shared project, creates it locally, places it in the caller's group.
- Same-named local project is left alone; the shared one lands in `"{name} ({owner})"`.
- `removedProjects` keeps the folder, clears the journal, marks it local-only, acknowledges.
- Delete on a shared project shows the leave dialog and sends `/delete`.
- Component tests through the real `TestComponentContext` harness.

## Phase 3: Attribution (additive)

### Entities

The server writes `story_entity.updated_by` and `updated_at` on every store. `download_entity`
and the `409` conflict response add headers `X-Updated-By` (pen name) and `X-Updated-At`. The
client passes them into `EntityConflict` and the conflict tabs read "Local" and "Remote: Alice,
2 hours ago" (`sync_conflict_tab_remote_by`). Owned projects with no co-authors keep the plain
"Remote". No hash changes, no client-format change.

### Writing activity

The server stamps `writing_activity.uploaded_by` on POST and returns it per slot in
`WritingActivityResponse` as an optional `author: String?` on `DeviceLog` (the server rewrites the
stored JSON's wrapper, not the sessions). `StatisticsService` groups by author when more than one
is present, so "Words by Device" becomes "Words by Author" with devices nested, and
`stats.sessions` in the MCP tools gains the author. Old slots without an author count as the owner.

### Project list and home

"Shared by Alice" on the project card for co-authors; "Shared with Bob, Carol" on the owner's
project home, read from a new `GET /api/project/{userId}/{projectId}/members` (owner and members'
pen names). Managing members stays on the website.

## Pull request plan

Stacked on `4.0.0`, squash-merged bottom-up:

1. **Server: membership resolution and owner-scoped storage.** Schema, `requireProjectAccess`,
   datasources take the owner, `ProjectSyncKey` by uuid, `updateSyncData` by uuid, session rules.
   No routes to add a member yet, so behaviour is unchanged for every existing client.
2. **Server: account sync and leave.** `owner` and `removedProjects`, member placement, `/delete`
   as leave, owner delete writes removals, rename `403` for members.
3. **Server: website invite, accept, members list, dashboard and story page changes.**
4. **Clients: owner awareness.** `owner` in the baseline, leave dialog, no-adoption rule, folder
   disambiguation, `removedProjects` handling, local-only toggle.
5. **Attribution.** Server stamps and headers, conflict tab label, writing-activity author.
6. **Docs:** `SYNCING-PROTOCOL.md` sections for membership, `removedProjects`, leave, and the
   session rules; `ARCHITECTURE.md` on the folder naming rule and `localOnly`.

Phases 1 to 3 ship without the clients changing; a co-author on a current 4.0.0 build would see
the project, sync it, and would be able to delete locally only. Phase 4 should land before the
feature is announced, because of the name-adoption risk.

## Later, optional

- Soft "claimed" marker on a scene ("Alice is working on this") as project metadata outside the
  entity, to reduce whole-scene conflicts.
- Roles (read-only co-author) on `project_member`.
- In-app invite management.
- Invite emails.
- Per-project cipher secret, if owner-scoped encryption ever needs to be decoupled from the owner
  account (for example, transferring ownership).

## Open questions

- Should a co-author be able to rename? The three-way attribute merge would carry it to everyone,
  which is consistent with every other shared attribute, but it collides with both users' name
  spaces. Owner-only is the simpler rule and is what this design assumes.
- Should co-authors see the owner's writing stats on the website story page, or only in-app?
- Does `removedProjects` need a reason (left, removed, owner deleted) for the log line?
