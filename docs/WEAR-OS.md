# Hammer for Wear OS

_Design doc. Status: phases 1 to 3 implemented on the `wear-app` branch (sync extraction,
pairing endpoint, watch app with pairing, sign-in, project subscriptions, and background sync).
Phase 4 (capture) is complete: the guards, the capture activity, the expedited sync, the tile and
the complication have all landed. Capture, the tile and the complication are smoke tested end to
end on a Wear OS emulator against a local server, not yet on a watch._

A standalone Wear OS client for capturing notes and ideas while away from a desk, and for
listening to scenes read aloud. It reuses the `common` data and sync layers unchanged and
presents a watch-native UI. The watch is a full, independent sync client: after a one-time
pairing it talks to the sync server on its own and never depends on the phone.

## Goals

- Pick a project and add a note to it in one or two taps, offline, with voice input.
- Capture a Story Idea (no project needed) just as quickly.
- Everything captured offline syncs on its own the next time the watch has a network.
- Listen to a scene read aloud through Bluetooth headphones, with position remembered.
- Reuse `common` for all data, sync, and domain logic. No second implementation of anything.

## Non-goals

- Editing scenes, encyclopedia entries, timelines, or anything beyond notes and ideas.
- Conflict resolution UI on the watch. A project that needs resolution is shown as such and
  resolved on a phone or desktop.
- Phone-relay transport. The watch does not send notes through the phone app.
- iOS or non-Wear watches.

## Architecture decision: independent sync client

Two shapes were considered.

**Remote control for the phone.** The watch writes notes as Wearable Data Layer items, and the
phone app receives them and runs the existing `AddNoteWorker`. Cheapest, but the watch is
useless without the phone app installed and reachable, and it can never grow a read-aloud
feature because no content lives on the wrist.

**Independent sync client (chosen).** The watch runs `common`, holds its own copy of the
projects it cares about, and syncs directly with the server. The protocol already treats every
device symmetrically, and offline ID collisions from a third device are resolved by
`IdConflictResolutionOperation` exactly as they are for phone plus desktop today. The phone is
involved only during pairing, to hand the watch a session.

## Module layout

```
wear/   Wear OS application module. Depends on :common only, never on :composeUi.
```

- `applicationId` is the phone app's (`com.darkrockstudios.apps.hammer.android`) so Google Play
  lists it under the same app. Version codes must be unique across the listing, so the wear
  module derives its `versionCode` from `getVersionCode(...)` plus a fixed offset.
- Manifest declares `android.hardware.type.watch` and
  `com.google.android.wearable.standalone = true`.
- UI: Wear Compose Material 3 (`androidx.wear.compose:compose-material3`), Wear Tiles with
  ProtoLayout, and the complication data source library. Pin versions in
  `gradle/libs.versions.toml` at implementation time; the `jetpack-compose-m3` skill covers the
  component vocabulary.
- `play-services-wearable` is needed only for the pairing handshake.
- Not published to F-Droid initially.

## Koin graph

The watch has its own `Application` that calls `startKoin` with `mainModule`,
`appModule(scope)`, and a `wearModule`. `mainModule` hard-includes the Android `platformModule`,
which also wires phone-only services (share service, backup manager, focus mode, platform
settings component). The module is not split: every one of those is a lazy binding resolved only
by `composeUi` screens the watch never shows, and `PlatformSpellCheckerFactory` constructs inertly
and degrades to no checkers. `wearModule` adds only watch pieces (Data Layer client, pairing use
case). The encrypted shared-prefs auth token store works unchanged on Wear.

`FileLogger` and the global crash handler live in `common/src/androidMain` so both applications
share them.

On the phone, everything that touches Google Play services (the pairing listener, dialog,
responder, and `wearPairingModule`) lives in `android/src/gms`, added to the source set only for
non-F-Droid builds. `src/nogms` supplies an empty `playServicesModules` list, and the F-Droid
manifest simply omits the pairing service and activity.

Backups: `BackupOperation` runs before every project sync. Projects on the watch are small and
few, so keep the behaviour rather than special-casing it; revisit if storage becomes a problem.

## Sync orchestration extraction (upstream refactor)

Per-project sync is already usable outside the UI (`AddNoteWorker` proves it). The
"sync everything" flow is not: account sync, the change probe, parallel project syncs, ideas,
reauth, and idea-conflict callbacks live inside `ProjectsListComponent.syncProjects`, mixed with
dialog status updates.

Move that orchestration into a `common` use case (working name `SyncAccountUseCase`) with:

- an explicit set of projects to sync (the watch passes its subscribed projects; the phone and
  desktop pass everything),
- progress and log callbacks,
- `onUnauthorized` and `onIdeaConflict` hooks,
- a result describing per-project outcomes.

`ProjectsListComponent` becomes a thin caller that maps results onto its dialog state. This is
a pure refactor with no behaviour change and lands first, on its own. It also makes a
WorkManager background sync on the phone trivial later.

## Pairing and authentication

