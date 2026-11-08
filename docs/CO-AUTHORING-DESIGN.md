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
   The owner is passed as an `OwnerId` value class that only the access check can produce, so a
   route cannot hand a datasource the caller by mistake.
2. **The client learns one fact per project: who owns it.** `ApiProjectDefinition` gains an
   optional owner id and display name. The client stores both in `project.toml`. A co-author's
   client uses them to offer *Leave* instead of *Delete*, to skip name-based adoption, to never
   create the project on the server as its own, and to label it. Nothing else in the protocol
   changes shape.
3. **A server-side action never deletes a co-author's local data, and never uploads it somewhere
   new.** When the server stops listing a shared project, whatever the cause, the local copy
   stays in place with sync turned off for it. This uses a per-project sync toggle in
   `project.toml`, exposed in project settings whenever a sync server is configured, which is
   useful on its own: a scratch project, or one the author wants kept off the server. Turning sync
   back on for a project that had an owner asks before uploading it as the author's own.
4. **Owner only: everything but writing.** Delete, rename, move into a group, invite, remove,
   publish, kudos toggle, share links and review requests stay with the owner. A co-author syncs
   content, sees the project on the dashboard, can read it on the website, and can leave. Opening
   share links and reviews to co-authors is deferred (see Later, optional): reviews in particular
   conflate requester and owner in a way that is costly to split.
5. **Invite by email, accept on the website.** The invite stores the address, not an account, and
   the owner sees the same pending row whether or not an account exists, so inviting never
   confirms one. Both accounts must be active and on the whitelist, and both need a pen name,
   which is the label each side sees for the other. Membership is ignored for an inactive or
   deleted account, so losing whitelist access revokes it too. A project has at most five
   co-authors; the feature is for writing partners, not distribution.
6. **Attribution is writing stats only.** The server stamps who uploaded a writing-activity slot
   and returns it on download. Writing stats group by author, and keep doing so after an author
   is removed, because the author's name is stored on the slot. Entity blobs, hashes and the
   conflict UI do not change.
7. **Ships in 4.0.0 under protocol version 4.** Protocol enforcement is exact-match and version 4
   is new with 4.0.0, so no client can speak it without carrying the client-side changes below.
   That is the deployment gate: the server PRs can land first with no risk of an older client
   adopting a shared project by name.

## Phase 1: Server membership and owner-scoped storage

### Schema

New tables in `server/src/main/sqldelight/…`, mirrored in `migrations/12.sqm`:

```sql
CREATE TABLE project_member (
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
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
```

The owner is still `project.user_id`; it is not duplicated into `project_member`. There is no
removal table and no per-member tombstone: the client never needs to be told "you had this, you
no longer do", because a project that had an owner is never auto-created (see Parked projects),
so simply not listing it is enough.

The cascades are the safety net, not the mechanism. `deleteProject` and `purgeUserData` delete
`project_member` and `project_invite` rows explicitly, because the test database runs with
foreign keys disabled.

For attribution (Phase 3), `writing_activity` gains nullable `author_id BIGINT` and
`author_name TEXT` in the same migration, written on every store and never hashed. `author_name`
is the pen name at upload time and has no foreign key, so a removed or deleted author keeps their
label. The slot's unique key is not changed here: SQLDelight's Postgres parser rejects an
expression conflict target, so Phase 3 gets its own migration that backfills
`author_id = user_id`, makes it `NOT NULL`, and swaps the unique key to
`(user_id, project_id, author_id, device_id)`.

### Project resolution

`requireProjectDef(userId)` becomes `requireProjectAccess()` returning

```kotlin
@JvmInline value class OwnerId(val id: Long)

data class ProjectAccess(val projectDef: ProjectDefinition, val owner: OwnerId, val isOwner: Boolean)
```

It looks the project up by `uuid` alone (`project_uuid_unique` already exists). Unknown uuid:
`410 Gone`, as today. Known uuid but the caller is neither the owner nor a member, or the owner's
account is inactive or soft-deleted: `403` with body `{"error": "not_member"}`. The two must
stay distinct; the client treats `410` as "free to create" and `403` as "exists, not yours".

