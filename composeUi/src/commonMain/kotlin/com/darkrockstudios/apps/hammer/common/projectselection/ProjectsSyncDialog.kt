package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import kotlinx.coroutines.launch

@Composable
fun ProjectsSyncDialog(component: ProjectSelection, snackbarHostState: SnackbarHostState) {
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
							component.hideProjectsSync()
							snackbarHostState.showSnackbar("Projects synced")
						} else {
							snackbarHostState.showSnackbar("Failed to sync projects")
						}
					}
				}
			}
		}

		Column {
			Text("Syncing Projects")

			LazyColumn {
				val log = state.syncState.syncLog
				items(count = log.size, key = { log[it].hashCode() }) { index ->
					Text(log[index])
				}
			}

			if (state.syncState.syncComplete) {
				Button(onClick = { component.hideProjectsSync() }) {
					Text("Complete")
				}
			}
		}
	}
}