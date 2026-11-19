# UI 2.1 design pass: screens and dialogs

Every user-facing screen and dialog in the client (Wear OS excluded), in navigation order. Tick an item once it has had its design pass. Dialogs sit under the screen that opens them.

`g:` names the screen gallery entry that renders it (`./gradlew :composeUi:renderScreenGallery -PgalleryOnly="Name"`). Items marked **no gallery** need a preview added before they can be reviewed in the markup tool.

Shared building blocks are listed once at the end; fixing one fixes every dialog built on it.

## 1. Project selection

- [x] Project selection shell: nav rail / bottom bar (`ProjectSelectScaffold`) g: Project selection shell, Project selection shell, rail expanded
- [x] Projects (`ProjectListUi`) g: Project list, Project list, grouped, Project list, searching
  - [x] What's New, changelog (`ChangelogDialog`) g: What's New dialog
  - [x] Create Project (`ProjectCreateDialog`) g: Create project dialog
  - [ ] Create from an Existing Story, import help (`ImportHelpDialog`) **no gallery**
  - [ ] Import Story (`ImportStoryDialog`) g: Import story dialog
  - [ ] Rename Project (`ProjectRenameDialog`) **no gallery**
  - [ ] Delete Project (`ProjectDeleteDialog`) **no gallery**
  - [ ] Leave Project (`ProjectLeaveDialog`) **no gallery**
  - [ ] Stop syncing this project? (`ProjectSyncOffDialog`) **no gallery**
  - [ ] Create / Rename / Describe / Delete Group (`ProjectGroupDialogs.kt`) **no gallery**
  - [ ] Move to group (`MoveToGroupDialog`) **no gallery**
  - [ ] Account Sync, with Sync Log and Cancel Sync? (`ProjectsSyncDialog`) **no gallery**
  - [ ] Story Idea Conflict (`IdeaConflictUi`) **no gallery**
  - [ ] Your app / the sync server is out of date (`ProtocolMismatchDialog`) **no gallery**
  - [ ] Update banner (`AppUpdateBanner`, inline)
- [ ] Story Ideas list and idea view (`StoryIdeasUi`) g: Story ideas, Story idea
  - [ ] Discard Changes / Delete Idea / Create Project from Idea (`SimpleConfirm`) **no gallery**
- [x] Settings (`AccountSettingsUi`, all tabs) g: Account settings, Account settings, writing, Account settings, sync, Account settings, sync not set up
  - [ ] Plugins tab (`PluginSettingsSection`, `RuntimePluginsSection`) **no gallery**
  - [ ] Set Up Server, with MFA step (`ServerSetupDialog`, `MfaCodeStep`) **no gallery**
  - [ ] About Sync Servers (`ServerSetupHelpDialog`) **no gallery**
  - [ ] Terms of Service (`TermsOfServiceDialog`) **no gallery**
  - [ ] Merge existing projects with the server?, and Delete all local projects? (`MergeProjectsDialog`) **no gallery**
  - [ ] Remove Server confirm (`SimpleConfirm`) **no gallery**
  - [ ] Manage Backups, and Restore Backup confirm (`BackupManagerDialog`) **no gallery**
  - [ ] Install "name"? (`InstallDialog`) **no gallery**
  - [ ] Plugin details (`DetailsDialog`) **no gallery**
  - [ ] Full command, desktop (`FullCommandDialog`) **no gallery**
  - [ ] Spell check language info, Android (`SpellCheckDictionaryControl`) **no gallery**
- [x] About (`AboutAppUi`, `PlatformAboutSection`) g: About, About, update available
  - [ ] Libraries (`LibrariesUi`) **no gallery**
  - [ ] Studio overlay (`CairnAboutOverlay`, external cairn library) **no gallery**

## 2. Project root

- [ ] Project shell: nav rail / bottom bar (`ProjectRootScaffold`) **no gallery**
- [ ] Unsaved Scenes (`ConfirmUnsavedScenesDialog`) **no gallery**
- [ ] Unsaved Note / Encyclopedia Entry / Timeline Event (`ConfirmCloseDialogs.kt`) **no gallery**
- [ ] Search project (`GlobalSearchUi`) **no gallery**
- [ ] Duplicate IDs (`DuplicateIdsDialog`) g: Duplicate IDs dialog

