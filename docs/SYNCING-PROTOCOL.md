# Synchronization Protocol

This doc will try to give a breif (_as possible_) overview of the client/server synchronization
protocol Hammer
uses.

## Two levels of syncing

**Account Sync:** This synchronizes what projects the Account has, creating, deleting, or renaming
just the top level directories on the client

**Project Sync:** This synchronizes an individual project and all of it's Entities

---

## Account Sync Protocol

Before any project level syncing is done, we must first do an Account level sync.

This will handle creating, deleting, and renaming projects, to bring the client and server into
parity with each other.
Additionally, it will find or create a `projectId` for the client's local projects. These are the
key to being able to sync a local project with the server.

Any given user account may only have one sync in progress at a time. Attempting to start a sync when
one is already in progress will result in a failure to begin the sync.

```mermaid
sequenceDiagram
	participant Client as Client
	participant Server as Server

	rect rgb(1, 59, 15)
		Client ->> Server: GET /projects/{userId}/begin_sync
		activate Server
		Note right of Client: bearer token
		Server -->> Client: 200 OK (Sync Began)
		deactivate Server
		activate Client
		Note left of Server: syncId<br/>projects<br/>deletedProjects
		alt Sync already in progress
			Server -x Client: 400 Bad Request (sync ends here)
		end
	end
	rect rgb(11, 0, 74)
		loop Rename Projects
			Client ->> Server: GET /api/projects/{userId}/rename
			deactivate Client
			activate Server
			Note right of Client: bearer token <br/> syncId <br/> projectId <br/> projectName
			Server -->> Client: 200 OK (Rename successful)
			deactivate Server
			activate Client
			alt Rename fails
				Server -->> Client: 4XX Bad Request
			end
		end
	end

	rect rgb(74, 0, 9)
		loop Delete Projects
			Client ->> Server: GET /api/projects/{userId}/delete
			deactivate Client
			activate Server
			Note right of Client: bearer token <br/> syncId <br/> projectId
			Server -->> Client: 200 OK (Delete successful)
			deactivate Server
			activate Client
			alt Delete fails
				Server -->> Client: 4XX Bad Request
			end
		end
	end

	rect rgb(49, 0, 74)
		loop Create Projects
			Client ->> Server: GET /api/projects/{userId}/{projectName}/create
			deactivate Client
			activate Server
			Note right of Client: bearer token <br/> syncId <br/> projectName
			Server -->> Client: 200 OK (projectId)
			deactivate Server
			activate Client
			alt Creation fails
				Server -->> Client: 4XX Bad Request
			end
		end
	end

	rect rgb(0, 15, 6)
		Client ->> Server: GET /api/projects/{userId}/end_sync
		deactivate Client
		activate Server
		Note right of Client: bearer token <br/> syncId
		Server -x Client: 200 OK (Sync completed)
		deactivate Server
	end

```

---

## Project Sync Protocol

## Goal

The goal of this protocol is to synchronize the various `Entities` on the client to the server.

There is no file history as in a true version control system such as `git`. This is instead a simpler synchronization system, yet still smart enough to detect `conflicts`, and prevent edits on multiple devices from overwriting each other on accident.

As such, there is very little book keeping data, and none of it is actually required. When all actors are fully synchronized, they all contain the full set of data. Thus if the server were to die and lose all of its data, it wouldn't matter. Every client would contain everything necessary to
setup on a new server.

Further more, the protocol is fully fault tolerant. It may fail at any step along the way, and the state of the client and server will remain entirely valid, although not entirely synchronized.

## Network Protocol (overview)

This is largely a client driven synchronization process.

### SyncIDs
The client calls `begin_sync` to get a valid `syncID`. This `syncID` is provided to all subsequent calls, and is terminated with a call to `end_sync`.

There can be only one valid `syncID` per project at any given time. This prevents race conditions with two clients syncing the same project at the same time.

You may however have `syncID`s for multiple different projects simultaneously.

These are Project level `syncID`s. Account syncing use separate Account level `syncID`s. There may only be one valid Account level `syncID` at a time, and if there is a valid Account `syncID`, then no Project level `syncID`s are allowed to be created. The Account level sync must finish before any Project level syncs may begin.

