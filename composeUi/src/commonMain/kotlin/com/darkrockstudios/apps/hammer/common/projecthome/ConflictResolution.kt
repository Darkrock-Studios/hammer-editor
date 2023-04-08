package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ProjectSynchronization(state: ProjectHome.State, component: ProjectHome) {
	MpDialog(
		title = "Project Synchronization",
		onCloseRequest = {},
		visible = state.isSyncing,
	) {
		val scope = rememberCoroutineScope()

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
			val conflict = state.entityConflict
			if (conflict != null) {
				when (conflict) {
					is ProjectHome.EntityConflict.SceneConflict -> {
						val sceneConflict = state.entityConflict as ProjectHome.EntityConflict.SceneConflict
						SceneConflict(sceneConflict, component)
					}

					is ProjectHome.EntityConflict.NoteConflict -> {
						val noteConflict = state.entityConflict as ProjectHome.EntityConflict.NoteConflict
						NoteConflict(noteConflict, component)
					}

					is ProjectHome.EntityConflict.TimelineEventConflict -> {
						val timelineEventConflict =
							state.entityConflict as ProjectHome.EntityConflict.TimelineEventConflict
						TimelineEventConflict(timelineEventConflict, component)
					}

					is ProjectHome.EntityConflict.EncyclopediaEntryConflict -> {
						val encyclopediaEntryConflict =
							state.entityConflict as ProjectHome.EntityConflict.EncyclopediaEntryConflict
						EncyclopediaEntryConflict(encyclopediaEntryConflict, component)
					}

					is ProjectHome.EntityConflict.SceneDraftConflict -> {
						val sceneDraftConflict =
							state.entityConflict as ProjectHome.EntityConflict.SceneDraftConflict
						SceneDraftConflict(sceneDraftConflict, component)
					}
				}
			} else {
				SyncLog(state, scope)
			}
		}
	}
}

@Composable
internal fun SyncLog(state: ProjectHome.State, scope: CoroutineScope) {
	var showLog by rememberSaveable { mutableStateOf(true) }
	Column(modifier = Modifier.fillMaxWidth()) {
		Button(onClick = { showLog = !showLog }) {
			Text("Sync Log")
		}
		if (showLog) {
			val listState: LazyListState = rememberLazyListState()
			LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
				items(count = state.syncLog.size, key = { it }) { index ->
					Text(state.syncLog[index])
				}
			}

			LaunchedEffect(state.syncLog) {
				if (state.syncLog.isNotEmpty()) {
					scope.launch {
						listState.animateScrollToItem(state.syncLog.size - 1)
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
				Text("Local Scene: ${entityConflict.clientEntity.name}")
				Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
					Text("Use Local")
				}
				Text(entityConflict.clientEntity.content)
			}

			Column(modifier = Modifier.weight(1f)) {
				Text("Remote Scene: ${entityConflict.serverEntity.name}")
				Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
					Text("Use Remote")
				}
				Text(entityConflict.serverEntity.content)
			}
		}
	}
}

@Composable
internal fun NoteConflict(
	entityConflict: ProjectHome.EntityConflict.NoteConflict,
	component: ProjectHome
) {
	Column {
		Text("Note Conflict")
		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.weight(1f)) {
				Text("Local Note: ${entityConflict.clientEntity.content}")
				Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
					Text("Use Local")
				}
				Text(entityConflict.clientEntity.content)
			}

			Column(modifier = Modifier.weight(1f)) {
				Text("Remote Note: ${entityConflict.serverEntity.content}")
				Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
					Text("Use Remote")
				}
				Text(entityConflict.serverEntity.content)
			}
		}
	}
}

@Composable
internal fun TimelineEventConflict(
	entityConflict: ProjectHome.EntityConflict.TimelineEventConflict,
	component: ProjectHome
) {
	Column {
		Text("Timeline Event Conflict")
		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.weight(1f)) {
				Text("Local Event: ${entityConflict.clientEntity.content}")
				Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
					Text("Use Local")
				}
				Text(entityConflict.clientEntity.content)
			}

			Column(modifier = Modifier.weight(1f)) {
				Text("Remote Event: ${entityConflict.serverEntity.content}")
				Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
					Text("Use Remote")
				}
				Text(entityConflict.serverEntity.content)
			}
		}
	}
}

@Composable
internal fun EncyclopediaEntryConflict(
	entityConflict: ProjectHome.EntityConflict.EncyclopediaEntryConflict,
	component: ProjectHome
) {
	Column {
		Text("Encyclopedia Entry Conflict")
		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.weight(1f)) {
				Text("Local Event: ${entityConflict.clientEntity.name}")
				Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
					Text("Use Local")
				}
				Text(entityConflict.clientEntity.text)
			}

			Column(modifier = Modifier.weight(1f)) {
				Text("Remote Event: ${entityConflict.serverEntity.name}")
				Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
					Text("Use Remote")
				}
				Text(entityConflict.serverEntity.text)
			}
		}
	}
}

@Composable
internal fun SceneDraftConflict(
	entityConflict: ProjectHome.EntityConflict.SceneDraftConflict,
	component: ProjectHome
) {
	Column {
		Text("Scene Draft Conflict")
		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.weight(1f)) {
				Text("Local Draft: ${entityConflict.clientEntity.name}")
				Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
					Text("Use Local")
				}
				Text(entityConflict.clientEntity.content)
			}

			Column(modifier = Modifier.weight(1f)) {
				Text("Remote Draft: ${entityConflict.serverEntity.name}")
				Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
					Text("Use Remote")
				}
				Text(entityConflict.serverEntity.content)
			}
		}
	}
}