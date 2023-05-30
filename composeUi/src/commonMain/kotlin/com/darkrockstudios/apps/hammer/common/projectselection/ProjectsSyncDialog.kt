package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import kotlinx.coroutines.launch

@Composable
fun ProjectsSyncDialog(component: ProjectsList) {
	val state by component.state.subscribeAsState()

	MpDialog(
		onCloseRequest = {
			if (state.syncState.syncComplete) {
				component.hideProjectsSync()
			}
		},
		visible = state.syncState.showProjectSync,
		size = DpSize(400.dp, 400.dp),
		title = "Synchronization"
	) {
		ProjectsSyncDialogContents(component)
	}
}

@Composable
internal fun ProjectsSyncDialogContents(
	component: ProjectsList,
) {
	val state by component.state.subscribeAsState()
	var showLog by rememberSaveable { mutableStateOf(false) }

	Column(modifier = Modifier.padding(Ui.Padding.L)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = CenterVertically
		) {
			Text(
				"Account Sync",
				style = MaterialTheme.typography.headlineSmall
			)

			Spacer(modifier = Modifier.weight(1f))
			LocalScreenCharacteristic.current.needsExplicitClose
			if (!state.syncState.syncComplete) {
				Icon(
					Icons.Default.Cancel,
					contentDescription = "Cancel",
					modifier = Modifier.padding(Ui.Padding.S).clickable { component.cancelProjectsSync() },
					tint = MaterialTheme.colorScheme.onBackground
				)
			}

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			if (state.syncState.syncComplete.not()) {
				CircularProgressIndicator(modifier = Modifier.size(16.dp))
			}

			Spacer(modifier = Modifier.size(Ui.Padding.M))

			Icon(
				Icons.Default.List,
				contentDescription = null,
				modifier = Modifier.padding(Ui.Padding.S).clickable { showLog = true },
				tint = MaterialTheme.colorScheme.onBackground
			)
		}

		val projectListState: LazyListState = rememberLazyListState()
		val projects = remember(state.syncState.projectsStatus) {
			state.syncState.projectsStatus.values.toList()
		}

		Box(modifier = Modifier.padding(Ui.Padding.M)) {
			LazyColumn(
				modifier = Modifier.heightIn(min = 128.dp),
				state = projectListState,
				contentPadding = PaddingValues(Ui.Padding.M)
			) {
				items(count = projects.size, key = { projects[it].projectName }) { index ->
					val projStatus = projects[index]
					ProjectStatusUi(projStatus)
				}
			}
		}
	}

	if (showLog) {
		SyncLog(component) { showLog = false }
	}
}

@Composable
fun SyncLog(component: ProjectsList, onClose: () -> Unit) {
	MpDialog(
		onCloseRequest = onClose,
		visible = true,
		title = "Sync Log",
	) {
		SyncLogContents(component)
	}
}

@Composable
fun SyncLogContents(component: ProjectsList) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	val logListState: LazyListState = rememberLazyListState()
	Box(modifier = Modifier.padding(Ui.Padding.L).fillMaxSize()) {
		LazyColumn(
			state = logListState,
			contentPadding = PaddingValues(4.dp),
			modifier = Modifier.fillMaxWidth()
		) {
			val log = state.syncState.syncLog
			items(count = log.size, key = { it }) { index ->
				val logMsg = log[index]
				SyncLogMessageUi(logMsg)
			}
		}
	}

	LaunchedEffect(state.syncState.syncLog) {
		if (state.syncState.syncLog.isNotEmpty()) {
			scope.launch {
				logListState.animateScrollToItem(state.syncState.syncLog.size - 1)
			}
		}
	}
}

@Composable
internal fun ProjectStatusUi(projectStatus: ProjectsList.ProjectSyncStatus) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(bottom = Ui.Padding.M),
		elevation = CardDefaults.elevatedCardElevation(4.dp),
	) {
		Row(modifier = Modifier.padding(Ui.Padding.M)) {
			ProjectStatusIcon(
				projectStatus.status,
				modifier = Modifier.size(24.dp).align(CenterVertically)
			)

			Column(modifier = Modifier.align(CenterVertically).padding(start = Ui.Padding.S)) {
				Text(projectStatus.projectName)
				LinearProgressIndicator(
					modifier = Modifier.fillMaxWidth(),
					progress = projectStatus.progress
				)
			}
		}
	}
}

@Composable
private fun ProjectStatusIcon(
	status: ProjectsList.Status,
	modifier: Modifier = Modifier,
) {
	when (status) {
		ProjectsList.Status.Pending -> {
			Icon(
				Icons.Default.HourglassTop,
				contentDescription = "Pending",
				modifier = modifier
			)
		}

		ProjectsList.Status.Syncing -> {
			CircularProgressIndicator(modifier = modifier)
		}

		ProjectsList.Status.Failed -> {
			Icon(
				Icons.Default.Error,
				contentDescription = "Error",
				tint = MaterialTheme.colorScheme.error,
				modifier = modifier
			)
		}

		ProjectsList.Status.Complete -> {
			Icon(
				Icons.Default.CheckCircle,
				contentDescription = "Sync Complete",
				tint = Color.Green,
				modifier = modifier
			)
		}

		ProjectsList.Status.Canceled -> {
			Icon(
				Icons.Default.Cancel,
				contentDescription = "Canceled",
				modifier = modifier
			)
		}
	}
}