package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.projectroot.UnsavedEntityDiscardConfirm
import com.darkrockstudios.apps.hammer.common.projectroot.UnsavedScenesConfirm
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.unsaved_dialog_positive_button
import com.darkrockstudios.apps.hammer.unsaved_encyclopedia_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_negative_button
import org.jetbrains.compose.resources.StringResource
import com.darkrockstudios.apps.hammer.unsaved_notes_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_timeline_dialog_message

@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedScenesDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	UnsavedScenesConfirm(
		onSave = { dismissDialog(ConfirmCloseResult.SaveAll, closeType) },
		onDiscard = { dismissDialog(ConfirmCloseResult.Discard, closeType) },
		onCancel = { dismissDialog(ConfirmCloseResult.Cancel, ApplicationState.CloseType.None) }
	)
}

@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedEncyclopediaDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	UnsavedEntityConfirm(closeType, Res.string.unsaved_encyclopedia_dialog_message, dismissDialog)
}

@ExperimentalMaterial3Api
@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedTimelineDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	UnsavedEntityConfirm(closeType, Res.string.unsaved_timeline_dialog_message, dismissDialog)
}

@ExperimentalMaterial3Api
@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedNotesDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	UnsavedEntityConfirm(closeType, Res.string.unsaved_notes_dialog_message, dismissDialog)
}

@Composable
private fun UnsavedEntityConfirm(
	closeType: ApplicationState.CloseType,
	message: StringResource,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit,
) {
	UnsavedEntityDiscardConfirm(
		message = message,
		discardLabel = discardButtonText(closeType),
		onDiscard = { dismissDialog(ConfirmCloseResult.Discard, closeType) },
		onKeep = { dismissDialog(ConfirmCloseResult.Cancel, closeType) },
	)
}

/** "Discard and close" while a window close is pending, plain "Discard" for a sync gate. */
@Composable
private fun discardButtonText(closeType: ApplicationState.CloseType): String =
	if (closeType == ApplicationState.CloseType.None) {
		Res.string.unsaved_entity_dialog_negative_button.get()
	} else {
		Res.string.unsaved_dialog_positive_button.get()
	}
