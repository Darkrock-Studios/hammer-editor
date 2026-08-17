package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdButtonBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
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

	ConfirmationDialog(
		visible = confirmReplace,
		title = Res.string.merge_projects_replace_confirm_title.get(),
		message = Res.string.merge_projects_replace_confirm_message.get(),
		confirmLabel = Res.string.merge_projects_replace_confirm_button.get(),
		cancelLabel = Res.string.confirm_dialog_negative.get(),
		onConfirm = {
			confirmReplace = false
			component.chooseReplace()
		},
		onDismiss = { confirmReplace = false },
		destructive = true,
		kind = "DELETE",
	)
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
			HdMasthead(
				section = "SYNC PROJECTS",
				trailing = {
					HdMastheadAction(label = "× CLOSE", onClick = onCancel)
				},
			)
			HdFolioDivider()

			Text(
				text = Res.string.merge_projects_dialog_title.get(),
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = Ui.Padding.XL,
						end = Ui.Padding.XL,
						top = Ui.Padding.L,
						bottom = Ui.Padding.S,
					),
			)

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = Ui.Padding.XL,
						end = Ui.Padding.XL,
						top = Ui.Padding.L,
						bottom = Ui.Padding.L,
					),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				HdHairlineSegmentedPicker(
					title = Res.string.merge_projects_mode_label.get(),
					options = MergeMode.entries,
					selected = mode,
					onSelect = onModeChange,
					label = { it.label() },
				)

				Text(
					text = mode.explanation(),
					style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}

			HdButtonBar(
				cancelLabel = Res.string.settings_server_setup_cancel_button.get(),
				primaryLabel = Res.string.merge_projects_continue_button.get(),
				onCancel = onCancel,
				onPrimary = onContinue,
				primaryDanger = mode == MergeMode.Replace,
			)
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
