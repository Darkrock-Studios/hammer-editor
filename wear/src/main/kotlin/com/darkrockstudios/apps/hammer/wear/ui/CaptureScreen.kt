package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.wear.compose.material3.Text
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.capture.Capture

@Composable
fun CaptureScreen(
	state: Capture.State,
	onEditText: () -> Unit,
	onShowProjectPicker: () -> Unit,
	onSelectProject: (String) -> Unit,
	onDismissProjectPicker: () -> Unit,
	onSave: () -> Unit,
	onDone: () -> Unit,
) {
	val outcome = state.outcome
	when {
		outcome != null -> CaptureOutcomeContent(outcome = outcome, onDone = onDone)
		state.pickingProject -> ProjectPickerContent(
			state = state,
			onSelectProject = onSelectProject,
			onCancel = onDismissProjectPicker,
		)
		else -> CaptureEntryContent(
			state = state,
			onEditText = onEditText,
			onShowProjectPicker = onShowProjectPicker,
			onSave = onSave,
		)
	}
}

@Composable
private fun CaptureEntryContent(
	state: Capture.State,
	onEditText: () -> Unit,
	onShowProjectPicker: () -> Unit,
	onSave: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()
	val notSet = stringResource(R.string.capture_text_empty)

	ScreenScaffold(
		scrollState = listState,
		edgeButton = {
			EdgeButton(onClick = onSave, enabled = state.canSave) {
				if (state.saving) {
					CircularProgressIndicator(modifier = Modifier.size(24.dp))
				} else {
					Text(stringResource(R.string.capture_save))
				}
			}
		},
	) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = screenContentPadding(contentPadding)) {
			item {
				ListHeader {
					Text(
						stringResource(
							if (state.mode == Capture.Mode.Idea) R.string.capture_title_idea else R.string.capture_title_note
						)
					)
				}
			}
			item {
				FilledTonalButton(
					onClick = onEditText,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.capture_text_label)) },
					secondaryLabel = { Text(state.text.ifBlank { notSet }, maxLines = 3) },
				)
			}
			if (state.mode == Capture.Mode.Note) {
				item {
					FilledTonalButton(
						onClick = onShowProjectPicker,
						enabled = state.projects.size > 1,
						modifier = Modifier.fillMaxWidth(),
						label = { Text(stringResource(R.string.capture_project_label)) },
						secondaryLabel = {
							Text(
								state.projectName ?: stringResource(
									if (state.loading) R.string.capture_project_loading else R.string.capture_project_none
								),
								maxLines = 1,
							)
						},
					)
				}
			}
		}
	}
}

@Composable
private fun ProjectPickerContent(
	state: Capture.State,
	onSelectProject: (String) -> Unit,
	onCancel: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()

	ScreenScaffold(
		scrollState = listState,
		edgeButton = {
			EdgeButton(onClick = onCancel) { Text(stringResource(R.string.capture_project_keep)) }
		},
	) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = screenContentPadding(contentPadding)) {
			item {
				ListHeader { Text(stringResource(R.string.capture_project_label)) }
			}
			items(count = state.projects.size, key = { state.projects[it] }) { index ->
				val name = state.projects[index]
				FilledTonalButton(
					onClick = { onSelectProject(name) },
					modifier = Modifier.fillMaxWidth(),
					label = { Text(name, maxLines = 2) },
				)
			}
		}
	}
}

@Composable
private fun CaptureOutcomeContent(
	outcome: Capture.Outcome,
	onDone: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()

	ScreenScaffold(
		scrollState = listState,
		edgeButton = {
			EdgeButton(onClick = onDone) { Text(stringResource(R.string.capture_done)) }
		},
	) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = screenContentPadding(contentPadding)) {
			item {
				ListHeader {
					Text(
						stringResource(
							when (outcome) {
								is Capture.Outcome.Saved -> R.string.capture_saved
								Capture.Outcome.NoProjects -> R.string.capture_no_projects_title
								Capture.Outcome.Failed -> R.string.capture_failed
							}
						)
					)
				}
			}
			item {
				Text(
					text = when (outcome) {
						is Capture.Outcome.Saved -> when {
							outcome.pending == null -> stringResource(R.string.capture_pending_checking)
							outcome.pending > 0 -> stringResource(R.string.capture_pending_count, outcome.pending)
							else -> stringResource(R.string.capture_pending_none)
						}

						Capture.Outcome.NoProjects -> stringResource(R.string.capture_no_projects_body)
						Capture.Outcome.Failed -> stringResource(R.string.capture_failed_body)
					},
					color = when (outcome) {
						is Capture.Outcome.Saved -> MaterialTheme.colorScheme.onSurfaceVariant
						else -> MaterialTheme.colorScheme.error
					},
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth(),
				)
			}
		}
	}
}
