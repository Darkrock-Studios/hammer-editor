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
3. **A server-side action never deletes a co-author's local data, and never uploads it somewhere
   new.** Being removed, or the owner deleting the project, leaves the local copy in place with
   sync turned off for it. This uses a new per-project sync toggle, exposed in project settings
   whenever a sync server is configured, which is useful on its own: a scratch project, or one the
   author wants kept off the server. Turning sync back on uploads the copy as the author's own
   project.
4. **Owner only: delete, rename, invite, remove, publish, kudos toggle.** Those put the owner's
   name on something or end the project. Share links and review requests are open to every
   author. Co-authors sync content, place the project in their own groups, and see it on the
   website.
5. **Invite by email, accept on the website.** The invite stores the address, not an account, and
   the owner sees the same pending row whether or not an account exists, so inviting never
   confirms one. Both accounts must be active and on the whitelist, and both need a pen name,
   which is the label each side sees for the other. Membership is ignored for an inactive or
   deleted account, so losing whitelist access revokes it too.
6. **Attribution is metadata outside the synced blobs.** The server stamps who uploaded an entity
   or a writing-activity slot and returns it on download and conflict responses. Entity shapes
   and hashes do not change. Writing stats group by author, and keep doing so after an author is
   removed, because the author's name is stored on the slot.
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
  email CITEXT NOT NULL,
  invited_by BIGINT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (project_id, email)
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
`updated_at TIMESTAMPTZ`, and `writing_activity` gains nullable `author_id BIGINT` and
`author_name TEXT`. They are written on every store and never hashed. `author_name` is the pen
name at upload time and has no foreign key, so a removed or deleted author keeps their label.

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

- **Invite:** on the story page, owner only, a dialog that takes an email address and creates
  `project_invite`. The owner must have a pen name (it is what the co-author's client shows as
  `owner`). The response and the pending-invite row are the same whether or not an account holds
  that address, and no mail is sent to an address without an account, so inviting confirms
  nothing. Invites share the login rate limit. Invites are listed with the members on the same
  page, each with a revoke/remove control; the owner's own address and existing members are
  refused.
- **Accept/decline:** a dashboard notice for invites whose `email` matches the session account.
  Accepting requires a pen name (set inline if missing), inserts `project_member`, deletes any
  `project_member_removed` row, and deletes the invite. Declining deletes the invite.
- **Dashboard:** `getProjectsWithSyncDate(userId)` unions member projects, labelled "shared by
  {owner}". `findProjectByUrlSegment` resolves member projects too, so `/story/{segment}` and
  `/read` work for co-authors with the owner's rows.
- **Owner-only on the story page:** publish, kudos toggle, invite/remove, delete, rename.
  Co-authors see a *Leave* control. Kudos: `giverId` is also refused when it is a member of the
  project.
- **Share links** are open to every author. `project_access` is keyed by project already and the
  public URL uses the owner's pen name, so the only change is not adding an owner check. The
  access list shows who created each link.
- **Review requests** are open to every author. `ReviewRepository` splits the two ids it
  conflates today: the requester (`review_request.user_id`, snapshot encryption, the invite
  email's "from") and the owner (reading scenes, minting drafts on commit, the internal sync
  session). Requests are listed per project, so both authors see them. Leaving or being removed
  deletes the leaver's open requests.
- **Admin:** project counts in the admin user list stay owner-based; the project list shows the
  member count.
- Notification mail to an invitee with an account (`email/project-invite.mustache`) is off for
  the first pass; the dashboard notice is enough.

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

### Per-project sync toggle

A project can be excluded from sync on this device. The flag lives in the account `sync.json`
(`ProjectsSynchronizationData.localOnlyProjects: Set<ProjectId>`), next to the baselines: it is
sync bookkeeping, per device, and `project.toml` stays identity-only. `ProjectsRepository`
exposes it and the project settings screen shows a *Sync this project* switch, only when
`serverSettings` is set. The project card shows a cloud-off marker for an excluded project.

While excluded, the project is invisible to every sync path on this device: account sync neither
creates, renames, moves nor deletes it on the server (it is filtered out before the phases run,
like a project in use), the change probe and background sync skip it, and the editor's sync
action is hidden. Nothing else changes; entities are still marked dirty in the project journal,
so turning sync back on resumes with the normal conflict detection, exactly like a device that
has not synced in a while.

Two consequences worth stating in the dialog that turns sync off:

- Other devices and the server are unaffected. The switch means "not from this device".
- Deleting an excluded project locally does not delete it on the server, and if the server still
  holds it, the next account sync re-creates it locally. The delete dialog for an excluded project
  that has a sync baseline therefore offers *Also delete on the server*.