### Entity Update Sequence
The server will inspect the provided ClientState, and then return a sequence of Entity IDs. Those and only those IDs should be synchronized by the client, and in that order.

### Remote Entity Deletion
The last step befor individual Entity synchronization can begin is having the Client notify the server of any locally deleted Entities.

```mermaid
sequenceDiagram
    participant Client
    participant Server

    Client->>Server: POST /project/$userId/$projectName/begin_sync
	activate Server
	Note right of Client: ProjectID<br/>ClientState

	Server -->> Client: 200 OK (Sync Began)
	deactivate Server
	activate Client
	Note left of Server: ProjectSynchronizationBegan

    rect rgb(74, 0, 9)
        loop Delete Entities
            Client->>Server: GET /project/$userId/$projectName/delete_entity/$id
            deactivate Client
            activate Server
            
            Server -->> Client: 200 OK
            deactivate Server
            activate Client
            Note left of Server: DeleteIdsResponse
        end
    end

    rect rgb(11, 0, 74)
        loop Transfer Entities
        Note right of Client: See breakout section for details
			Client ->> Server: [various]
			Server ->> Client: [various]
        end
    end

    Client->>Server: POST /project/$userId/$projectName/end_sync
	deactivate Client
	activate Server
	Note right of Client: ProjectID<br/>SyncId
	Server -->> Client: 200 OK (Sync Terminated)
	deactivate Server
	activate Client
```

## Network Protocol (Entity Transfer)
The Client now attempts to sync each ID provided in the server in the `Entity Update Sequence` in the order provided.

It will now either upload or download each ID depending on what it infers from the combined Client and Server state that has been transferred so far.

### Download
The client has determined that it needs to download the Server's copy of an Entity. This is either because the client is simply missing the Entity, or it has determined that the server has a newer version and it wants to overwrite the local client copy with the server copy.
```mermaid
sequenceDiagram
    participant Client
    participant Server

    Client->>Server: GET /project/$userId/$projectName/download_entity/$entityId
	activate Server

	Server -->> Client: 200 OK (Sync Began)
	deactivate Server
	activate Client
	Note left of Server: LoadEntityResponse
```

#### Stale Hash Detection and Self-Healing

During a download, the server compares its cached entity hash with a freshly computed hash. If they don't match (which
can occur due to schema evolution, such as adding new fields to entities), the server returns a 412 Precondition Failed
response with details about the mismatch.

The client handles this by force uploading its local copy to "heal" the server's stale cache. This self-healing protocol
ensures that schema changes don't cause persistent sync issues.

```mermaid
sequenceDiagram
    participant Client
    participant Server

    Client->>Server: GET /project/$userId/$projectName/download_entity/$entityId
	activate Server
	Note over Server: Cached hash != Computed hash

	Server -->> Client: 412 Precondition Failed
	deactivate Server
	activate Client
	Note left of Server: StaleHashResponse<br/>{cachedHash, computedHash}

	Note right of Client: Client detects stale cache<br/>and initiates healing
	Client->>Server: POST /project/$userId/$projectName/upload_entity/$entityId?force=true
	deactivate Client
	activate Server
	Note right of Client: Force upload to heal server cache

	Server -->> Client: 200 OK
	deactivate Server
	activate Client
	Note left of Server: SaveEntityResponse<br/>Server cache now healed
```

This mechanism is transparent to the user and ensures data consistency across schema migrations without requiring manual
intervention or database migrations.

### Upload
The client has determined that it needs to upload the local Client copy of an Entity. This is either because the server is missing the entity, or the client has a dirty copy that needs to be synchronized.

#### No conflict
In the nominal case, the server will accept the incoming entity, and simply overwrite the Server's own copy with it. The server knows this is safe to do so because it compares the Server copy's hash, with the provided `original hash`. If they match, the Server knows that the Client was editing the same copy which the server will now replace.
```mermaid
sequenceDiagram
    participant Client
    participant Server

    Client->>Server: POST /project/$userId/$projectName/upload_entity/$entityId
	activate Server
	Note right of Client: X-Entity-Hash = {original hash} <br /> ApiProjectEntity

	Server -->> Client: 200 OK
	deactivate Server
	activate Client
	Note left of Server: SaveEntityResponse
```

