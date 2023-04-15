package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import kotlinx.coroutines.launch

@Composable
fun ProjectsSyncDialog(component: ProjectsList, snackbarHostState: SnackbarHostState) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	MpDialog(
		onCloseRequest = {
			if (state.syncState.syncComplete) {
				component.hideProjectsSync()
			}
		},
		visible = state.syncState.showProjectSync,
		title = "Projects Sync"
	) {
		LaunchedEffect(state.syncState.showProjectSync) {
			if (state.syncState.showProjectSync) {
				component.syncProjects { success ->
					scope.launch {
						if (success) {
							snackbarHostState.showSnackbar("Projects synced")
						} else {
							snackbarHostState.showSnackbar("Failed to sync projects")
						}
					}
				}
			}
		}

		Column {
			Row(modifier = Modifier.fillMaxWidth()) {
				Text("Syncing Projects")

				Spacer(modifier = Modifier.weight(1f))

				if (state.syncState.syncComplete) {
					Button(onClick = { component.hideProjectsSync() }) {
						Text("Complete")
					}
				} else {
					Button(onClick = { component.cancelProjectsSync() }) {
						Text("Cancel")
					}
				}
			}

			val listState: LazyListState = rememberLazyListState()
			LazyColumn(state = listState) {
				val log = state.syncState.syncLog
				items(count = log.size, key = { it }) { index ->
					Text(log[index])
				}
			}

			LaunchedEffect(state.syncState.syncLog) {
				if (state.syncState.syncLog.isNotEmpty()) {
					scope.launch {
						listState.animateScrollToItem(state.syncState.syncLog.size - 1)
					}
				}
			}
		}
	}
}