## 3. Project home

- [ ] Home, stats dashboard (`ProjectHomeUi`, `ProjectStatsUi`) g: Project stats
  - [ ] Export Story (`ExportOptionsDialog`, `SceneSelector`) g: Export options dialog
  - [ ] Choosing an Export Format (`ExportHelpDialog`) **no gallery**
- [x] Project settings (`ProjectSettingsUi`, all tabs) g: Project settings, Project settings, shared, Project settings, writing, Project settings, timelines, Project settings, plugins
  - [ ] Choose a language (`LanguageField`, `HdSearchableListDialog`) **no gallery**
  - [ ] Color picker, Primary / Secondary (`ColorPickerDialog`) **no gallery**
  - [ ] Edit timeline (`TimelineEditor`) **no gallery**
  - [ ] Switch to a dated / manual timeline (`ModeSwitchDialog`) **no gallery**
  - [ ] Plugins manage dialog (`ManagePluginsDialog`) **no gallery**
  - [ ] Per-plugin settings (`PluginSettingsDialog`) **no gallery**
- [ ] Images gallery (`ImageGalleryUi`) g: Image gallery
  - [ ] Choose an image, pick mode g: Image picker
  - [ ] Rename image (`RenameDialog`) **no gallery**
  - [ ] Delete image (`DeleteDialog`) **no gallery**

## 4. Story

- [ ] Story container, list and detail (`StoryEditorUi`) **no gallery**
- [ ] Scenes list (`SceneListUi`) g: Scene list
  - [ ] Create Scene / Group / Chapter Scene / Chapter Group (`CreateDialog`) **no gallery**
  - [ ] Rename Scene / Chapter / Group (`SceneRenameDialog`) **no gallery**
  - [ ] Delete Scene / Chapter / Group (`SceneDeleteDialog`) **no gallery**
  - [ ] Cannot Delete Chapter / Group (`GroupDeleteNotAllowedDialog`) **no gallery**
  - [ ] Archive Scene, from the list (`SceneArchiveDialog`) **no gallery**
  - [ ] Move scene (`MoveSceneDialog`) g: Move scene dialog
  - [ ] Archived Scenes (`ArchivedScenesDialog`) **no gallery**