Server tokens are stored per `(userId, installId)`, and a refresh replaces the row for that
install. The watch therefore must own its own install ID and its own token; copying the phone's
would make the two devices race each other on refresh. The phone stores no password, so it
cannot log in on the watch's behalf. A new server endpoint is needed:

```
POST /api/account/{userId}/pair_install
  Authorization: Bearer <phone token>
  form: installId=<watch install id>
  200: Token for the new install
```

It is `createToken(userId, installId)` behind bearer auth. A device that holds a session can
already read and write everything, so minting a sibling session grants no new power. Rate limit
it and log it like login.

Handshake, all over the Wearable Data Layer (app-private, carried on the encrypted Bluetooth
link):

1. Watch generates its install ID via `GlobalSettingsStore.ensureInstallId()` and sends a
   `pair_request` message carrying it.
2. Phone shows a confirmation, calls `pair_install`, and replies with a `pair_response`
   message containing the `ServerSettings` payload (url, ssl, email, userId, bearer, refresh)
   for the watch's install.
3. Watch stores the settings through the normal `GlobalSettingsStore` path and runs its first
   account sync.

Use `MessageClient`, not persisted `DataItem`s, so the token is never left lying in the Data
Layer store. If the watch is unreachable the phone simply reports that and the user retries.

Client side, add `AccountUseCase.pairInstall(installId)` and the matching `ServerAccountApi`
call. The watch keeps a manual sign-in screen (server URL, email, password via `RemoteInput`)
as a fallback for a watch with no Hammer phone app.

Sign out on the watch wipes local settings and projects; the server token expires on its own.
The existing audit finding that refresh tokens never expire applies to watch tokens too and is
tracked separately.

### Local network access

Android 17 blocks an app from reaching local network addresses until it holds
`ACCESS_LOCAL_NETWORK`, and a self-hosted server is usually on the LAN. A blocked connection does not
fail fast; it times out as a generic connection error, so the permission is settled before
connecting rather than diagnosed after. The watch asks only when the server needs it: the address is
resolved and checked against the private, loopback, link-local, IPv6 unique local, and carrier-grade
NAT ranges (the last covers mesh VPNs such as Tailscale). A name that will not resolve is judged by
how it looks (`.local`, `.lan`, `.home`, `.internal`, a single label). A server like hammer.ink never
triggers the prompt.

- **Manual sign in** checks before the login request, since the login is the first thing that
  touches the server.
- **Pairing** hands the watch its settings and lands on the projects screen, which holds its
  app-open sync until the permission is answered. A refusal leaves the sync held with an
  explanation and an Allow button.

## Projects on the watch

Account sync creates a local directory for every server project, with metadata and a server
project ID but no entities. Syncing content is opt-in per project:

- The watch keeps its subscribed projects in a wear-owned DataStore (`WearPrefsDatasource`, the
  only writer), keyed by server `ProjectId` so a rename does not drop a subscription. It is not a
  `GlobalSettings` field because that file is shared by every platform.
- Only subscribed projects are passed to the sync use case. The change probe keeps repeat
  syncs to a single request when nothing changed.
- A subscribed project's first sync pulls every entity, one request each. That is acceptable for
  a novel and is shown with progress.

**Notes can only be added to a subscribed, synced project.** An unsynced project has an empty
`IdAllocator`, so a note created there would take ID 1 and collide on every sync. Unsubscribed
projects appear in the picker greyed out with a "sync to enable" action. Ideas have no such
restriction because they are UUID keyed.

## Capture flow

- **Tile**: shows the last project captured to as a button, a "Note" button, a "Story idea"
  button, and the count of unsynced items. Tapping the project opens capture on its picker, which
  is the only way to change where a note goes without going into the app. The project button lives
  in the main slot because the title slot does not receive taps. A tile launch action carrying an
  `AndroidBooleanExtra` is silently dropped along with the whole action, so flags travel as
  strings. Tiles cannot take input; the buttons launch the activity through a
  `LaunchAction`, which is why `CaptureActivity` is exported. Built with ProtoLayout Material 3.
- **Complication**: SHORT_TEXT and MONOCHROMATIC_IMAGE, showing the unsynced count (or "Note"
  when there is nothing waiting) and tapping through to capture.
- Both are cached by the system and re-read only on their own slow cadence, so anything that
  changes the pending count calls `CaptureSurfaceUpdater`: a capture, and every finished sync.
  Without it the watch face keeps showing a stale count.
- **Activity**: opens into `RemoteInput` (voice first, keyboard fallback) as soon as it knows a
  capture has somewhere to go, never before: prompting first would take a whole dictated note and
  then throw it away on the "no project" screen. The project defaults to the last one captured to
  and is changed from a list, never a required step.
  Confirm saves through `NotesRepository.createNote` inside `temporaryProjectTask`, exactly
  like `AddNoteWorker`, then enqueues a sync. Saving runs in the app scope, so a capture still
  lands if the watch drops the activity mid-save.
- The `RemoteInput` keyboard opens with shift engaged and offers no way to turn that off, so the
  sign-in server and email are folded to lowercase. A password cannot be normalised, so a capital
  there is the user's to fix.
