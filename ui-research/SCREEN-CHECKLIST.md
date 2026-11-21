# UI 2.1 design pass: screens and dialogs

Every user-facing screen and dialog in the client (Wear OS excluded), in navigation order. Tick an item once it has had its design pass. Dialogs sit under the screen that opens them.

`g:` names the screen gallery entry that renders it (`./gradlew :composeUi:renderScreenGallery -PgalleryOnly="Name"`). Items marked **no gallery** need a preview added before they can be reviewed in the markup tool.

Shared building blocks are listed once at the end; fixing one fixes every dialog built on it.

## 1. Project selection

- [x] Project selection shell: nav rail / bottom bar (`ProjectSelectScaffold`) g: Project selection shell, Project selection shell, rail expanded
- [x] Projects (`ProjectListUi`) g: Project list, Project list, grouped, Project list, searching
  - [x] What's New, changelog (`ChangelogDialog`) g: What's New dialog
  - [x] Create Project (`ProjectCreateDialog`) g: Create project dialog
  - [x] Create from an Existing Story, import help (`ImportHelpDialog`) g: Import help dialog
  - [x] Import Story (`ImportStoryDialog`) g: Import story dialog
  - [x] Rename Project (`ProjectRenameDialog`) g: Rename project dialog
  - [x] Delete Project (`ProjectDeleteDialog`) g: Delete project dialog
  - [x] Leave Project (`ProjectLeaveDialog`) g: Leave project dialog
  - [x] Stop syncing this project? (`ProjectSyncOffDialog`) g: Stop syncing dialog
  - [x] Create / Rename / Describe / Delete Group (`ProjectGroupDialogs.kt`) g: Create group dialog, Rename group dialog, Group description dialog, Delete group dialog
  - [x] Move to group (`MoveToGroupDialog`) g: Move to group dialog
  - [x] Account Sync, with Sync Log and Cancel Sync? (`ProjectsSyncDialog`) g: Account sync dialog, Account sync log
  - [x] Story Idea Conflict (`IdeaConflictUi`) g: Story idea conflict
  - [x] Your app / the sync server is out of date (`ProtocolMismatchDialog`) g: Out of date dialog, app, Out of date dialog, server
  - [x] Update banner (`AppUpdateBanner`, inline) g: Project list, update available, Update banner states
- [x] Story Ideas list and idea view (`StoryIdeasUi`) g: Story ideas, Story idea
  - [x] Discard Changes / Delete Idea / Create Project from Idea (`SimpleConfirm`) g: Story idea confirms
- [x] Settings (`AccountSettingsUi`, all tabs) g: Account settings, Account settings, writing, Account settings, sync, Account settings, sync not set up
  - [x] Plugins tab (`PluginSettingsSection`, `RuntimePluginsSection`) g: Account settings, plugins
  - [x] Set Up Server, with MFA step (`ServerSetupDialog`, `MfaCodeStep`) g: Server setup dialog, Server setup dialog, MFA
  - [x] About Sync Servers (`ServerSetupHelpDialog`) g: About sync servers
  - [x] Terms of Service (`TermsOfServiceDialog`) g: Terms of service dialog
  - [x] Merge existing projects with the server?, and Delete all local projects? (`MergeProjectsDialog`) **no gallery** g: Merge projects dialog, Merge projects dialog, replace, Delete all local projects confirm
  - [x] Remove Server confirm (`SimpleConfirm`) **no gallery** g: Remove server confirm
  - [x] Manage Backups, and Restore Backup confirm (`BackupManagerDialog`) **no gallery** g: Backup manager, Restore backup confirm, Delete backup confirm
  - [x] Install "name"? (`InstallDialog`) **no gallery** g: Plugin install dialog
  - [x] Plugin details (`DetailsDialog`) **no gallery** g: Plugin details dialog
  - [x] Full command, desktop (`FullCommandDialog`) **no gallery** g: Full command dialog
  - [x] Spell check language info, Android (`SpellCheckDictionaryControl`) **no gallery** g: Spell check dictionary, Android, Spell check language info (baseline only, dialog removed)
- [x] About (`AboutAppUi`, `PlatformAboutSection`) g: About, About, update available
  - [x] Libraries (`LibrariesUi`) **no gallery** g: Open source libraries
  - [~] Studio overlay (`CairnAboutOverlay`, external cairn library) **no gallery** skipped: lives in Cairn, not this repo

## 2. Project root

- [x] Project shell: nav rail / bottom bar (`ProjectRootScaffold`) **no gallery** g: Project shell, Project shell, rail expanded
- [x] Unsaved Scenes (`ConfirmUnsavedScenesDialog`) **no gallery** g: Unsaved scenes confirm
- [x] Unsaved Note / Encyclopedia Entry / Timeline Event (`ConfirmCloseDialogs.kt`) **no gallery** g: Unsaved note, entry and event confirms
- [x] Search project (`GlobalSearchUi`) **no gallery** g: Search project, Search project, too short
- [x] Duplicate IDs (`DuplicateIdsDialog`) g: Duplicate IDs dialog