#### Conflict detected
In the case where the Sever and Client's `original hash` do no match, there is a conflict.

The server infers from this that the client was editing a different version of the Entity than what the server now has. This is probably because a different client uploaded an independent edit of the Entity.

The server will respond with it's copy of the Entity and require the Client to resolve the conflict by resubmitting the upload with `force=true` set.
```mermaid
sequenceDiagram
    participant Client
    participant Server

    Client->>Server: POST /project/$userId/$projectName/upload_entity/$entityId
	activate Server
	Note right of Client: X-Entity-Hash = {original hash} <br /> ApiProjectEntity

	Server -->> Client: 409 Conflict
	deactivate Server
	activate Client
	Note left of Server: ApiProjectEntity

	Note right of Client: {client now helps the user resolve the conflict}
	Client->>Server: POST /project/$userId/$projectName/upload_entity/$entityId?force=true
	deactivate Client
	activate Server
	Note right of Client: X-Entity-Hash = {original hash} <br /> ApiProjectEntity {resolved entity}

	Server -->> Client: 200 OK
	deactivate Server
	activate Client
	Note left of Server: SaveEntityResponse
```
Note that the resolved `ApiProjectEntity` in the `force` request does not have to be exclusively the Client's or Server's copy, it can be a merging between the two that the client helped the user create.

## Project Data Sync (non-entity blob)

In addition to entity sync, each project has a single per-project blob holding user-authored settings (author name, theme colors, word-count goal). This blob is synced as its own phase, inserted into the pipeline immediately *before* entity transfer so the project's identity is settled before any entity churn.

The blob is a structured object — see `ProjectData` in the `base` module — but is treated as a single unit at the sync layer. Conflict detection is hash-based, mirroring entity sync: the client persists the `lastSyncedHash` it most recently agreed on with the server and replays it on the next upload.

```mermaid
sequenceDiagram
    participant Client
    participant Server

    Client->>Server: GET /project/$userId/$projectName/project_data
    activate Server
    Server -->> Client: 200 ProjectDataDto OR 204 No Content
    deactivate Server

    alt Local clean since last sync, server changed
        Note right of Client: Fast-forward — adopt server state, save lastSyncedHash
    else Both sides changed
        Client->>Server: POST /project_data
        Note right of Client: { data, originalHash = lastSyncedHash }
        activate Server
        alt Hashes match
            Server -->> Client: 200 ProjectDataDto
        else Conflict
            Server -->> Client: 409 ProjectDataConflictDto
            Note right of Client: User resolves per-field
            Client->>Server: POST /project_data
            Note right of Client: { data = resolved, originalHash = serverHash }
            Server -->> Client: 200 ProjectDataDto
        end
        deactivate Server
    end
```

Unlike writing-activity sync (which swallows errors and continues), a non-conflict failure on the project-data phase fails the whole sync — the data is user-authored and silent loss is unacceptable.

## Writing Activity Sync (per-device slots)

After entity transfer, the client syncs **writing activity** — an auxiliary record of writing sessions used for stats and observability (words written, session start/end, sealed flag). Each device tracks its own sessions locally; the project's full activity is the union of every device's log, keyed by `deviceId`.

The model is intentionally conflict-free by construction: **only the owning device ever writes its own slot**. When the client pulls the server's view, it wholesale-overwrites its local copies of *foreign* device slots, and merges only its *own* slot before pushing it back. There is no hash-based conflict detection like entity sync or project_data — each device is the sole writer of its slot, so there is nothing to conflict on across devices.

For the device's own slot, `mergeOwnSlotSessions` (see [SessionMerge.kt](common/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/common/data/writingactivity/SessionMerge.kt)) unions sessions by `startedAt`. On collision it keeps the higher `wordsWritten`, the later `endedAt`, and `sealed = local || remote` (sealing is one-way).