`OwnerId` has a private constructor. `OwnerId.of(projectRow)` is the safe way to get one, and
`OwnerId.unchecked(id)` exists behind an `@UncheckedOwner` opt-in for code that names the owner
without a project row: the account-sync routes (the caller is the owner by definition),
`UserSession.asOwner` on the owner-only website pages, share links, reviews, kudos and public
pages. Every opt-in site is explicit and searchable. Stage 2 must switch
`ProbeProjectChangesUseCase` from the caller to the resolved owner for member projects, and the
co-author read page in stage 3 must not use `session.asOwner`. Every project-scoped datasource interface
(`ProjectEntityDatasource`, `ProjectDataDatasource`, `ServerWritingActivityRepository`,
`ProjectDao` project-level queries) changes its `userId: Long` parameter to `owner: OwnerId`,
with no default value. This is a mechanical sweep of around fifty call sites and forty mocks; its
point is that a missed site fails to compile rather than silently writing a shadow row under the
wrong user with the wrong encryption key.

`ProjectDao.updateSyncData` is keyed by `(user_id, name)` and moves to `(owner, uuid)`, since
the session key below no longer carries the owner's name.

### Sync sessions

`ProjectSyncKey(userId, projectDef)` becomes `ProjectSyncKey(projectId: ProjectId)`. One session
per project regardless of who holds it; same-install reclaim keeps working because install ids
are global UUIDs.

`beginProjectSync` refuses with `409 project_sync_in_progress` when a **co-author** asks while
the **owner's** account session is active, since that is the session that can rename or delete
the project; it re-checks after claiming the slot, which closes the race with the owner's
delete. The caller's own contention stays `400` as the protocol doc says. `validateSyncId` is
unchanged: checking the owner there would fail every co-author request while the owner has the
app open. The invariant that matters is the reverse one: the owner's `delete`, `rename` and
`move` refuse (`409`) while someone other than the owner holds the project session, so a
co-author mid-sync is not pulled out from under. The owner's own other devices behave as today;
their sessions are already frozen by the owner's account session.

`ProbeProjectChangesUseCase` and `ReviewRepository.withInternalSyncSession` use the same key.

### Account sync

`begin_sync` for user U returns owned projects plus every project U is a member of:

```kotlin
data class ApiProjectDefinition(
	val name: String,
	val uuid: ProjectId,
	val groupId: GroupId? = null,
	/** Set when the caller is a co-author; null for the caller's own projects. */
	val ownerId: Long? = null,
	/** Owner's pen name at the time of the response, for display only. */
	val ownerName: String? = null,
)
```

`groupId` is null for a shared project: shared projects sit at the top level of a co-author's
list and cannot be moved. The owner's placement is private to the owner. Group upsert and
delete are unchanged.

`create` with a `projectId` the caller is a member of answers `alreadyExisted = true` with that id
(idempotent, as for owned projects). `create` with a `projectId` that exists and the caller is not
a member of answers `403 not_member`, not `project_id_taken`. `create` by name does not match
shared projects: adoption by name is per account and only among owned projects.

`sync_probe` for a shared project works as for an owned one, with the owner's rows.

With `403 not_member` on `create`, the server no longer answers `project_id_taken` for another
account's id; that code is left only for an insert lost to a concurrent delete. Today's client
re-mints only on `project_id_taken`, so stage 5 must also re-mint on `403 not_member` from
`create` for a project without `ownerId`. The member listing and `create` apply the same
owner-active rule as `requireProjectAccess`: a soft-deleted or delisted owner's projects are not
listed, and `create` with their id answers `403`.

### Create, rename, delete, leave

| Request | Owner | Co-author | Non-member |
|---|---|---|---|
| `create` | as today | idempotent on the member project | `403 not_member` |
| `rename` | as today; `409` while a project session is live | `403` | `403` |
| `move` | as today; `409` while a project session is live | `403` | `403` |
| `delete` | as today; member rows are deleted, the tombstone is written for the owner only | **leave**: delete the member row, `200`; no tombstone | `403` |

