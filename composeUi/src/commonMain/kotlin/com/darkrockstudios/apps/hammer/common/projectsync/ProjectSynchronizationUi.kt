package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
internal fun ProjectSynchronization(
	component: ProjectSync,
	showSnackbar: (String) -> Unit
) {
	val state by component.state.subscribeAsState()
	var showLog by rememberSaveable { mutableStateOf(false) }

	MpDialog(
		title = "Project Synchronization",
		onCloseRequest = { if (state.isSyncing.not()) component.endSync() },
		visible = true,
		size = DpSize(width = 1024.dp, height = 768.dp)
	) {
		val scope = rememberCoroutineScope()

		LaunchedEffect(Unit) {
			component.syncProject { success ->
				if (success) {
					showSnackbar("Project Sync Complete")
				} else {
					showSnackbar("Project Sync Failed")
				}
			}
		}

		Column(modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL)) {
			Row {
				if (state.isSyncing) {
					Text(
						"Synchronizing...",
						style = MaterialTheme.typography.headlineSmall
					)
				} else {
					Text(
						"Sync Complete",
						style = MaterialTheme.typography.headlineSmall
					)
				}

				Spacer(modifier = Modifier.weight(1f))

				if (state.isSyncing) {
					Icon(
						Icons.Default.Cancel,
						contentDescription = "Cancel",
						modifier = Modifier.padding(Ui.Padding.S).clickable { component.cancelSync() },
						tint = MaterialTheme.colorScheme.onBackground
					)
				}

				Icon(
					Icons.Default.List,
					contentDescription = null,
					modifier = Modifier.padding(Ui.Padding.S).clickable { showLog = !showLog },
					tint = MaterialTheme.colorScheme.onBackground
				)
			}

			Spacer(modifier = Modifier.size(Ui.Padding.L))

			LinearProgressIndicator(
				progress = state.syncProgress,
				modifier = Modifier.fillMaxWidth()
			)

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			val conflict = state.entityConflict
			if (conflict != null) {
				when (conflict) {
					is ProjectSync.EntityConflict.SceneConflict -> {
						val sceneConflict = state.entityConflict as ProjectSync.EntityConflict.SceneConflict
						SceneConflict(sceneConflict, component)
					}

					is ProjectSync.EntityConflict.NoteConflict -> {
						val noteConflict = state.entityConflict as ProjectSync.EntityConflict.NoteConflict
						NoteConflict(noteConflict, component)
					}

					is ProjectSync.EntityConflict.TimelineEventConflict -> {
						val timelineEventConflict =
							state.entityConflict as ProjectSync.EntityConflict.TimelineEventConflict
						TimelineEventConflict(timelineEventConflict, component)
					}

					is ProjectSync.EntityConflict.EncyclopediaEntryConflict -> {
						val encyclopediaEntryConflict =
							state.entityConflict as ProjectSync.EntityConflict.EncyclopediaEntryConflict
						EncyclopediaEntryConflict(encyclopediaEntryConflict, component)
					}

					is ProjectSync.EntityConflict.SceneDraftConflict -> {
						val sceneDraftConflict =
							state.entityConflict as ProjectSync.EntityConflict.SceneDraftConflict
						SceneDraftConflict(sceneDraftConflict, component)
					}
				}
			} else if (showLog) {
				SyncLog(state, scope)
			}
		}
	}
}