- **Ideas**: the same flow through `IdeasRepository`, no project step.
- Show the pending count after save so the user knows the note exists locally even though it
  has not synced.

## Protecting captures that have not synced

Everything the watch writes is offline-first, so two flows can destroy writing that never reached
the server. Both are guarded before the capture UI exists, because once capture ships the loss is
silent.

- **Unsubscribing** syncs the project first, while it is still in the sync filter, then deletes
  its content only if the sync journal reports nothing outstanding. If anything remains the
  project keeps both its content and its subscription: an unsubscribed project is filtered out of
  every later sync, so dropping the subscription would strand the writing rather than free it.
  A count that cannot be read is treated as outstanding, so a failure can never authorise a
  delete.
- **Signing out** counts the subscribed projects' pending entities plus ideas with no server
  baseline, and asks for confirmation before wiping. An unreadable count still warns.

`loadPendingEntityCount` is the source of truth for a project. It reads the journal file without a
project scope, because opening one initialises the scene tree, every scene's content, metadata and
the timeline, which is far too much work to learn how many entities are outstanding. A journal that
cannot be read throws rather than reporting zero: callers use the count to decide whether local
writing is safe to discard, and an unreadable journal is not evidence that it is. The watch reaches
it through `UnsyncedContentSource`, an interface, so the components can be tested with fakes.

## Background sync

WorkManager, mirroring the widget worker:

- A one-time expedited request with a network constraint after every capture, enqueued with
  `APPEND_OR_REPLACE`. `REPLACE` cancels work that is already running, so a second capture would
  abort the first one's sync partway through the protocol. `SyncCoordinator` reports `Busy` rather
  than `Skipped` when another sync holds the lock, so work that needs its own captures uploaded
  asks again instead of reporting success.
- The pending count is gathered after the save is confirmed, not before. Nothing that can still
  fail belongs between writing the user's words and telling them the words are safe.
- A periodic request (charging plus unmetered network) for all subscribed projects.
- A manual "Sync now" in the app.

## Read aloud

Phase-gated behind the rest, but designed in now because it depends on synced content.

- A foreground service owning a `TextToSpeech` instance and a `MediaSession`, so headphone
  controls and the system media UI work. Register an Ongoing Activity so the watch face shows
  the session.
- Text pipeline: scene markdown to plain speech text. Add a `base` helper next to
  `ProseHtml` that walks the same intellij-markdown tree the PDF renderer uses and emits plain
  paragraphs, dropping formatting markers and turning headings and scene breaks into pauses.
- Chunk by paragraph, staying under `TextToSpeech.getMaxSpeechInputLength()`, and queue with
  utterance IDs so progress is trackable. Persist the last utterance per scene so playback
  resumes where it stopped.
- Controls: play/pause, next/previous scene in outline order, speed.
- Output device: check `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` for a Bluetooth
  headset before starting, pause on disconnect, and offer the media output switcher on Wear OS
  5 and later. Allow the watch speaker only when the platform reports one.
- Offline: Wear OS ships an on-device TTS engine with seven preloaded languages; other
  languages download on Wi-Fi while charging. Voice quality is the on-device tier. Use the
  project's language setting to pick the TTS locale.

## Packaging and release

- `versionName` comes from `libs.versions.toml` like every other target; `prepareForRelease`
  needs no changes.
- `fastlane/Fastfile` gains wear lanes mirroring the phone lanes, and
  `publish-google-play.yml` uploads the wear bundle alongside the phone bundle on the same
  track.
- `build.yml` builds and unit-tests the wear module.

## Testing

- `common` desktop tests for the extracted sync use case, driven with the existing fake
  filesystem and in-memory datasources.
- Server route tests for `pair_install` (note that `frontend()` and account DI additions ripple
  into the existing route-test fixtures).
- Compose previews for every watch screen, rendered and checked.
- Wear emulator smoke run through the `android-cli` skill.
- End to end: capture a note on the watch offline, come online, confirm it appears on desktop
  after sync, and that a colliding note created offline on desktop is re-IDed rather than lost.

## Phases

1. **Sync use case extraction** in `common`. No behaviour change. Standalone PR.
2. **Pairing endpoint** on the server plus the client API and use case. Standalone PR.
3. **Wear module skeleton**: Koin split, pairing flow, manual sign-in fallback, project list
   with subscribe and sync, sync status.
4. **Capture** (done): guards for unsynced captures, then the activity with `RemoteInput`, note
   and idea flows, tile, complication, WorkManager sync.
5. **Read aloud**: markdown to speech helper in `base`, playback service, controls.
6. **Release pipeline**: fastlane lanes, Play workflow, version code offset.

Phases 1 and 2 improve the phone and desktop clients on their own and should land even if the
watch slips.

## Open questions

- Does dictation work offline on the target watch? Voice capture is the point of the feature and
  the emulator cannot answer this; it needs the real hardware.
- Whether Play accepts a wear bundle with a version-code offset scheme, or whether a separate
  version stream is cleaner.
- Whether backups on the watch stay on or get a no-op datasource once real usage shows the
  storage cost.