A co-author's `delete` writes nothing to `deleted_project`. A tombstone for the caller would make
`create` answer `project_deleted` for ever after, and the client never re-mints on that code, so
the kept copy could never be uploaded as the co-author's own.

A co-author's `rename` and `move` answer `403` with `errorCode` `not_owner`
(`ApiErrorCode.NOT_OWNER`), a stranger's `403 not_member`; an unknown id stays `404` as today.
Leave also ends any project session the leaver holds on the project, since a session left behind
would refuse the owner's `delete`, `rename` and `move` until it expired, and the leaver could no
longer end it. Leave works whatever the owner's state, so a co-author who leaves while the owner
is soft-deleted stays gone if the owner is restored. A `delete` for an unknown id still
tombstones it for the caller as today, so a co-author's leave that reaches the server after the
owner deleted the project lists the id under that co-author's `deletedProjects`; the client
already drops queued deletes for ids the server does not list, and skips excluded projects in the
delete phase.

The owner's `delete` must not write any tombstone a member can see, so a member never finds the
project under `deletedProjects` (which deletes the local folder). The owner deleting the project,
the owner removing a member, a member leaving from another device, and the owner's account being
soft-deleted all look the same to a co-author's client: the project is listed in neither set, and
project routes answer `403` or `410`. The client handles all four with one rule (Parked projects).

### Website

- **Invite:** on the story page, owner only, a dialog that takes an email address and creates
  `project_invite`. The owner must have a pen name (it is what the co-author's client shows as
  the owner). The response and the pending-invite row are the same whether or not an account
  holds that address, and no mail is sent to an address without an account, so inviting confirms
  nothing. Invites share the login rate limit and refuse once members plus pending invites reach
  five. Invites are listed with the members on the same page, each with a revoke/remove control;
  the owner's own address and existing members are refused. Removing a member deletes the row.
- **Repository:** `ProjectMemberRepository` (stage 2) holds the invite, member and accept
  operations; the routes add authentication, rate limits and rendering. Addresses are stored
  trimmed and lower-cased.
  Re-inviting a pending address succeeds without a second row, even at the cap. Removing a
  member ends their project session, as leave does. `receivedInvites` does not filter invites
  from an owner who has since become inactive; the dashboard decides whether to show them.
- **Accept/decline:** a dashboard notice for invites whose `email` matches the session account.
  Accepting requires a pen name (set inline if missing), inserts `project_member` and deletes the
  invite. Declining deletes the invite.
- **Dashboard:** `getProjectsWithSyncDate(userId)` unions member projects, labelled "shared by
  {owner}". Co-authors count towards nothing in the owner's project limits.
- **Story page for a co-author:** `findProjectByUrlSegment` resolves member projects, and
  `/story/{segment}` for a member renders a reduced page: title, owner, sync state, the *Read*
  link, and a *Leave* control. Every other control on the story page (publish, kudos, share
  links, review requests, rename, delete, invite) stays owner-only and is simply absent, so the
  existing `session.userId` call sites behind those controls do not change. Kudos: `giverId` is
  also refused when it is a member of the project.
- **Share links and review requests:** unchanged, owner only.
- **Admin:** project counts in the admin user list stay owner-based; the project list shows the
  member count.
- Notification mail to an invitee with an account (`email/project-invite.mustache`) is off for
  the first pass; the dashboard notice is enough.

### Tests

- Membership resolution: owner, member, non-member (`403`), unknown uuid (`410`), soft-deleted
  owner (`403` for members), inactive member.
- Entity round trip by a co-author decrypts for the owner, and the reverse.
- Two users cannot hold a project session at once; same-install reclaim still works.
- Owner `delete`, `rename` and `move` while a member's project session is live are refused.
- Owner `delete` leaves no tombstone visible to the member; the project is in neither list.
- Co-author `delete` leaves, writes no tombstone; a later `create` with that id by the
  ex-member (after the owner deleted it) succeeds. `rename` and `move` are `403`.
