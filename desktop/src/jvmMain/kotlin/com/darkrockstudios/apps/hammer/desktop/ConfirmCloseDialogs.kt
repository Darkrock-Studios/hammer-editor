package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.UnsavedScenesConfirmDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedScenesDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	UnsavedScenesConfirmDialog(
		title = Res.string.unsaved_scenes_dialog_title.get(),
		message = Res.string.unsaved_scenes_dialog_message.get(),
		saveButtonText = Res.string.unsaved_entity_dialog_positive_button.get(),
		discardButtonText = Res.string.unsaved_entity_dialog_negative_button.get(),
		cancelButtonText = Res.string.unsaved_entity_dialog_neutral_button.get(),
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
	SimpleConfirm(
		title = Res.string.unsaved_encyclopedia_dialog_title.get(),
		message = Res.string.unsaved_encyclopedia_dialog_message.get(),
		positiveButton = Res.string.unsaved_dialog_positive_button.get(),
		negativeButton = Res.string.unsaved_dialog_negative_button.get(),
		onDismiss = { /* Noop */ },
		onNegative = {
			dismissDialog(ConfirmCloseResult.Cancel, closeType)
		},
		onConfirm = {
			dismissDialog(ConfirmCloseResult.Discard, closeType)
		}
	)
}

@ExperimentalMaterial3Api
@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedNotesDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	SimpleConfirm(
		title = Res.string.unsaved_notes_dialog_title.get(),
		message = Res.string.unsaved_notes_dialog_message.get(),
		positiveButton = Res.string.unsaved_dialog_positive_button.get(),
		negativeButton = Res.string.unsaved_dialog_negative_button.get(),
		onDismiss = { /* Noop */ },
		onNegative = {
			dismissDialog(ConfirmCloseResult.Cancel, closeType)
		},
		onConfirm = {
			dismissDialog(ConfirmCloseResult.Discard, closeType)
		}
	)
}
