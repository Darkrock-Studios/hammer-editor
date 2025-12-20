package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.projectselection.SyncLogMessageUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun ProjectSynchronization(
	component: ProjectSynchronization,
	showSnackbar: (String) -> Unit
) {
	val state by component.state.subscribeAsState()

	SimpleDialog(
		title = Res.string.sync_project_dialog_title.get(),
		onCloseRequest = { if (state.isSyncing.not()) component.endSync() },
		visible = true,
		modifier = Modifier.fillMaxSize(0.9f),
		overridePlatformWidth = true
	) {
		val screenCharacteristics = calculateWindowSizeClass()
		ProjectSynchronizationContent(component, showSnackbar, screenCharacteristics)
	}
}

@Composable
internal fun ProjectSynchronizationContent(
	component: ProjectSynchronization,
	showSnackbar: (String) -> Unit,
	screenCharacteristics: WindowSizeClass
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val snackbarHostState = remember { SnackbarHostState() }

	LaunchedEffect(Unit) {
		component.syncProject { success ->
			scope.launch {
				if (success) {
					showSnackbar(strRes.get(Res.string.sync_toast_success))
				} else {
					showSnackbar(strRes.get(Res.string.sync_toast_failed))
				}
			}
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL)) {
		Row {
			if (state.isSyncing) {
				Text(
					Res.string.sync_status_in_progress.get(),
					style = MaterialTheme.typography.headlineSmall
				)
			} else {
				if (state.failed) {
					Text(
						Res.string.sync_status_failed.get(),
						style = MaterialTheme.typography.headlineSmall
					)
				} else {
					Text(
						Res.string.sync_status_success.get(),
						style = MaterialTheme.typography.headlineSmall
					)
				}
			}

			Spacer(modifier = Modifier.weight(1f))

			if (state.isSyncing) {
				Icon(
					Icons.Default.Cancel,
					contentDescription = Res.string.sync_cancel_button.get(),
					modifier = Modifier.padding(Ui.Padding.S).clickable { component.cancelSync() },
					tint = MaterialTheme.colorScheme.onBackground
				)
			}

			Icon(
				Icons.Default.List,
				contentDescription = null,
				modifier = Modifier.padding(Ui.Padding.S).clickable { component.showLog(!state.showLog) },
				tint = MaterialTheme.colorScheme.onBackground
			)
		}

		Spacer(modifier = Modifier.size(Ui.Padding.L))

		LinearProgressIndicator(
			progress = state.syncProgress,
			modifier = Modifier.fillMaxWidth()
		)

		Spacer(modifier = Modifier.size(Ui.Padding.M))

		Column(modifier = Modifier.fillMaxSize()) {
			Spacer(modifier = Modifier.size(Ui.Padding.L))

			val conflict = state.entityConflict
			if (conflict != null) {
				Box(
					modifier = Modifier.fillMaxWidth(),
					contentAlignment = Alignment.Center
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(
							Icons.Default.Warning,
							contentDescription = Res.string.sync_conflict_icon_description.get(),
							modifier = Modifier.size(32.dp),
							tint = MaterialTheme.colorScheme.error
						)

						Text(
							text = state.conflictTitle?.get() ?: "error",
							style = MaterialTheme.typography.headlineSmall,
							modifier = Modifier.padding(start = Ui.Padding.L)
						)

						val infoMessage = Res.string.sync_conflict_merge_explained.get()
						Icon(
							Icons.Default.Info,
							contentDescription = infoMessage,
							modifier = Modifier
								.padding(start = Ui.Padding.M)
								.clickable {
									scope.launch {
										snackbarHostState.showSnackbar(infoMessage)
									}
								},
							tint = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}

				when (conflict) {
					is ProjectSynchronization.EntityConflict.SceneConflict -> {
						val sceneConflict = state.entityConflict as ProjectSynchronization.EntityConflict.SceneConflict
						SceneConflict(sceneConflict, component, screenCharacteristics)
					}

					is ProjectSynchronization.EntityConflict.NoteConflict -> {
						val noteConflict = state.entityConflict as ProjectSynchronization.EntityConflict.NoteConflict
						NoteConflict(noteConflict, component, screenCharacteristics)
					}

					is ProjectSynchronization.EntityConflict.TimelineEventConflict -> {
						val timelineEventConflict =
							state.entityConflict as ProjectSynchronization.EntityConflict.TimelineEventConflict
						TimelineEventConflict(timelineEventConflict, component, screenCharacteristics)
					}

					is ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict -> {
						val encyclopediaEntryConflict =
							state.entityConflict as ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict
						EncyclopediaEntryConflict(encyclopediaEntryConflict, component, screenCharacteristics)
					}

					is ProjectSynchronization.EntityConflict.SceneDraftConflict -> {
						val sceneDraftConflict =
							state.entityConflict as ProjectSynchronization.EntityConflict.SceneDraftConflict
						SceneDraftConflict(sceneDraftConflict, component, screenCharacteristics)
					}
				}
			} else if (state.showLog) {
				SyncLog(state, scope)
			}
		}
	}

		SnackbarHost(
			hostState = snackbarHostState,
			modifier = Modifier.align(Alignment.BottomCenter)
		)
	}
}

@Composable
internal fun SyncLog(state: ProjectSynchronization.State, scope: CoroutineScope) {

	val listState: LazyListState = rememberLazyListState()
	LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
		items(count = state.syncLog.size, key = { it }) { index ->
			SyncLogMessageUi(state.syncLog[index], false)
		}
	}

	LaunchedEffect(state.syncLog) {
		if (state.syncLog.isNotEmpty()) {
			scope.launch {
				listState.animateScrollToItem(state.syncLog.lastIndex)
			}
		}
	}
}