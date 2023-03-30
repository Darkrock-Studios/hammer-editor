package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
internal fun ConflictResolution(state: ProjectHome.State, component: ProjectHome) {
	MpDialog(
		title = "Sync Conflict",
		onCloseRequest = {},
		visible = state.isSyncing,
	) {
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
			}
			if (state.entityConflict != null) {
				when (state.entityConflict) {
					is ProjectHome.EntityConflict.SceneConflict -> {
						val sceneConflict = state.entityConflict as ProjectHome.EntityConflict.SceneConflict
						SceneConflict(sceneConflict, component)
					}

					else -> {
						Text("Unknown Conflict")
					}
				}
			} else {
				SyncLog(state)
			}
		}
	}
}

@Composable
internal fun SyncLog(state: ProjectHome.State) {
	var showLog by rememberSaveable { mutableStateOf(true) }
	Column(modifier = Modifier.fillMaxWidth()) {
		Button(onClick = { showLog = !showLog }) {
			Text("Sync Log")
		}
		if (showLog) {
			LazyColumn(modifier = Modifier.fillMaxWidth()) {
				state.syncLog?.forEach { log ->
					item {
						Text(log)
					}
				}
			}
		}
	}
}

@Composable
internal fun SceneConflict(
	entityConflict: ProjectHome.EntityConflict.SceneConflict,
	component: ProjectHome
) {
	Column {
		Text("Scene Conflict")
		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.weight(1f)) {
				Text("Local Scene: ${entityConflict.clientScene.name}")
				Button(onClick = { component.resolveConflict(entityConflict.clientScene) }) {
					Text("Use Local")
				}
				Text(entityConflict.clientScene.content)
			}

			Column(modifier = Modifier.weight(1f)) {
				Text("Remote Scene: ${entityConflict.serverScene.name}")
				Button(onClick = { component.resolveConflict(entityConflict.serverScene) }) {
					Text("Use Remote")
				}
				Text(entityConflict.serverScene.content)
			}
		}
	}
}