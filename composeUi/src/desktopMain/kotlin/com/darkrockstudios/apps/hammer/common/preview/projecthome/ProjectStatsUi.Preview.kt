package com.darkrockstudios.apps.hammer.common.preview.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHomeContentRouter
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.EntryAppearance
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.dummyBackHandler
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectStatsUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.StringResource

@Preview
@Composable
fun ScreenProjectStatsUiPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.fillMaxSize(),
			) {
				ProjectStatsUi(
					modifier = Modifier,
					component = component,
					scope = scope,
				)
			}
		}
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenProjectStatsUiTabletPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		TabletPreviewSurface {
			ProjectStatsUi(
				modifier = Modifier,
				component = component,
				scope = scope,
			)
		}
	}
}

private val component = object : ProjectHome {
	override val state: Value<ProjectHome.State> = MutableValue(
		ProjectHome.State(
			projectDef = fakeProjectDef(),
			created = "12 Jun 2024",
			numberOfScenes = 24,
			totalWords = 41_200,
			wordsByChapter = mapOf(1 to 5400, 2 to 6100, 3 to 4800, 4 to 7200),
			encyclopediaEntriesByType = mapOf(
				EntryType.PERSON to 8,
				EntryType.PLACE to 5,
				EntryType.THING to 3,
			),
			longestSceneId = 4,
			longestSceneName = "The Wreck",
			longestSceneWords = 3100,
			lastEditedSceneId = 7,
			lastEditedSceneName = "The Lamp Room",
			shortestSceneWords = 320,
			medianSceneWords = 1650,
			sceneWordsStdDev = 540,
			numberOfNotes = 12,
			numberOfTimelineEvents = 9,
		)
	)
	override val contentRouterState: Value<ChildStack<ProjectHomeContentRouter.Config, ProjectHome.ContentDestination>> =
		MutableValue(
			ChildStack(
				ProjectHomeContentRouter.Config.Stats,
				ProjectHome.ContentDestination.Stats,
			)
		)

	override val backHandler = dummyBackHandler
	override val toast = MutableSharedFlow<ToastMessage>()

	override suspend fun exportProject(path: String, options: ExportOptions): HPath = fakeProjectDef().path
	override suspend fun exportProjectToFile(filePath: String, options: ExportOptions): HPath = fakeProjectDef().path
	override fun beginProjectExport() {}
	override fun cancelExportDialog() {}
	override fun confirmExportDialog(options: ExportOptions) {}
	override fun endProjectExport() {}
	override fun startProjectSync() {}
	override fun showGlobalSearch() {}
	override fun showGlobalSearchForTag(tag: String) {}
	override fun showLongestScene() {}
	override fun showLastEditedScene() {}
	override fun showEntry(entry: EntryAppearance) {}
	override fun supportsBackup(): Boolean = true
	override fun createBackup(callback: (ProjectBackupDef?) -> Unit) {}
	override fun getExportStoryFileName(format: ExportFormat): String = "story"
	override fun refreshStatistics() {}
	override fun showProjectStats() {}
	override fun showProjectSettings() {}
	override fun closeProject() {}
	override fun onBack() {}
	override fun isAtRoot(): Boolean = true
	override fun shouldConfirmClose(): Set<CloseConfirm> = emptySet()

	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
	override fun showToast(scope: CoroutineScope, message: String) {}
	override fun showToast(scope: CoroutineScope, message: Msg) {}
	override suspend fun showToast(message: StringResource, vararg params: Any) {}
	override suspend fun showToast(message: String) {}
	override suspend fun showToast(message: Msg) {}
}
