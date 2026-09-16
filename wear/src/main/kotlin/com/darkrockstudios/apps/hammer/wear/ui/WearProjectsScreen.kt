package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.wear.CaptureActivity
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.capture.Capture
import com.darkrockstudios.apps.hammer.wear.components.projects.WearProjects
import kotlin.math.roundToInt

@Composable
fun WearProjectsUi(component: WearProjects) {
	val state by component.state.subscribeAsState()
	val context = LocalContext.current
	WearProjectsScreen(
		state = state,
		onNewNote = { context.startActivity(CaptureActivity.intent(context, Capture.Mode.Note)) },
		onNewIdea = { context.startActivity(CaptureActivity.intent(context, Capture.Mode.Idea)) },
		onToggleSubscription = component::toggleSubscription,
		onSyncNow = component::syncNow,
		onShowSyncLog = component::showSyncLog,
		onSignOut = component::signOut,
		onConfirmSignOut = component::confirmSignOut,
		onCancelSignOut = component::cancelSignOut,
		onDismissNotice = component::dismissNotice,
	)
}

@Composable
fun WearProjectsScreen(
	state: WearProjects.State,
	onNewNote: () -> Unit,
	onNewIdea: () -> Unit,
	onToggleSubscription: (projectName: String) -> Unit,
	onSyncNow: () -> Unit,
	onShowSyncLog: () -> Unit,
	onSignOut: () -> Unit,
	onConfirmSignOut: () -> Unit,
	onCancelSignOut: () -> Unit,
	onDismissNotice: () -> Unit,
) {
	val warning = state.signOutWarning
	if (warning != null) {
		SignOutWarningContent(warning = warning, onConfirm = onConfirmSignOut, onCancel = onCancelSignOut)
	} else {
		ProjectsContent(
			state = state,
			onNewNote = onNewNote,
			onNewIdea = onNewIdea,
			onToggleSubscription = onToggleSubscription,
			onSyncNow = onSyncNow,
			onShowSyncLog = onShowSyncLog,
			onSignOut = onSignOut,
			onDismissNotice = onDismissNotice,
		)
	}
}

@Composable
private fun ProjectsContent(
	state: WearProjects.State,
	onNewNote: () -> Unit,
	onNewIdea: () -> Unit,
	onToggleSubscription: (projectName: String) -> Unit,
	onSyncNow: () -> Unit,
	onShowSyncLog: () -> Unit,
	onSignOut: () -> Unit,
	onDismissNotice: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()

	ScreenScaffold(
		scrollState = listState,
		edgeButton = {
			EdgeButton(onClick = onSyncNow, enabled = !state.syncing) {
				if (state.syncing) {
					CircularProgressIndicator(modifier = Modifier.size(24.dp))
				} else {
					Text(stringResource(R.string.projects_sync_now))
				}
			}
		},
	) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = screenContentPadding(contentPadding)) {
			item {
				ListHeader { Text(stringResource(R.string.projects_header)) }
			}
			state.accountEmail?.let { email ->
				item {
					Text(
						text = stringResource(R.string.projects_signed_in_as, email),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
						maxLines = 1,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
			item {
				FilledTonalButton(
					onClick = onNewNote,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.projects_new_note)) },
				)
			}
			item {
				FilledTonalButton(
					onClick = onNewIdea,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.projects_new_idea)) },
				)
			}
			if (state.needsReauth || state.lastSyncFailed) {
				item {
					Text(
						text = stringResource(
							if (state.needsReauth) R.string.projects_needs_reauth else R.string.projects_last_sync_failed
						),
						color = MaterialTheme.colorScheme.error,
						textAlign = TextAlign.Center,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
			state.notice?.let { notice ->
				item {
					Text(
						text = when (notice.reason) {
							WearProjects.Notice.Reason.UnsyncedKept ->
								stringResource(R.string.projects_unsynced_kept, notice.projectName)

							WearProjects.Notice.Reason.Failed ->
								stringResource(R.string.projects_unsubscribe_failed, notice.projectName)
						},
						color = when (notice.reason) {
							WearProjects.Notice.Reason.UnsyncedKept -> MaterialTheme.colorScheme.tertiary
							WearProjects.Notice.Reason.Failed -> MaterialTheme.colorScheme.error
						},
						textAlign = TextAlign.Center,
						modifier = Modifier.fillMaxWidth(),
					)
				}
				item {
					FilledTonalButton(
						onClick = onDismissNotice,
						modifier = Modifier.fillMaxWidth(),
						label = { Text(stringResource(R.string.projects_notice_dismiss)) },
					)
				}
			}
			if (state.projects.isEmpty()) {
				item {
					Text(
						text = stringResource(if (state.loaded) R.string.projects_empty else R.string.projects_loading),
						textAlign = TextAlign.Center,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
			items(count = state.projects.size, key = { state.projects[it].name }) { index ->
				val row = state.projects[index]
				SwitchButton(
					checked = row.subscribed,
					onCheckedChange = { onToggleSubscription(row.name) },
					enabled = row.canSubscribe && !row.unsubscribing,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(row.name, maxLines = 1) },
					secondaryLabel = { Text(projectStatus(row), maxLines = 1) },
				)
			}
			item {
				FilledTonalButton(
					onClick = onShowSyncLog,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.projects_sync_log)) },
				)
			}
			item {
				FilledTonalButton(
					onClick = onSignOut,
					enabled = !state.checkingSignOut,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.projects_sign_out)) },
					icon = if (state.checkingSignOut) {
						{ CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
					} else {
						null
					},
				)
			}
		}
	}
}

@Composable
private fun SignOutWarningContent(
	warning: WearProjects.SignOutWarning,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()

	ScreenScaffold(
		scrollState = listState,
		edgeButton = {
			EdgeButton(onClick = onCancel) {
				Text(stringResource(R.string.projects_sign_out_cancel))
			}
		},
	) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = screenContentPadding(contentPadding)) {
			item {
				ListHeader { Text(stringResource(R.string.projects_sign_out)) }
			}
			item {
				Text(
					text = if (warning.items > 0) {
						stringResource(R.string.projects_sign_out_warning_count, warning.items)
					} else {
						stringResource(R.string.projects_sign_out_warning_unknown)
					},
					color = MaterialTheme.colorScheme.error,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth(),
				)
			}
			item {
				FilledTonalButton(
					onClick = onConfirm,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.projects_sign_out_confirm)) },
				)
			}
		}
	}
}

@Composable
private fun projectStatus(row: WearProjects.ProjectRow): String {
	val progress = row.progress
	return when {
		!row.canSubscribe -> stringResource(R.string.project_status_not_on_server)
		row.unsubscribing -> stringResource(R.string.project_status_unsubscribing)
		row.outcome == null && progress != null ->
			stringResource(R.string.project_status_syncing, (progress * 100).roundToInt())

		row.outcome == ProjectSyncOutcome.Success || row.outcome == ProjectSyncOutcome.Unchanged ->
			stringResource(R.string.project_status_synced)

		row.outcome == ProjectSyncOutcome.NeedsResolution -> stringResource(R.string.project_status_needs_resolution)
		row.outcome == ProjectSyncOutcome.Failed -> stringResource(R.string.project_status_failed)
		row.subscribed -> stringResource(R.string.project_status_on_watch)
		else -> stringResource(R.string.project_status_not_on_watch)
	}
}
