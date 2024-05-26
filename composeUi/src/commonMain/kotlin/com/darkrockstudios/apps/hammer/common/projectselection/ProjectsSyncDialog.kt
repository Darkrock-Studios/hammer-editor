package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import kotlinx.coroutines.launch

@Composable
fun ProjectsSyncDialog(component: ProjectsList) {
	val state by component.state.subscribeAsState()

	SimpleDialog(
		onCloseRequest = {
			if (state.syncState.syncComplete) {
				component.hideProjectsSync()
			}
		},
		visible = state.syncState.showProjectSync,
		modifier = Modifier.size(400.dp), // TODO this size is a hold over from the old Dialog, maybe we dont need it
		title = MR.strings.account_sync_dialog_title.get()
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
				MR.strings.account_sync_dialog_header.get(),
				style = MaterialTheme.typography.headlineSmall
			)

			Spacer(modifier = Modifier.weight(1f))
			LocalScreenCharacteristic.current.needsExplicitClose
			if (!state.syncState.syncComplete) {
				Icon(
					Icons.Default.Cancel,
					contentDescription = MR.strings.account_sync_dialog_cancel_button.get(),
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
	SimpleDialog(
		onCloseRequest = onClose,
		visible = true,
		title = MR.strings.account_sync_log_title.get(),
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
				contentDescription = MR.strings.account_sync_dialog_status_pending.get(),
				modifier = modifier
			)
		}

		ProjectsList.Status.Syncing -> {
			CircularProgressIndicator(modifier = modifier)
		}

		ProjectsList.Status.Failed -> {
			Icon(
				Icons.Default.Error,
				contentDescription = MR.strings.account_sync_dialog_status_error.get(),
				tint = MaterialTheme.colorScheme.error,
				modifier = modifier
			)
		}

		ProjectsList.Status.Complete -> {
			Icon(
				Icons.Default.CheckCircle,
				contentDescription = MR.strings.account_sync_dialog_status_pending.get(),
				tint = Color.Green,
				modifier = modifier
			)
		}

		ProjectsList.Status.Canceled -> {
			Icon(
				Icons.Default.Cancel,
				contentDescription = MR.strings.account_sync_dialog_status_canceled.get(),
				modifier = modifier
			)
		}
	}
}