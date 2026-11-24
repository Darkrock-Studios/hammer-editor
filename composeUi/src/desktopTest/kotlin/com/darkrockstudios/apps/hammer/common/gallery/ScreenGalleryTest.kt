package com.darkrockstudios.apps.hammer.common.gallery

import com.darkrockstudios.apps.hammer.common.preview.HelpDialogAllTopicsPreview
import com.darkrockstudios.apps.hammer.common.preview.HelpDialogScenesPreview
import com.darkrockstudios.apps.hammer.common.preview.MergeProjectsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.plugin.PluginActionDialogsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ReauthenticationScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ReauthenticationMfaScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ProjectSyncInProgressPreview
import com.darkrockstudios.apps.hammer.common.preview.ProjectSyncLogPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.TimelineEventConfirmsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.DeleteOrphansScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.TimelineSwitcherDialogsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.DiscardNewEntryScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.RenameEntryNamesScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.RenameEntrySummaryScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.EntryAddDialogsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.EntryConfirmsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.NoteDiscardConfirmScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.NoteDeleteConfirmScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenDraftCompareUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.AddTagScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.AddReferenceScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.EditorRenameSceneScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.DiscardChangesScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.SaveDraftScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ArchivedScenesScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ArchivedScenesEmptyScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.MergeReplaceConfirmScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.RemoveServerConfirmScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.BackupManagerScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.BackupRestoreConfirmScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.BackupDeleteConfirmScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.PluginInstallScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.PluginDetailsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.FullCommandScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.SpellCheckDictionaryAndroidPreview
import com.darkrockstudios.apps.hammer.common.preview.LibrariesScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.UnsavedScenesScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.UnsavedEntityConfirmsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.GlobalSearchScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.GlobalSearchEmptyScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ExportHelpScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.designsystem.LanguagePickerScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ColorPickerScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.TimelineEditorScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.TimelineModeSwitchScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ManagePluginsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.PluginSettingsScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.images.RenameImageScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.images.DeleteImageScrimPreview
import com.darkrockstudios.apps.hammer.common.projectselection.settings.MergeMode
import com.darkrockstudios.apps.hammer.common.preview.TermsOfServiceScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ServerSetupHelpPreview
import com.darkrockstudios.apps.hammer.common.preview.ServerSetupDialogScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenAccountSettingsUiPluginsTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyideas.StoryIdeaConfirmsPreview
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdGrouping
import com.darkrockstudios.apps.hammer.common.compose.designsystem.LocalHdGrouping
import com.darkrockstudios.apps.hammer.common.compose.theme.hammerMonoFontFamily
import com.darkrockstudios.apps.hammer.common.preview.ScreenAccountSettingsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenAccountSettingsUiWritingTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenAccountSettingsUiSyncTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenAccountSettingsUiSyncNotSetUpTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.SceneConflictCompactPreview
import com.darkrockstudios.apps.hammer.common.preview.NoteConflictPreview
import com.darkrockstudios.apps.hammer.common.preview.SceneDraftConflictPreview
import com.darkrockstudios.apps.hammer.common.preview.EncyclopediaEntryConflictPreview
import com.darkrockstudios.apps.hammer.common.preview.EncyclopediaEntryConflictCompactPreview
import com.darkrockstudios.apps.hammer.common.preview.TimelineConflictPreview
import com.darkrockstudios.apps.hammer.common.preview.ProjectDataConflictPreview
import com.darkrockstudios.apps.hammer.common.preview.SceneConflictPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenCreateEntryPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenSceneListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenSceneListUiTipPreview
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import com.darkrockstudios.apps.hammer.common.components.intro.Intro
import com.darkrockstudios.apps.hammer.common.preview.intro.ScreenIntroUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.CreateSceneDialogScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.SceneRenameDialogScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.SceneDeleteConfirmScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ChapterCannotDeleteScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.SceneArchiveConfirmScrimPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenStoryNoSceneTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectListUiGroupedTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectListUiSearchingTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectListUiUpdateTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.AppUpdateBannerStates
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ImportStoryDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.SceneMetadataPanelUiTimelinesPreview
import com.darkrockstudios.apps.hammer.common.preview.encyclopedia.ScreenViewEntryUiImagesPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenViewEntryTimelinesPreview
import com.darkrockstudios.apps.hammer.common.preview.images.ImageGalleryBrowsePreview
import com.darkrockstudios.apps.hammer.common.preview.images.ImageGalleryPickTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.MoveToTimelineDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectSettingsUiSharedPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectSettingsUiWritingTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectSettingsUiTimelinesTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectSettingsUiPluginsTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.TimelineSettingsUiThreeTimelinesPreview
import com.darkrockstudios.apps.hammer.common.preview.duplicateids.DuplicateIdsDialogDarkPreview
import com.darkrockstudios.apps.hammer.common.preview.duplicateids.DuplicateIdsDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.encyclopedia.ScreenBrowseEntriesUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.encyclopedia.ScreenViewEntryUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenBrowseNotesUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenCreateNoteUiPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenViewNoteUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ExportOptionsDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectSettingsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectStatsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectShellTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectCreateDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ImportHelpDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ProjectDeleteDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ProjectLeaveDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ProjectSyncOffDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.GroupCreateDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.GroupRenameDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.GroupDescriptionDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.GroupDeleteDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.MoveToGroupDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ProjectsSyncDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.protocolmismatch.ProtocolMismatchDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ProjectRenameDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenDraftsListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenFocusModeUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenSceneEditorUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyeditor.ScreenMoveSceneDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.storyeditor.ScreenOutlineOverviewUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyeditor.ScreenReaderModeUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyideas.ScreenStoryIdeasUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyideas.ScreenStoryIdeasViewTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenCreateTimeLineEventUiPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenTimeLineOverviewUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenViewTimeLineEventUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ChangelogDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectSelectShellTabletPreview
import com.darkrockstudios.apps.hammer.common.projectselection.about.ScreenAboutAppUiTabletPreview
import com.darkrockstudios.apps.hammer.common.projectselection.about.ScreenAboutAppUiUpdateAvailableTabletPreview
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/**
 * Renders every major client screen at phone and tablet/desktop sizes, in light and
 * dark, into a browsable gallery for UI review. Skipped in normal test runs; use
 * `./gradlew :composeUi:renderScreenGallery`, which writes `build/screen-gallery/index.html`.
 *
 * Screens come from their tablet previews: `TabletPreviewSurface` picks the layout
 * from the canvas size, so the same preview yields the compact layout on a phone canvas.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ScreenGalleryTest {

	private enum class FormFactor(val label: String, val widthDp: Int, val heightDp: Int) {
		Phone("Phone", 412, 915),
		Tablet("Tablet / desktop", 1280, 800),
	}

	private data class Variant(val formFactor: FormFactor, val dark: Boolean) {
		val dir = "${formFactor.name.lowercase()}-${if (dark) "dark" else "light"}"
		val label = "${formFactor.label}, ${if (dark) "dark" else "light"}"
	}

	private class Screen(
		val name: String,
		val note: String? = null,
		val content: @Composable (FormFactor) -> Unit,
	)

	private data class StudyStyle(val label: String, val grouping: HdGrouping)

	/** [scrollerHeightPx] is the height of the scroll container holding the most hidden content. */
	private class Capture(
		val image: BufferedImage,
		val overflowPx: Int,
		val scrollerHeightPx: Int,
		val textInventory: String,
	)

	/** [full] is the grown-canvas capture; null when the screen fits its viewport. */
	private data class Shot(val viewport: String, val full: String?, val hiddenPx: Int) {
		val fullComplete get() = hiddenPx <= 0
	}

	private fun screen(name: String, note: String? = null, content: @Composable () -> Unit) =
		Screen(name, note) { content() }

	/** For previews that don't wrap themselves in the theme-aware, size-aware surface. */
	private fun surfaced(name: String, note: String? = null, content: @Composable () -> Unit) =
		Screen(name, note = note) { TabletPreviewSurface(content) }

	private val screens = listOf(
		screen("Project list") { ScreenProjectListUiTabletPreview() },
		screen("Project list, grouped") { ScreenProjectListUiGroupedTabletPreview() },
		screen("Project list, searching") { ScreenProjectListUiSearchingTabletPreview() },
		screen("Project list, update available") { ScreenProjectListUiUpdateTabletPreview() },
		surfaced("Update banner states") { AppUpdateBannerStates() },
		surfaced("Import story dialog") { ImportStoryDialogPreview() },
		surfaced("What's New dialog") { ChangelogDialogPreview() },
		screen("Create project dialog", note = "Preview hardcodes the dark theme.") { ScreenProjectCreateDialogPreview() },
		surfaced("Import help dialog") { ImportHelpDialogPreview() },
		surfaced("Rename project dialog") { ProjectRenameDialogPreview() },
		surfaced("Delete project dialog") { ProjectDeleteDialogPreview() },
		surfaced("Leave project dialog") { ProjectLeaveDialogPreview() },
		surfaced("Stop syncing dialog") { ProjectSyncOffDialogPreview() },
		surfaced("Create group dialog") { GroupCreateDialogPreview() },
		surfaced("Rename group dialog") { GroupRenameDialogPreview() },
		surfaced("Group description dialog") { GroupDescriptionDialogPreview() },
		surfaced("Delete group dialog") { GroupDeleteDialogPreview() },
		surfaced("Move to group dialog") { MoveToGroupDialogPreview() },
		surfaced("Account sync dialog") { ProjectsSyncDialogPreview() },
		surfaced("Account sync log") { ProjectsSyncDialogPreview(showLog = true) },
		surfaced("Story idea conflict") { ProjectsSyncDialogPreview(ideaConflict = true) },
		surfaced("Out of date dialog, app") { ProtocolMismatchDialogPreview() },
		surfaced("Out of date dialog, server") { ProtocolMismatchDialogPreview(clientIsBehind = false) },
		*Intro.Slide.entries.map { slide ->
			val name = if (slide == Intro.Slide.Example) "example project" else slide.name.lowercase()
			screen("Intro, $name") { ScreenIntroUiTabletPreview(slide) }
		}.toTypedArray(),
		screen("Intro, welcome, 840x670 window", note = "Drawn top-left at that size; phone canvases crop it.") {
			Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(840.dp, 670.dp)) {
				ScreenIntroUiTabletPreview(Intro.Slide.Welcome)
			}
		},
		screen("Intro, welcome, 1024x640 window", note = "Drawn top-left at that size; phone canvases crop it.") {
			Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(1024.dp, 640.dp)) {
				ScreenIntroUiTabletPreview(Intro.Slide.Welcome)
			}
		},
		screen("Project selection shell") { ScreenProjectSelectShellTabletPreview() },
		screen("Project selection shell, rail expanded") { ScreenProjectSelectShellTabletPreview(railExpanded = true) },
		screen("Project shell") { ScreenProjectShellTabletPreview() },
		screen("Project shell, rail expanded") { ScreenProjectShellTabletPreview(railExpanded = true) },
		surfaced("Unsaved scenes confirm") { UnsavedScenesScrimPreview() },
		surfaced("Unsaved note, entry and event confirms") { UnsavedEntityConfirmsScrimPreview() },
		surfaced("Search project") { GlobalSearchScrimPreview() },
		surfaced("Search project, too short") { GlobalSearchEmptyScrimPreview() },
		screen("About") { ScreenAboutAppUiTabletPreview() },
		screen("About, update available") { ScreenAboutAppUiUpdateAvailableTabletPreview() },
		surfaced("Help, scenes") { HelpDialogScenesPreview() },
		surfaced("Help, all topics") { HelpDialogAllTopicsPreview() },
		screen("Account settings") { ScreenAccountSettingsUiTabletPreview() },
		screen("Account settings, writing") { ScreenAccountSettingsUiWritingTabletPreview() },
		screen("Account settings, sync") { ScreenAccountSettingsUiSyncTabletPreview() },
		screen("Account settings, sync not set up") { ScreenAccountSettingsUiSyncNotSetUpTabletPreview() },
		screen("Account settings, plugins") { ScreenAccountSettingsUiPluginsTabletPreview() },
		surfaced("Server setup dialog") { ServerSetupDialogScrimPreview() },
		surfaced("Server setup dialog, MFA") { ServerSetupDialogScrimPreview(mfa = true) },
		surfaced("About sync servers") { ServerSetupHelpPreview() },
		surfaced("Terms of service dialog") { TermsOfServiceScrimPreview() },
		surfaced("Merge projects dialog") { MergeProjectsScrimPreview(MergeMode.Merge) },
		surfaced("Merge projects dialog, replace") { MergeProjectsScrimPreview(MergeMode.Replace) },
		surfaced("Delete all local projects confirm") { MergeReplaceConfirmScrimPreview() },
		surfaced("Remove server confirm") { RemoveServerConfirmScrimPreview() },
		surfaced("Backup manager") { BackupManagerScrimPreview() },
		surfaced("Restore backup confirm") { BackupRestoreConfirmScrimPreview() },
		surfaced("Delete backup confirm") { BackupDeleteConfirmScrimPreview() },
		surfaced("Plugin install dialog") { PluginInstallScrimPreview() },
		surfaced("Plugin details dialog") { PluginDetailsScrimPreview() },
		surfaced("Full command dialog") { FullCommandScrimPreview() },
		surfaced("Spell check dictionary, Android") { SpellCheckDictionaryAndroidPreview() },
		surfaced("Open source libraries") { LibrariesScrimPreview() },
		screen("Story, no scene selected") { ScreenStoryNoSceneTabletPreview() },
		screen("Scene list") { ScreenSceneListUiTabletPreview() },
		screen("Scene list, swipe tip") { ScreenSceneListUiTipPreview(NuxHint.SceneListSwipeMenu) },
		screen("Scene list, right-click tip") { ScreenSceneListUiTipPreview(NuxHint.SceneListContextMenu) },
		screen("Scene list, reorder tip") { ScreenSceneListUiTipPreview(NuxHint.SceneListReorder) },
		surfaced("Create scene dialog") { CreateSceneDialogScrimPreview() },
		surfaced("Rename chapter dialog") { SceneRenameDialogScrimPreview() },
		surfaced("Delete scene confirm") { SceneDeleteConfirmScrimPreview() },
		surfaced("Cannot delete chapter") { ChapterCannotDeleteScrimPreview() },
		surfaced("Archive scene confirm") { SceneArchiveConfirmScrimPreview() },
		screen("Scene editor") { ScreenSceneEditorUiTabletPreview() },
		surfaced("Start new draft") { SaveDraftScrimPreview() },
		surfaced("Discard changes") { DiscardChangesScrimPreview() },
		surfaced("Rename scene, phone editor") { EditorRenameSceneScrimPreview() },
		surfaced("Scene metadata, timelines") { SceneMetadataPanelUiTimelinesPreview() },
		surfaced("Add reference") { AddReferenceScrimPreview() },
		surfaced("Add tags") { AddTagScrimPreview() },
		screen("Focus mode") { ScreenFocusModeUiTabletPreview() },
		screen("Drafts list") { ScreenDraftsListUiTabletPreview() },
		screen("Draft compare") { ScreenDraftCompareUiTabletPreview() },
		screen("Reader mode") { ScreenReaderModeUiTabletPreview() },
		screen("Outline overview") { ScreenOutlineOverviewUiTabletPreview() },
		surfaced("Move scene dialog") { ScreenMoveSceneDialogPreview() },
		surfaced("Archived scenes") { ArchivedScenesScrimPreview() },
		surfaced("Archived scenes, empty") { ArchivedScenesEmptyScrimPreview() },
		screen("Browse notes") { ScreenBrowseNotesUiTabletPreview() },
		screen("View note") { ScreenViewNoteUiTabletPreview() },
		surfaced("Discard note changes") { NoteDiscardConfirmScrimPreview() },
		surfaced("Delete note confirm") { NoteDeleteConfirmScrimPreview() },
		surfaced("Create note") { ScreenCreateNoteUiPreview() },
		screen("Browse encyclopedia") { ScreenBrowseEntriesUiTabletPreview() },
		screen("View encyclopedia entry") { ScreenViewEntryUiTabletPreview() },
		screen("View encyclopedia entry, images") { ScreenViewEntryUiImagesPreview() },
		surfaced("View encyclopedia entry, timelines") { ScreenViewEntryTimelinesPreview() },
		surfaced("Encyclopedia entry confirms") { EntryConfirmsScrimPreview() },
		surfaced("Encyclopedia add tags and alias") { EntryAddDialogsScrimPreview() },
		surfaced("Rename everywhere, names") { RenameEntryNamesScrimPreview() },
		surfaced("Rename everywhere, summary") { RenameEntrySummaryScrimPreview() },
		surfaced("Image gallery") { ImageGalleryBrowsePreview() },
		surfaced("Image picker") { ImageGalleryPickTabletPreview() },
		surfaced("Rename image") { RenameImageScrimPreview() },
		surfaced("Delete image") { DeleteImageScrimPreview() },
		surfaced("Create encyclopedia entry") { ScreenCreateEntryPreview() },
		surfaced("Discard new entry") { DiscardNewEntryScrimPreview() },
		screen("Timeline") { ScreenTimeLineOverviewUiTabletPreview() },
		surfaced("Timeline new, rename, delete") { TimelineSwitcherDialogsScrimPreview() },
		surfaced("Delete events of a deleted timeline") { DeleteOrphansScrimPreview() },
		screen("View timeline event") { ScreenViewTimeLineEventUiTabletPreview() },
		surfaced("Timeline event confirms") { TimelineEventConfirmsScrimPreview() },
		surfaced("Move to timeline dialog") { MoveToTimelineDialogPreview() },
		surfaced("Create timeline event") { ScreenCreateTimeLineEventUiPreview() },
		screen("Story ideas") { ScreenStoryIdeasUiTabletPreview() },
		screen("Story idea") { ScreenStoryIdeasViewTabletPreview() },
		surfaced("Story idea confirms") { StoryIdeaConfirmsPreview() },
		screen("Project stats") { ScreenProjectStatsUiTabletPreview() },
		screen("Project settings") { ScreenProjectSettingsUiTabletPreview() },
		screen("Project settings, shared") { ScreenProjectSettingsUiSharedPreview() },
		screen("Project settings, writing") { ScreenProjectSettingsUiWritingTabletPreview() },
		screen("Project settings, timelines") { ScreenProjectSettingsUiTimelinesTabletPreview() },
		screen("Project settings, plugins") { ScreenProjectSettingsUiPluginsTabletPreview() },
		surfaced("Timeline settings") { TimelineSettingsUiThreeTimelinesPreview() },
		surfaced("Edit timeline") { TimelineEditorScrimPreview() },
		surfaced("Switch timeline mode") { TimelineModeSwitchScrimPreview() },
		surfaced("Manage plugins dialog") { ManagePluginsScrimPreview() },
		surfaced("Plugin settings dialog") { PluginSettingsScrimPreview() },
		surfaced("Export options dialog") { ExportOptionsDialogPreview() },
		surfaced("Export format help") { ExportHelpScrimPreview() },
		surfaced("Language picker") { LanguagePickerScrimPreview() },
		surfaced("Color picker") { ColorPickerScrimPreview() },
		screen("Duplicate IDs dialog") {
			if (isSystemInDarkTheme()) DuplicateIdsDialogDarkPreview() else DuplicateIdsDialogPreview()
		},
		Screen("Project sync, in progress") { formFactor ->
			TabletPreviewSurface { ProjectSyncInProgressPreview(compact = formFactor == FormFactor.Phone) }
		},
		Screen("Project sync, log") { formFactor ->
			TabletPreviewSurface { ProjectSyncLogPreview(compact = formFactor == FormFactor.Phone) }
		},
		screen("Sync conflict, note", note = "Preview hardcodes a white backdrop and its own size class.") { TabletPreviewSurface { NoteConflictPreview() } },
		screen("Sync conflict, scene draft", note = "Preview hardcodes a white backdrop and its own size class.") { TabletPreviewSurface { SceneDraftConflictPreview() } },
		Screen("Sync conflict, encyclopedia entry", note = "Preview hardcodes a white backdrop and its own size class.") { formFactor ->
			TabletPreviewSurface {
				if (formFactor == FormFactor.Phone) EncyclopediaEntryConflictCompactPreview() else EncyclopediaEntryConflictPreview()
			}
		},
		screen("Sync conflict, timeline event", note = "Preview hardcodes a white backdrop and its own size class.") { TabletPreviewSurface { TimelineConflictPreview() } },
		screen("Sync conflict, project data", note = "Preview hardcodes a white backdrop and its own size class.") { TabletPreviewSurface { ProjectDataConflictPreview() } },
		surfaced("Plugin action dialogs", note = "Shared frame with stand-in bodies.") { PluginActionDialogsScrimPreview() },
		surfaced("Reauthorize") { ReauthenticationScrimPreview() },
		surfaced("Reauthorize, MFA") { ReauthenticationMfaScrimPreview() },
		Screen("Sync conflict", note = "Preview hardcodes a white backdrop and its own size class.") { formFactor ->
			TabletPreviewSurface {
				if (formFactor == FormFactor.Phone) SceneConflictCompactPreview() else SceneConflictPreview()
			}
		},
	)

	private var monoFamily: FontFamily? = null

	private val variants = FormFactor.entries.flatMap { ff -> listOf(Variant(ff, false), Variant(ff, true)) }

	@Test
	fun renderGallery() {
		assumeTrue(System.getProperty(ENABLED_PROPERTY) == "true", "Run via :composeUi:renderScreenGallery")
		val outDir = File(System.getProperty(OUT_DIR_PROPERTY) ?: "build/screen-gallery")
		val only = System.getProperty(ONLY_PROPERTY)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }
		// A partial render updates its screens in place, so the rest of the gallery stays viewable.
		if (only.isNullOrEmpty()) outDir.deleteRecursively()

		val failures = mutableListOf<String>()
		val rendered = mutableMapOf<Pair<String, String>, Shot>()
		val screens = if (only.isNullOrEmpty()) screens else screens.filter { it.name in only }
		for (screen in screens) {
			for (variant in variants) {
				runCatching { renderShot(screen, variant, outDir) }
					.onSuccess { rendered[screen.name to variant.dir] = it }
					.onFailure { failures += "${screen.name} (${variant.label}): ${it.message}" }
			}
		}

		val study = mutableMapOf<Triple<String, String, StudyStyle>, Shot>()
		for (screen in screens.filter { only.isNullOrEmpty() && (GROUPING_STUDY?.contains(it.name) ?: true) }) {
			for (variant in variants) {
				rendered[screen.name to variant.dir]?.let { study[Triple(screen.name, variant.dir, STUDY_STYLES.first())] = it }
				for (style in STUDY_STYLES.drop(1)) {
					val prefix = "grouping/${slug(style.label)}/"
					runCatching { renderShot(screen, variant, outDir, style, prefix) }
						.onSuccess { study[Triple(screen.name, variant.dir, style)] = it }
						.onFailure { failures += "${screen.name} (${variant.label}, ${style.label}): ${it.message}" }
				}
			}
		}

		File(outDir, "index.html").writeText(indexHtml(rendered, failures))
		File(outDir, "grouping.html").writeText(groupingHtml(study))
		val fullCount = rendered.values.count { it.full != null }
		println("Screen gallery: ${rendered.size} viewports, $fullCount full-length, ${study.size} grouping shots, ${failures.size} failures -> ${outDir.absolutePath}/index.html")
		val partial = rendered.filterValues { !it.fullComplete }
		File(outDir, "capture-report.txt").writeText(
			partial.entries.joinToString("\n") { (key, shot) -> "${key.first} (${key.second}): ${shot.hiddenPx / SCALE} dp still hidden" }
		)
		partial.forEach { (key, shot) -> println("  PARTIAL ${key.first} (${key.second}): ${shot.hiddenPx / SCALE} dp still hidden") }
		failures.forEach { println("  FAILED $it") }
		assertTrue(rendered.isNotEmpty(), "no screens rendered")
	}

	/**
	 * Captures the viewport, then, if anything scrolls vertically, re-renders on a canvas
	 * grown by the remaining scroll range until nothing scrolls. Lazy lists don't report
	 * their real range, only a small constant while more items remain, so when the range
	 * stalls but the scroller itself grew, growth continues in bigger steps. A scroller that
	 * neither shrinks its range nor grows has a fixed height, so growing stops there.
	 */
	private fun renderShot(
		screen: Screen,
		variant: Variant,
		outDir: File,
		style: StudyStyle = STUDY_STYLES.first(),
		dirPrefix: String = "",
	): Shot {
		val base = "$dirPrefix${variant.dir}/${slug(screen.name)}"
		val viewportHeight = variant.formFactor.heightDp * SCALE
		val viewport = capture(screen, variant, viewportHeight, style)
		write(viewport.image, File(outDir, "$base.png"))

		var height = viewportHeight
		var overflow = viewport.overflowPx
		var scrollerHeight = viewport.scrollerHeightPx
		var fullCapture: Capture? = null
		for (pass in 0 until MAX_GROW_PASSES) {
			if (overflow <= 0) break
			val step = if (fullCapture == null && pass == 0) overflow else maxOf(overflow, viewportHeight / 2)
			val nextHeight = minOf(height + step, MAX_HEIGHT_DP * SCALE)
			if (nextHeight == height) break
			val grown = capture(screen, variant, nextHeight, style)
			val stalled = grown.overflowPx >= overflow && grown.scrollerHeightPx <= scrollerHeight
			if (stalled) break
			fullCapture = grown
			height = nextHeight
			overflow = grown.overflowPx
			scrollerHeight = grown.scrollerHeightPx
		}

		val full = fullCapture?.let { "$base.full.png".also { path -> write(it.image, File(outDir, path)) } }
		// A partial render keeps old files, so drop a whole-screen shot this screen no longer needs.
		if (fullCapture == null) File(outDir, "$base.full.png").delete()
		File(outDir, "$base.text.tsv").writeText((fullCapture ?: viewport).textInventory)
		return Shot("$base.png", full, hiddenPx = maxOf(overflow, 0))
	}

	/**
	 * Captures the image, the largest remaining vertical scroll range in px, and a TSV of
	 * every text node's position and style (for auditing emphasis levels and greebles).
	 */
	private fun capture(screen: Screen, variant: Variant, heightPx: Int, style: StudyStyle): Capture {
		val ff = variant.formFactor
		lateinit var result: Capture
		runDesktopComposeUiTest(width = ff.widthDp * SCALE, height = heightPx) {
			// Infinite animations (cursor blink, spinners) never let the clock idle.
			mainClock.autoAdvance = false
			setContent {
				monoFamily = hammerMonoFontFamily()
				CompositionLocalProvider(
					LocalDensity provides Density(SCALE.toFloat()),
					LocalSystemTheme provides if (variant.dark) SystemTheme.Dark else SystemTheme.Light,
					LocalHdGrouping provides style.grouping,
				) {
					screen.content(ff)
				}
			}
			waitForIdle()
			// Let enter transitions finish so content isn't captured mid-fade.
			mainClock.advanceTimeBy(1_000)
			waitForIdle()

			val scroller = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange), useUnmergedTree = true)
				.fetchSemanticsNodes()
				.map { node ->
					val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
					val remaining = range.maxValue() - range.value()
					node to (if (remaining.isFinite()) ceil(remaining).toInt() else heightPx)
				}
				.maxByOrNull { it.second }
			val overflow = scroller?.second ?: 0
			val scrollerHeight = scroller?.first?.boundsInRoot?.height?.roundToInt() ?: 0
			result = Capture(onRoot().captureToImage().toAwtImage(), overflow, scrollerHeight, textInventory())
		}
		return result
	}

	private fun ComposeUiTest.textInventory(): String = buildString {
		appendLine("top_dp\tleft_dp\tfamily\tsize_sp\tweight\tletter_spacing\ttext")
		onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
			.fetchSemanticsNodes()
			.sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
			.forEach { node ->
				val text = node.config[SemanticsProperties.Text].joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
				if (text.isEmpty()) return@forEach
				val layouts = mutableListOf<TextLayoutResult>()
				node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
				val style = layouts.firstOrNull()?.layoutInput?.style
				val family = when (style?.fontFamily) {
					null -> "?"
					monoFamily -> "mono"
					else -> if ("Mono" in style.fontFamily.toString()) "mono" else "sans"
				}
				val b = node.boundsInRoot
				appendLine(
					listOf(
						(b.top / SCALE).roundToInt(), (b.left / SCALE).roundToInt(), family,
						style?.fontSize, style?.fontWeight?.weight, style?.letterSpacing, text,
					).joinToString("\t")
				)
			}
	}

	private fun write(image: BufferedImage, file: File) {
		file.parentFile.mkdirs()
		ImageIO.write(image, "png", file)
	}

	private fun indexHtml(rendered: Map<Pair<String, String>, Shot>, failures: List<String>): String = buildString {
		appendLine("<!doctype html><html><head><meta charset=\"utf-8\"><title>Hammer screen gallery</title><style>")
		appendLine(
			"""
			body{font:14px/1.4 system-ui,sans-serif;margin:24px;background:#f4f4f2;color:#222}
			nav a{margin-right:12px}
			section{margin:40px 0;border-top:1px solid #ccc;padding-top:16px}
			h3{font-size:13px;font-weight:600;color:#555;margin:16px 0 8px}
			.row{display:flex;gap:16px;align-items:flex-start;overflow-x:auto}
			figure{margin:0}figcaption{font-size:12px;color:#666;margin-bottom:4px}
			img{display:block;border:1px solid #bbb;background:#fff}
			img.phone{width:206px}img.tablet{width:640px}
			.note{color:#8a5a00}
			""".trimIndent()
		)
		appendLine("</style></head><body><h1>Hammer screen gallery</h1>")
		appendLine("<p><a href=\"grouping.html\">Grouping study</a></p>")
		appendLine("<p>Rendered from fake preview data at ${SCALE}x. Whole screen grows the canvas until nothing scrolls, so tall screens show in full; screens that still hide content say how much. As it first appears is the plain viewport. Click an image for full size.</p>")
		if (failures.isNotEmpty()) {
			appendLine("<h2>Failed renders</h2><ul>")
			failures.forEach { appendLine("<li>${esc(it)}</li>") }
			appendLine("</ul>")
		}
		val screens = screens.filter { s -> rendered.keys.any { it.first == s.name } }
		appendLine("<nav>")
		screens.forEach { appendLine("<a href=\"#${slug(it.name)}\">${esc(it.name)}</a>") }
		appendLine("</nav>")
		for (screen in screens) {
			appendLine("<section id=\"${slug(screen.name)}\"><h2>${esc(screen.name)}</h2>")
			screen.note?.let { appendLine("<p class=\"note\">${esc(it)}</p>") }
			appendLine("<h3>Whole screen</h3>")
			appendFigures(screen, rendered, fullLength = true) { it.full ?: it.viewport }
			if (variants.any { rendered[screen.name to it.dir]?.full != null }) {
				appendLine("<h3>As it first appears</h3>")
				appendFigures(screen, rendered) { it.viewport }
			}
			appendLine("</section>")
		}
		appendLine("</body></html>")
	}

	private fun groupingHtml(study: Map<Triple<String, String, StudyStyle>, Shot>): String = buildString {
		appendLine("<!doctype html><html><head><meta charset=\"utf-8\"><title>Hammer grouping study</title><style>")
		appendLine(
			"""
			body{font:14px/1.4 system-ui,sans-serif;margin:24px;background:#f4f4f2;color:#222}
			nav a{margin-right:12px}
			section{margin:40px 0;border-top:1px solid #ccc;padding-top:16px}
			h3{font-size:13px;font-weight:600;color:#555;margin:20px 0 8px}
			.row{display:flex;gap:16px;align-items:flex-start;overflow-x:auto}
			figure{margin:0}figcaption{font-size:12px;color:#666;margin-bottom:4px}
			img{display:block;border:1px solid #bbb;background:#fff}
			img.phone{width:260px}img.tablet{width:480px}
			""".trimIndent()
		)
		appendLine("</style></head><body><h1>Grouping study</h1>")
		appendLine("<p>Study styles: <b>Rules</b> (current); <b>HangingSpine</b> (cool-tinted regions with an ink spine, titles hung on wide screens, controls as wells). <a href=\"index.html\">All screens</a></p>")
		val names = screens.map { it.name }.filter { GROUPING_STUDY?.contains(it) ?: true }
		appendLine("<nav>")
		names.forEach { appendLine("<a href=\"#${slug(it)}\">${esc(it)}</a>") }
		appendLine("</nav>")
		for (name in names) {
			appendLine("<section id=\"${slug(name)}\"><h2>${esc(name)}</h2>")
			for (variant in variants) {
				appendLine("<h3>${esc(variant.label)}</h3><div class=\"row\">")
				for (style in STUDY_STYLES) {
					val shot = study[Triple(name, variant.dir, style)] ?: continue
					val src = shot.full ?: shot.viewport
					val cls = variant.formFactor.name.lowercase()
					appendLine("<figure><figcaption>${style.label}</figcaption><a href=\"$src\"><img class=\"$cls\" loading=\"lazy\" src=\"$src\"></a></figure>")
				}
				appendLine("</div>")
			}
			appendLine("</section>")
		}
		appendLine("</body></html>")
	}

	private fun StringBuilder.appendFigures(
		screen: Screen,
		rendered: Map<Pair<String, String>, Shot>,
		fullLength: Boolean = false,
		path: (Shot) -> String?,
	) {
		appendLine("<div class=\"row\">")
		for (variant in variants) {
			val shot = rendered[screen.name to variant.dir] ?: continue
			val src = path(shot) ?: continue
			val caption = if (fullLength && !shot.fullComplete) {
				"${variant.label} (${shot.hiddenPx / SCALE} dp still hidden)"
			} else {
				variant.label
			}
			val cls = variant.formFactor.name.lowercase()
			appendLine("<figure><figcaption>${esc(caption)}</figcaption><a href=\"$src\"><img class=\"$cls\" loading=\"lazy\" src=\"$src\"></a></figure>")
		}
		appendLine("</div>")
	}

	private fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

	private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

	companion object {
		const val ENABLED_PROPERTY = "hammer.screenGallery"
		const val OUT_DIR_PROPERTY = "hammer.screenGallery.outDir"
		const val ONLY_PROPERTY = "hammer.screenGallery.only"
		private const val SCALE = 2
		/** The first entry is the baseline, reused from the main gallery renders. */
		private val STUDY_STYLES = listOf(
			StudyStyle("Rules", HdGrouping.Rules),
			StudyStyle("HangingSpine", HdGrouping.HangingSpine),
		)
		private val GROUPING_STUDY: Set<String>? = setOf(
			"Project settings", "Account settings", "Project stats", "View encyclopedia entry",
			"Browse notes", "Scene editor", "Timeline",
		)
		private const val MAX_GROW_PASSES = 12
		private const val MAX_HEIGHT_DP = 8000
	}
}