```mermaid
sequenceDiagram
    participant Client
    participant Server

    Client->>Server: GET /api/project/$userId/$projectName/writing_activity
    activate Server
    Server -->> Client: 200 WritingActivityResponse
    deactivate Server
    Note left of Server: { deviceId → DeviceLog }

    Note right of Client: Overwrite local copies of<br/>foreign device slots.<br/>Merge own slot with server's copy.

    Client->>Server: POST /api/project/$userId/$projectName/writing_activity/$deviceId
    activate Server
    Note right of Client: DeviceLog (own slot, merged)
    Server -->> Client: 200 OK
    deactivate Server
```

Endpoints (both take `projectId` as a query parameter):

- `GET  /api/project/{userId}/{projectName}/writing_activity` → `WritingActivityResponse` (`Map<deviceId, DeviceLog>`)
- `POST /api/project/{userId}/{projectName}/writing_activity/{deviceId}` body: `DeviceLog`

Data shapes (`WritingSession`, `DeviceLog`, `WritingActivityResponse`) live in [WritingSession.kt](base/src/commonMain/kotlin/com/darkrockstudios/apps/hammer/base/http/writingactivity/WritingSession.kt).

Both GET and POST failures are logged and swallowed: the writing-activity phase never fails the surrounding project sync. Activity data is auxiliary observability — a transient network or server error must not block the user's actual content from syncing. Local state is left untouched on a failed GET, so the next sync simply tries again.

## Client Operations Sequence
Beyond the network side of the Protocol, the Client is doing a bit of work to ensure data loss is not possible, and to work out what should be done with the minimal book keeping data it has.

```mermaid
flowchart TD
    A[PrepareForSync] --> B[FetchLocalData]
    B --> C[FetchServerData]
    C --> D[CollateIds]
    D --> E[Backup]
    E --> F[IdConflictResolution]
    F --> P[ProjectDataSync]
    P --> G[EntityDelete]
    G --> H[EntityTransfer]
    H --> W[WritingActivitySync]
    W --> I[FinalizeSync]
```

## Terminology

**Entity**
any individual block of data. Each entity is given a unique ID. Examples include:

- Scene
- Scene Draft
- Timeline Event
- Encyclopedia Entry
- Note

**Entity ID**
Every Entity is given an Entity ID, which is a unique, monotonically incrementing
integer, with the first valid ID being 1

**Sync ID**
This is a UUID generated by the server and passed back to the client identifying a
particular syncing session to a particular client. The server will only allow one syncing session per
account at a time to prevent race conditions.

**Entity Update Sequence**
A list of Entity IDs in a particular order determined by the server.
The client will update these IDs in the provided order. The server will leave out IDs of Entities
that do not need synchronization.

**Re-ID**
The process of taking a client side Entity and issuing it a new ID, changing any
references to that ID in the process.

**Conflicts**
The same file that has been edited in different ways on different devices, must allow the user to resolve the conflict in order to bring them back into sync with each other.

**Dirty Entity** When a client edits a local Entity, it adds the **Entity ID** to a "dirty list"
together with that Entity's **conflict baseline** — the hash the server last confirmed for it. At
sync time the client sends this baseline as the upload's `original hash`; if another client edited
the same Entity and synced first, the server's hash no longer matches the baseline and the conflict
is detected.

The baseline is the hash recorded the last time the client and server agreed on the Entity (on a
successful upload or download), **not** a hash re-derived from the current local content at edit
time. Re-deriving it is unsafe: an Entity's hash includes fields such as `lastEdited` that the
autosave can stamp independently of a real content change, so a freshly computed baseline can
disagree with the server even when nothing meaningful changed — forging a phantom conflict. This is
the same locked-baseline scheme `project_data` uses with its `lastSyncedHash`.

A baseline exists for every Entity the client and server have agreed on, set on each successful
transfer. If a baseline is absent the server cannot conflict-check and accepts the upload, so a
project whose sync data predates this scheme backfills a baseline for every in-sync Entity on its
first sync (the local hash, which equals the server's for an agreed Entity) before any upload relies
on it.