## 3. Project home

- [x] Home, stats dashboard (`ProjectHomeUi`, `ProjectStatsUi`) g: Project stats
  - [x] Export Story (`ExportOptionsDialog`, `SceneSelector`) g: Export options dialog
  - [x] Choosing an Export Format (`ExportHelpDialog`) **no gallery** g: Export format help
- [x] Project settings (`ProjectSettingsUi`, all tabs) g: Project settings, Project settings, shared, Project settings, writing, Project settings, timelines, Project settings, plugins
  - [x] Choose a language (`LanguageField`, `HdSearchableListDialog`) **no gallery** g: Language picker
  - [x] Color picker, Primary / Secondary (`ColorPickerDialog`) **no gallery** g: Color picker
  - [x] Edit timeline (`TimelineEditor`) **no gallery** g: Edit timeline
  - [x] Switch to a dated / manual timeline (`ModeSwitchDialog`) **no gallery** g: Switch timeline mode
  - [x] Plugins manage dialog (`ManagePluginsDialog`) **no gallery** g: Manage plugins dialog
  - [x] Per-plugin settings (`PluginSettingsDialog`) **no gallery** g: Plugin settings dialog
- [x] Images gallery (`ImageGalleryUi`) g: Image gallery
  - [x] Choose an image, pick mode g: Image picker
  - [x] Rename image (`RenameDialog`) **no gallery** g: Rename image
  - [x] Delete image (`DeleteDialog`) **no gallery** g: Delete image

## 4. Story

- [x] Story container, list and detail (`StoryEditorUi`) g: Story, no scene selected
- [x] Scenes list (`SceneListUi`) g: Scene list
  - [x] Create Scene / Group / Chapter Scene / Chapter Group (`CreateDialog`) g: Create scene dialog
  - [x] Rename Scene / Chapter / Group (`SceneRenameDialog`) g: Rename chapter dialog
  - [x] Delete Scene / Chapter / Group (`SceneDeleteDialog`) g: Delete scene confirm
  - [x] Cannot Delete Chapter / Group (`GroupDeleteNotAllowedDialog`) g: Cannot delete chapter
  - [x] Archive Scene, from the list (`SceneArchiveDialog`) g: Archive scene confirm
  - [x] Move scene (`MoveSceneDialog`) g: Move scene dialog
  - [x] Archived Scenes (`ArchivedScenesDialog`) g: Archived scenes
- [x] Story Outline (`OutlineOverviewUi`) g: Outline overview
- [x] Scene editor (`SceneEditorUi`) g: Scene editor
  - [x] Start New Draft (`SaveDraftDialog`) g: Start new draft
  - [x] Discard Changes (`DiscardBufferDialog`) g: Discard changes
  - [x] Archive Scene, from the editor (merged into `SceneArchiveDialog`) g: Archive scene confirm
  - [x] Rename Scene, Android / iOS top bar (`RenameSceneDialog`) g: Rename scene, phone editor
- [x] Scene metadata panel (`SceneMetadataPanelUi`) g: Scene metadata, timelines
  - [x] Add reference (`AddReferenceDialog`) g: Add reference
  - [x] Add tags (`AddTagDialog`) g: Add tags
- [x] Drafts list (`DraftsListUi`) g: Drafts list
- [x] Draft Compare (`DraftCompareUi`) g: Draft compare
- [x] Reader mode (`ReaderModeUi`) g: Reader mode
- [x] Focus mode (`FocusModeUi`) g: Focus mode

## 5. Notes

- [x] Notes container (`NotesUi`) no UI of its own
- [x] Notes list (`BrowseNotesUi`) g: Browse notes
- [x] Note view (`ViewNoteUi`) g: View note
  - [x] Discard Changes? / Delete Note? (`SimpleConfirm`, `ConfirmDeleteNoteDialog`) g: Discard note changes, Delete note confirm
- [x] Create Note (`CreateNoteUi`) g: Create note, redesigned on the Create entry pattern
  - [x] Discard Changes? (`SimpleConfirm`) g: Discard note changes

## 6. Encyclopedia

- [x] Encyclopedia container (`EncyclopediaUi`) no UI of its own
- [x] Browse (`BrowseEntriesUi`) g: Browse encyclopedia
- [x] Entry view (`ViewEntryUi`) g: View encyclopedia entry, View encyclopedia entry, images, View encyclopedia entry, timelines
  - [x] Discard Changes? / Delete Entry? / Remove Image? (`SimpleConfirm`) g: Encyclopedia entry confirms
  - [x] Add Tags (`TagAddDialog`) g: Encyclopedia add tags and alias
  - [x] Add Alias (`AliasAddDialog`) g: Encyclopedia add tags and alias
  - [x] Rename "name" everywhere (`RenameEntryDialog`) g: Rename everywhere, names, Rename everywhere, summary
- [x] Create entry (`CreateEntryUi`) g: Create encyclopedia entry
  - [x] Discard New Entry? (`SimpleConfirm`) g: Discard new entry