- `create` by a co-author with a same-named own project does not adopt the shared one.
- `create` with a uuid the caller is not a member of is `403`, not `project_id_taken`.
- Sixth invite is refused.
- Existing `ProjectRoutes`/`ProjectsRoutes` tests pass unchanged with `{userId}` = owner.

## Phase 2: Clients

### What the client learns

`project.toml` (`Info`) gains three fields:

```kotlin
val syncEnabled: Boolean = true
val ownerId: Long? = null
val ownerName: String? = null
```

They live in `project.toml`, not the account `sync.json`, because every synchronous consumer of
a project (project list, editor, Android widget worker, Wear, MCP sync operations, backups)
already reads `project.toml`, and because the account file is replaced with defaults when it is
missing or corrupt, which must never flip a project back into sync or make it adoptable.
`ownerId` and `ownerName` are written when account sync creates the local folder for a shared
project; `ownerName` is refreshed on every account sync. They are not on `ProjectDef`, so
data-class equality and the project list are untouched; `ProjectsRepository` exposes them.

### Per-project sync toggle

The project settings screen shows a *Sync this project* switch, only when `serverSettings` is
set. The project card shows a cloud-off marker for an excluded project.

There is one enforcement point: `ProjectsRepository.isSyncEnabled(projectDef)`, which reads
`Info.syncEnabled`. `SyncAccountUseCase` filters excluded projects out before any phase runs,
the delete phase included, exactly as it already filters projects that are open; the change
probe, background sync, the Android note worker, the MCP sync operation and Wear all check the
same function before touching a project; the editor's sync action is hidden. Nothing else
changes: entities are still marked dirty in the project journal, so turning sync back on resumes
with the normal conflict detection, like a device that has not synced in a while.

Turning sync **off** states two consequences in the dialog:

- Other devices and the server are unaffected. The switch means "not from this device".
- Deleting an excluded project locally does not delete it on the server, and if the server still
  holds it, the next account sync re-creates it locally. The delete dialog for an excluded project
  that has a sync baseline therefore offers *Also delete on the server*.

Turning sync **on**:

- If `ownerId` is set, the project belonged to someone else. The dialog explains that turning
  sync on uploads it as the user's own project, severed from the original. On confirm, `ownerId`
  and `ownerName` are cleared, a fresh `ProjectId` is minted (the old one is held by the owner),
  and the normal create path uploads everything.
- Otherwise, if the account `sync.json` lists the id under `deletedProjects`, the project was
  deleted on the server while excluded here; a fresh id is minted so the create path does not
  answer `project_deleted`.
- Otherwise sync resumes. If the project was parked by the server (below) rather than by hand,
  `verifyAllEntities` is set first, as `markProjectRestored` does today.

### Leave vs delete

`ProjectsService.deleteProject` on a shared project (`ownerId != null`) shows a *Leave project*
dialog instead: it explains that the owner's copy is untouched, sends `/delete` (which the
server treats as leave), and sets `syncEnabled = false`. The folder is kept. There is no second
flow: a user who wants the copy gone deletes it afterwards as an ordinary excluded project.

A co-author cannot rename or move a shared project in the UI, and `ProjectAttributeReconciler`
never emits `RenameOnServer` or `MoveOnServer` for one: a folder renamed outside the app is
renamed back with `RenameLocally` to the server's name, and the server's null `groupId` is
applied locally.

### Name collisions and adoption

Account sync adopts a server project onto a same-named local folder with no id match
(`ClientAccountSynchronizer`, around the `adopt` step). For a shared project this would merge an
unrelated local story into it through a full conflict check. Rules:

- A project with `ownerId != null` is never adopted by name and never adopts an unmarked folder.
- When a shared project's name collides with a local folder (the check is against the
  filesystem, not only the loaded project list, so an unmarked folder cannot wedge creation), the
  shared project is skipped for that session with the existing "name already taken" log line.
  The co-author renames their own project and the next sync brings the shared one down. No
  folder-name rule, no reconciler special case.