A project that enters the set because of a removal loses its project `sync.json` (the baseline
described a server project this device no longer follows) and its `ProjectBaseline` and `owner`.
Turning sync on then takes the unknown-project path: the client creates it under its id,
re-minting if the id is held elsewhere, and uploads everything. A project excluded by hand keeps
its journal, so re-enabling it resumes where it left off.

### Leave vs delete

`ProjectsService.deleteProject` on a shared project queues the same `/delete`, but the UI text
changes: the confirm dialog says *Leave project*, explains that the owner's copy is untouched,
and offers *Keep an unsynced copy on this device*, which moves the project into
`localOnlyProjects` instead of deleting the folder. The server interprets `/delete` from a
member as leave. A
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

For each id in `removedProjects`: move the project into `localOnlyProjects` (which drops its
baseline, `owner` and project journal as above), log `sync_log_account_project_removed` ("You are
no longer a co-author of %1$s. Your copy is kept on this device and no longer syncs. Turn sync
on in its settings to upload it as your own project."), and send `/delete` to acknowledge. The
same session's create phase skips it, like any excluded project. The owner-deleted case is
identical from the client's view. If the user does not want the copy, they delete it as any other
project; if they want it, they flip the switch and the normal create path uploads it.

### Tests

- Account sync lists a shared project, creates it locally, places it in the caller's group.
- Same-named local project is left alone; the shared one lands in `"{name} ({owner})"`.
- An excluded project is skipped by account sync, the probe and background sync; its local
  rename or delete sends nothing; a server rename of it is not applied.
- Turning sync on for an excluded project with a stale journal creates it fresh on the server.
- `removedProjects` keeps the folder, excludes it, logs, acknowledges; nothing is uploaded.
- Leave with "keep a copy" excludes the project instead of deleting the folder.
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

`DeviceLog` gains `author: String? = null`. The server ignores whatever the client sends in it:
on POST it stores `author_id` and `author_name` (the caller's pen name) on the row, and on GET it
fills `author` in each slot from the row. The client writes the returned value into its local
copy of every slot, its own included, so `{project}/scenes/.activity/{deviceId}.toml` carries the
author and stats work offline. A slot with a null author is read as the owner's (legacy data,
or the own slot before its first round trip).

`StatisticsService` groups by author when more than one is present, so "Words by Device" becomes
"Words by Author" with devices nested, and `stats.sessions` in the MCP tools gains the author.
Because `author_name` is plain text on the row and in the local file, removing an author, or the
author deleting their account, changes nothing in the stats of anyone who still has the project.
Their devices simply stop adding sessions.

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
3. **Server: website invite, accept, members list, dashboard and story page changes,** share
   links and review requests for co-authors.
4. **Clients: per-project sync toggle.** `localOnlyProjects`, the settings switch, the card
   marker, exclusion from every sync path, the delete dialog's server option. Independent of
   co-authoring and shippable on its own.
5. **Clients: owner awareness.** `owner` in the baseline, leave dialog, no-adoption rule, folder
   disambiguation, `removedProjects` handling.
6. **Attribution.** Server stamps and headers, conflict tab label, writing-activity author.
7. **Docs:** `SYNCING-PROTOCOL.md` sections for membership, `removedProjects`, leave, and the
   session rules; `ARCHITECTURE.md` on the folder naming rule and `localOnlyProjects`.

PRs 1 to 3 ship without the clients changing; a co-author on a current 4.0.0 build would see
the project, sync it, and would be able to delete locally only. PR 5 should land before the
feature is announced, because of the name-adoption risk.

### How the work runs

Implementation sessions run on Opus 5.5 at high effort. Each PR is built as a series of
checkpoint commits, one per coherent step (schema, resolution, sessions, and so on). After every
checkpoint the diff gets a code review, and every finding is addressed before the next step
starts. A PR is opened only once its last checkpoint has been reviewed clean.

## Later, optional

- Soft "claimed" marker on a scene ("Alice is working on this") as project metadata outside the
  entity, to reduce whole-scene conflicts.
- Roles (read-only co-author) on `project_member`.
- In-app invite management.
- Invite notification emails.
- Per-project cipher secret, if owner-scoped encryption ever needs to be decoupled from the owner
  account (for example, transferring ownership).

## Open questions

- Should co-authors see the project's writing stats on the website story page, or only in-app?
- Does `removedProjects` need a reason (removed, owner deleted) for the log line? Leaving is
  acknowledged in the same request, so it never appears in the list.
- Should the owner be able to purge a removed co-author's writing-activity slots?
