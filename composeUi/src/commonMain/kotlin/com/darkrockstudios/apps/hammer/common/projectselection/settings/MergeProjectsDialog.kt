package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.ui.Alignment
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationActions
import com.darkrockstudios.apps.hammer.common.compose.IndexStripCard
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import androidx.compose.foundation.layout.fillMaxSize
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.resources.get

private val DialogMaxWidth = 480.dp

enum class MergeMode {
	Merge,
	Replace,
}

/**
 * Raised in place of the server setup dialog when logging in to a new server would merge real local
 * work into it. A sibling of [ServerSetupDialog] and [TermsOfServiceDialog] rather than a child, so
 * the two never stack.
 */
@Composable
fun MergeProjectsDialog(component: AccountSettings) {
	val state by component.state.subscribeAsState()

	var renderInternal by remember { mutableStateOf(state.mergePrompt) }
	LaunchedEffect(state.mergePrompt) { if (state.mergePrompt) renderInternal = true }

	// Keyed on renderInternal, not mergePrompt: the choice has to outlive the exit animation, and
	// resetting on close would visibly snap the picker back to Merge on the way out.
	var mode by rememberSaveable(renderInternal) { mutableStateOf(MergeMode.Merge) }
	var confirmReplace by rememberSaveable(renderInternal) { mutableStateOf(false) }

	if (renderInternal) {
		AnimatedDialogContainer(
			isOpen = state.mergePrompt,
			onDismissRequest = { component.cancelMergePrompt() },
			onClosed = { renderInternal = false },
			properties = DialogProperties(usePlatformDefaultWidth = false),
		) {
			MergeProjectsDialogContent(
				mode = mode,
				onModeChange = { mode = it },
				onCancel = { component.cancelMergePrompt() },
				onContinue = {
					when (mode) {
						MergeMode.Merge -> component.chooseMerge()
						MergeMode.Replace -> confirmReplace = true
					}
				},
				modifier = Modifier.predictiveBackTransform(),
			)
		}
	}

	AnimatedDialog(
		visible = confirmReplace,
		onCloseRequest = { confirmReplace = false },
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		MergeReplaceConfirmCard(
			onConfirm = {
				confirmReplace = false
				component.chooseReplace()
			},
			onCancel = { confirmReplace = false },
		)
	}
}

/** The last check before Replace deletes every local project, without its dialog window. */
@Composable
internal fun MergeReplaceConfirmCard(onConfirm: () -> Unit, onCancel: () -> Unit) {
	IndexStripCard(
		title = Res.string.merge_projects_replace_confirm_title.get(),
		message = Res.string.merge_projects_replace_confirm_message.get(),
		destructive = true,
		kind = "DELETE",
	) {
		ConfirmationActions(
			confirmLabel = Res.string.merge_projects_replace_confirm_button.get(),
			onConfirm = onConfirm,
			cancelLabel = Res.string.merge_projects_cancel_button.get(),
			onCancel = onCancel,
			destructive = true,
		)
	}
}

/**
 * The static chrome of [MergeProjectsDialog] without the [AnimatedDialogContainer] wrapper.
 * Render this directly in a `@Preview`: the Desktop preview renderer can't advance the enter
 * animation, so only this settles to an opaque frame.
 */
@Composable
fun MergeProjectsDialogContent(
	mode: MergeMode,
	onModeChange: (MergeMode) -> Unit,
	onCancel: () -> Unit,
	onContinue: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		modifier = modifier
			.padding(Ui.Padding.M)
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(
			width = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		),
	) {
		Column {
			Text(
				text = Res.string.merge_projects_dialog_title.get(),
				style = MaterialTheme.typography.titleLarge,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.padding(start = Ui.Padding.XL, end = Ui.Padding.XL, top = Ui.Padding.XL),
			)

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				HdHairlineSegmentedPicker(
					options = MergeMode.entries,
					selected = mode,
					onSelect = onModeChange,
					label = { it.label() },
				)

				Text(
					text = mode.explanation(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}

			HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
			) {
				HdHairlineButton(label = Res.string.merge_projects_cancel_button.get(), onClick = onCancel)
				HdHairlineButton(
					label = when (mode) {
						MergeMode.Merge -> Res.string.merge_projects_merge_button.get()
						MergeMode.Replace -> Res.string.merge_projects_replace_button.get()
					},
					onClick = onContinue,
					emphasised = mode == MergeMode.Merge,
					danger = mode == MergeMode.Replace,
				)
			}
		}
	}
}

@Composable
private fun MergeMode.label(): String = when (this) {
	MergeMode.Merge -> Res.string.merge_projects_mode_merge.get()
	MergeMode.Replace -> Res.string.merge_projects_mode_replace.get()
}

@Composable
private fun MergeMode.explanation(): String = when (this) {
	MergeMode.Merge -> Res.string.merge_projects_explain_merge.get()
	MergeMode.Replace -> Res.string.merge_projects_explain_replace.get()
}