### Parked projects

A local project with `ownerId != null` that the server no longer lists (not in `projects`, not in
`deletedProjects`) is **parked**: `syncEnabled` is set to false, its `ProjectBaseline` is
dropped, and the sync log records `sync_log_account_project_parked` ("{name} is no longer shared
with you. Your copy is kept on this device and no longer syncs. Turn sync on in its settings to
upload it as your own project."). The project journal is untouched: a rebuilt journal infers
deletions from id gaps and marks nothing dirty, so clearing it would let a partial copy delete the
owner's entities on re-invite and let the co-author's unsynced edits be downloaded over.

A project with `ownerId != null` never takes the unknown-project create path, whatever the
response. This single rule covers removal, the owner deleting the project, the owner's account
being soft-deleted (reversible for the retention window), the owner losing whitelist access, and
a lost account `sync.json`. Without it, any of those would end in `project_id_taken`, a re-mint,
and a forked upload of the owner's book under the co-author's account.

If the project is listed again later (re-invite), the folder is still there with the same id:
account sync re-enables it with `verifyAllEntities` set and the normal full comparison brings the
two copies back together.

`SyncJournal.buildSyncData`, which rebuilds a lost project journal, sets `verifyAllEntities` on
the result regardless of co-authoring. A rebuilt journal is a guess and the next sync should
check every entity.

### Tests

- Account sync lists a shared project, creates it locally at the top level with `ownerId` and
  `ownerName` in `project.toml`.
- Same-named local project is left alone; the shared one is skipped with the name-taken log.
- An excluded project is skipped by account sync, including the delete phase, the probe and
  background sync; its local rename or delete sends nothing; a server rename of it is not applied.
- Turning sync on for a project with `ownerId` asks, clears the owner, re-mints, creates.
- Turning sync on for an id under `deletedProjects` re-mints.
- A shared project missing from the response is parked: folder and journal intact, baseline
  dropped, logged, nothing uploaded; a `403` from a project route does the same.
- A parked project listed again resumes with `verifyAllEntities`.
- Leave keeps the folder excluded and sends `/delete`.
- Reconciler never emits `RenameOnServer` or `MoveOnServer` for a shared project.
- `buildSyncData` sets `verifyAllEntities`.
- Component tests through the real `TestComponentContext` harness.

## Phase 3: Writing stats by author

`DeviceLog` gains `authorId: Long? = null` and `authorName: String? = null`. The server ignores
whatever the client sends in them: on POST it stores `author_id` and `author_name` (the caller's
pen name) on the row, and on GET it fills both in each slot. The client writes the returned
values into its local copy of every slot, so `{project}/scenes/.activity/{deviceId}.toml`
carries the author and stats work offline. A slot with a null author is read as the owner's
(legacy data, or the own slot before its first round trip).

Two accounts signed in on the same install share a `deviceId`. The server keys slots by
`(owner, project, author_id, device_id)`, which needs the Phase 3 migration described under
Schema (`author_id` backfilled and `NOT NULL`), and the client refuses to merge a returned slot
for its own `deviceId` whose `author_id` is not the signed-in account. Nothing is lost; the other
account's sessions simply do not appear in this account's local file.

`StatisticsService` groups by `authorId` when more than one is present and displays the stored
`authorName`, so "Words by Device" becomes "Words by Author" with devices nested, and
`stats.sessions` in the MCP tools gains the author. Because the name is plain text on the row and
in the local file, removing an author, or the author deleting their account, changes nothing in
the stats of anyone who still has the project. Their devices simply stop adding sessions.

The project card shows "Shared by {ownerName}" for a co-author, read from `project.toml`. The
owner's in-app view of who is on the project is deferred; the website lists members.

## Work plan

One branch, `4_0/coauthoring`, one pull request into `4.0.0` at the end. The work runs in the
stages below, in order, each landing as a series of checkpoint commits on the branch:

1. **Server: membership resolution and owner-scoped storage.** Schema, `requireProjectAccess`
   with `403 not_member`, `OwnerId` through every project-scoped datasource, `ProjectSyncKey` by
   uuid, `updateSyncData` by uuid, session rules, explicit child deletes. No routes to add a
   member yet, so behaviour is unchanged for every existing client. Landed 2026-11-08 as
   `fbec858d3`, `f53c79f8c`, `00ce71f20`, `25a3fe262`, `b09a7b110`.
2. **Server: account sync and leave.** `ownerId`/`ownerName` in the response, member projects
   listed at the top level, `/delete` as leave without tombstone, `403` for member `rename` and
   `move`, `403 not_member` on `create`, invite cap, probe resolves the owner for member projects.
   Tests deferred from stage 1: owner delete leaves no member-visible tombstone, co-author
   `delete` is leave, `create` answers `403 not_member`, sixth invite refused. Landed
   2026-11-08 as `029b7529d`, `ffe2f958a`, `167e36a46`, `c05cbacd1`, `54f5ccd83`.
3. **Server: website.** Invite, accept, members list with remove, dashboard label, reduced story
   page for co-authors with *Leave*.
4. **Clients: per-project sync toggle.** `syncEnabled` in `project.toml`, the settings switch,
   the card marker, the single enforcement point and its callers, the delete dialog's server
   option, the re-mint on turn-on for a tombstoned id. Independent of co-authoring.
5. **Clients: owner awareness.** `ownerId`/`ownerName` in `project.toml`, leave dialog,
   no-adoption and filesystem collision check, reconciler rules, parked projects, the
   "upload as my own" turn-on path, `buildSyncData` hardening.
6. **Writing stats by author.** Server stamps, slot key, client merge rule, stats grouping, card
   label.
7. **Docs:** `SYNCING-PROTOCOL.md` sections for membership, `403 not_member`, leave and the
   session rules; `ARCHITECTURE.md` on `project.toml`'s sync fields and parked projects.

Protocol version 4 is the deployment gate (Decision 7), and server and clients ship together in
4.0.0, so nothing in the order above is load-bearing for deployment; it is the order that keeps
each stage testable against the one before it.

### How the work runs

Implementation sessions run on Opus 5.5 at high effort. Each stage is built as a series of
checkpoint commits, one per coherent step (schema, resolution, sessions, and so on). After every
checkpoint the diff gets a code review, and every finding is addressed before the next step
starts. The pull request is opened once the last stage has been reviewed clean.

All web front-end work (the invite dialog, member list, dashboard notice, story page controls)
follows [WEB-DESIGN-SYSTEM.md](WEB-DESIGN-SYSTEM.md) and is done with the `frontend-design`
skill loaded, so new partials match the existing Writer's Desk templates.

## Later, optional

- Share links for co-authors: `project_access` is keyed by project already, but needs a
  `created_by` column and an owner-or-member check on every access route.
- Review requests for co-authors: split requester (`review_request.user_id`, snapshot encryption,
  invite mail) from owner (reading scenes, minting drafts, internal sync session) in
  `ReviewRepository` and the review frontend; decide what happens to a leaver's encrypted
  snapshots.
- Co-author placement of shared projects in their own groups (`project_member.group_id`, member
  `move`, reconciler moves both ways).
- Entity attribution: `story_entity.updated_by`, `X-Updated-By` on `409`, "Remote: Alice" in the
  conflict tabs.
- In-app members list for the owner (`GET …/members`).
- Soft "claimed" marker on a scene ("Alice is working on this") as project metadata outside the
  entity, to reduce whole-scene conflicts.
- Roles (read-only co-author) on `project_member`.
- Invite notification emails.
- Per-project cipher secret, if owner-scoped encryption ever needs to be decoupled from the owner
  account (for example, transferring ownership).

## Open questions

- Should co-authors see the project's writing stats on the website story page, or only in-app?
- Should the owner be able to purge a removed co-author's writing-activity slots?
