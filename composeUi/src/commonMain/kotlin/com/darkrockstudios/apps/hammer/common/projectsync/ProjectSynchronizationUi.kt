package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
internal fun ProjectSynchronization(component: ProjectSync) {
	MpDialog(
		title = "Project Synchronization",
		onCloseRequest = {},
		visible = true,
	) {
		val state by component.state.subscribeAsState()
		val scope = rememberCoroutineScope()

		LaunchedEffect(state.isSyncing) {
			if (state.isSyncing.not()) {
				component.syncProject()
			}
		}

		Column(modifier = Modifier.padding(Ui.Padding.L).fillMaxSize()) {
			Text("Project Synchronizing...")
			Spacer(modifier = Modifier.size(Ui.Padding.L))
			LinearProgressIndicator(
				progress = state.syncProgress,
				modifier = Modifier.fillMaxWidth()
			)
			if (state.syncProgress == 1f) {
				Button(onClick = component::endSync) {
					Text("Complete")
				}
			} else {
				Button(onClick = component::cancelSync) {
					Text("Cancel")
				}
			}
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
			} else {
				SyncLog(state, scope)
			}
		}
	}
}