## 7. Timeline

- [x] Timeline container (`TimeLineUi`) no UI of its own (event dialog frame is under Event view)
- [x] Timeline overview (`TimeLineOverviewUi`) g: Timeline
  - [x] New / Rename / Delete timeline (`TimelineSwitcherDialogs`) g: Timeline new, rename, delete
  - [x] Delete N events?, from the deleted-timeline notice (`TimelineSwitcher.kt`) g: Delete events of a deleted timeline
- [x] Event view and edit (`ViewTimeLineEventUi`) g: View timeline event
  - [x] Discard Changes? / Delete Timeline Event? (`SimpleConfirm`) g: Timeline event confirms
  - [x] Move to another timeline (`MoveToTimelineDialog`) g: Move to timeline dialog
- [ ] Create Event (`CreateTimeLineEventUi`) g: Create timeline event **needs a larger redesign, not a polish pass**
  - [x] Discard Changes? (`SimpleConfirm`) g: Timeline event confirms

## 8. Sync and auth

- [x] Project Synchronization, log and cancel (`ProjectSynchronizationUi`) g: Project sync, in progress, Project sync, log
  - [x] Cancel Sync? (`SimpleConfirm`)
- [x] Conflicts, shared layout (`ConflictSplit`)
  - [x] Scene conflict (`SceneConflict`) g: Sync conflict
  - [x] Scene draft conflict (`SceneDraftConflict`) g: Sync conflict, scene draft
  - [x] Note conflict (`NoteConflict`) g: Sync conflict, note
  - [x] Encyclopedia entry conflict (`EncyclopediaEntryConflict`) g: Sync conflict, encyclopedia entry
  - [x] Timeline event conflict (`TimelineEventConflict`) g: Sync conflict, timeline event
  - [x] Image conflict (`ImageConflict`) **no gallery**; takes the shared conflict layout changes, no preview to review
  - [x] Project data conflict (`ProjectDataConflict`) g: Sync conflict, project data
- [x] Reauthorize, with MFA step (`ReauthenticationUi`) g: Reauthorize, Reauthorize, MFA

## 9. Plugin actions

- [x] Action page (`PageDialog`) g: Plugin action dialogs (shared frame)
- [x] Start over? confirm g: Plugin action dialogs (shared frame)
- [x] Scene chooser (`SceneChooser`) g: Plugin action dialogs (shared frame)
- [x] Run that cannot continue, and Start afresh? (`ChangedDialog`) g: Plugin action dialogs (shared frame)
- [x] Working / progress (`WorkingDialog`) g: Plugin action dialogs (shared frame)

## 10. Platform-only

Desktop:
- [x] Splash (`SplashScreen`) reviewed by code, brand screen left as is
- [x] Editor window menu bar (`ProjectEditorWindow`) reviewed by code, platform title-case menus left as is
- [x] Close-window unsaved confirms (`desktop/.../ConfirmCloseDialogs.kt`; copies of the project-root confirms) reviewed by code, matched to the project-root confirms
- [x] macOS sandbox: Welcome to Hammer, retry, choose a projects folder (`SandboxStartup`, native alerts) reviewed by code, native alerts left as is

Android:
- [x] Add Note to:, and Discard Note? (`AddNoteActivity` widget) reviewed by code
- [x] Configure Note Widget (`AddNoteWidgetConfigActivity`) reviewed by code
- [x] Story Info Widget config (`StoryInfoWidgetConfigActivity`) reviewed by code
- [x] Sign in watch to Hammer?, gms only (`WearPairingDialog`) reviewed by code

## Shared building blocks

- [x] `ConfirmationDialog`, `IndexStripDialog` reworked through its confirms (captions, verb buttons, destructive styling)
- [x] `SimpleConfirm` (used by ~20 confirms above) reworked through its confirms
- [x] `SimpleDialog` deleted; every caller moved to `HdHairlineDialogShell`
- [x] `FormDialog`, `FormDialogScaffold` reworked through its dialogs
- [x] `AnimatedDialog`, `AnimatedFullScreenDialog` no chrome of their own
- [x] `HdHairlineDialogShell` close made optional; dropped wherever Cancel already closes
- [x] `HdSearchableListDialog` reviewed by code: on the hairline shell, one close
- [x] `UnsavedScenesConfirmDialog` reworked in f3e764edb
- [x] `EntityPickerDialog` (Add references, Place a scene / event, plugin entity fields) reviewed by code: on the hairline shell, one close
- [x] `ImageViewerDialog` (cover, entry and gallery images) reviewed by code: full-screen viewer, one close

## After the pass

Over everything the pass touched (all `4_0/ui-redesign-prototype` commits since the pass began):

- [ ] `/simplify`: duplicate code and repeated implementations of common things (cards, title rows, confirm wiring, previews)
- [ ] Compose stability and performance review (unstable params, needless recomposition, remember/derivedStateOf misuse, lambdas, lazy list keys)
- [ ] `/code-review` for correctness
