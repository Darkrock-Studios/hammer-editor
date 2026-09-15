package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.projects.WearProjects
import kotlin.math.roundToInt

@Composable
fun WearProjectsUi(component: WearProjects) {
	val state by component.state.subscribeAsState()
	WearProjectsScreen(
		state = state,
		onToggleSubscription = component::toggleSubscription,
		onSyncNow = component::syncNow,
		onShowSyncLog = component::showSyncLog,
		onSignOut = component::signOut,
	)
}

@Composable
fun WearProjectsScreen(
	state: WearProjects.State,
	onToggleSubscription: (projectName: String) -> Unit,
	onSyncNow: () -> Unit,
	onShowSyncLog: () -> Unit,
	onSignOut: () -> Unit,
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
		TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
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
					enabled = row.canSubscribe,
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
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.projects_sign_out)) },
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
