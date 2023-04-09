package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SyncLog(state: ProjectSync.State, scope: CoroutineScope) {
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
	entityConflict: ProjectSync.EntityConflict.SceneConflict,
	component: ProjectSync
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
	entityConflict: ProjectSync.EntityConflict.NoteConflict,
	component: ProjectSync
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
	entityConflict: ProjectSync.EntityConflict.TimelineEventConflict,
	component: ProjectSync
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
	entityConflict: ProjectSync.EntityConflict.EncyclopediaEntryConflict,
	component: ProjectSync
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
	entityConflict: ProjectSync.EntityConflict.SceneDraftConflict,
	component: ProjectSync
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