- [ ] Story Outline (`OutlineOverviewUi`) g: Outline overview
- [ ] Scene editor (`SceneEditorUi`) g: Scene editor
  - [ ] Start New Draft (`SaveDraftDialog`) **no gallery**
  - [ ] Discard Changes (`DiscardBufferDialog`) **no gallery**
  - [ ] Archive Scene, from the editor (`ArchiveSceneDialog`; duplicates the list's archive dialog) **no gallery**
  - [ ] Rename Scene, Android / iOS top bar (`RenameSceneDialog`) **no gallery**
- [ ] Scene metadata panel (`SceneMetadataPanelUi`) g: Scene metadata, timelines
  - [ ] Add reference (`AddReferenceDialog`) **no gallery**
  - [ ] Add tags (`AddTagDialog`) **no gallery**
- [ ] Drafts list (`DraftsListUi`) g: Drafts list
- [ ] Draft Compare (`DraftCompareUi`) **no gallery** (preview exists)
- [ ] Reader mode (`ReaderModeUi`) g: Reader mode
- [ ] Focus mode (`FocusModeUi`) g: Focus mode

## 5. Notes

- [ ] Notes container (`NotesUi`) **no gallery**
- [ ] Notes list (`BrowseNotesUi`) g: Browse notes
- [ ] Note view (`ViewNoteUi`) g: View note
  - [ ] Discard Changes? / Delete Note? (`SimpleConfirm`, `ConfirmDeleteNoteDialog`) **no gallery**
- [ ] Create Note (`CreateNoteUi`) g: Create note
  - [ ] Discard Changes? (`SimpleConfirm`) **no gallery**

## 6. Encyclopedia

- [ ] Encyclopedia container (`EncyclopediaUi`) **no gallery**
- [ ] Browse (`BrowseEntriesUi`) g: Browse encyclopedia
- [ ] Entry view (`ViewEntryUi`) g: View encyclopedia entry, View encyclopedia entry, images, View encyclopedia entry, timelines
  - [ ] Discard Changes? / Delete Entry? / Remove Image? (`SimpleConfirm`) **no gallery**
  - [ ] Add Tags (`TagAddDialog`) **no gallery**
  - [ ] Add Alias (`AliasAddDialog`) **no gallery**
  - [ ] Rename "name" everywhere (`RenameEntryDialog`) **no gallery**
- [ ] Create entry (`CreateEntryUi`) g: Create encyclopedia entry
  - [ ] Discard New Entry? (`SimpleConfirm`) **no gallery**

## 7. Timeline

- [ ] Timeline container (`TimeLineUi`) **no gallery**
- [ ] Timeline overview (`TimeLineOverviewUi`) g: Timeline
  - [ ] New / Rename / Delete timeline (`TimelineSwitcherDialogs`) **no gallery**
  - [ ] Delete N events?, from the deleted-timeline notice (`TimelineSwitcher.kt`) **no gallery**
- [ ] Event view and edit (`ViewTimeLineEventUi`) g: View timeline event
  - [ ] Discard Changes? / Delete Timeline Event? (`SimpleConfirm`) **no gallery**
  - [ ] Move to another timeline (`MoveToTimelineDialog`) g: Move to timeline dialog
- [ ] Create Event (`CreateTimeLineEventUi`) g: Create timeline event
  - [ ] Discard Changes? (`SimpleConfirm`) **no gallery**

## 8. Sync and auth

- [ ] Project Synchronization, log and cancel (`ProjectSynchronizationUi`) **no gallery** for the log view
  - [ ] Cancel Sync? (`SimpleConfirm`) **no gallery**
- [ ] Conflicts, shared layout (`ConflictSplit`)
  - [ ] Scene conflict (`SceneConflict`) g: Sync conflict
  - [ ] Scene draft conflict (`SceneDraftConflict`) **no gallery**
  - [ ] Note conflict (`NoteConflict`) **no gallery**
  - [ ] Encyclopedia entry conflict (`EncyclopediaEntryConflict`) **no gallery**
  - [ ] Timeline event conflict (`TimelineEventConflict`) **no gallery**
  - [ ] Image conflict (`ImageConflict`) **no gallery**
  - [ ] Project data conflict (`ProjectDataConflict`) **no gallery**
- [ ] Reauthorize, with MFA step (`ReauthenticationUi`) **no gallery**

## 9. Plugin actions

- [ ] Action page (`PageDialog`) **no gallery**
- [ ] Start over? confirm **no gallery**
- [ ] Scene chooser (`SceneChooser`) **no gallery**
- [ ] Run that cannot continue, and Start afresh? (`ChangedDialog`) **no gallery**
- [ ] Working / progress (`WorkingDialog`) **no gallery**

## 10. Platform-only

Desktop:
- [ ] Splash (`SplashScreen`) **no gallery**
- [ ] Editor window menu bar (`ProjectEditorWindow`) **no gallery**
- [ ] Close-window unsaved confirms (`desktop/.../ConfirmCloseDialogs.kt`; copies of the project-root confirms) **no gallery**
- [ ] macOS sandbox: Welcome to Hammer, retry, choose a projects folder (`SandboxStartup`, native alerts)

Android:
- [ ] Add Note to:, and Discard Note? (`AddNoteActivity` widget) **no gallery**
- [ ] Configure Note Widget (`AddNoteWidgetConfigActivity`) **no gallery**
- [ ] Story Info Widget config (`StoryInfoWidgetConfigActivity`) **no gallery**
- [ ] Sign in watch to Hammer?, gms only (`WearPairingDialog`) **no gallery**

## Shared building blocks

- [ ] `ConfirmationDialog`, `IndexStripDialog`
- [ ] `SimpleConfirm` (used by ~20 confirms above)
- [ ] `SimpleDialog`
- [ ] `FormDialog`, `FormDialogScaffold`
- [ ] `AnimatedDialog`, `AnimatedFullScreenDialog`
- [ ] `HdHairlineDialogShell`
- [ ] `HdSearchableListDialog`
- [ ] `UnsavedScenesConfirmDialog`
- [ ] `EntityPickerDialog` (Add references, Place a scene / event, plugin entity fields)
- [ ] `ImageViewerDialog` (cover, entry and gallery images)
