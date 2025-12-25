package com.darkrockstudios.apps.hammer.android

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleCoroutineScope
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.UnsavedScenesConfirmDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.launch

@Composable
fun ConfirmUnsavedScenesDialog(
	component: ProjectRoot,
	lifecycleScope: LifecycleCoroutineScope
) {
	UnsavedScenesConfirmDialog(
		title = Res.string.unsaved_scenes_dialog_title.get(),
		message = Res.string.unsaved_scenes_dialog_message.get(),
		saveButtonText = Res.string.unsaved_entity_dialog_positive_button.get(),
		discardButtonText = Res.string.unsaved_entity_dialog_negative_button.get(),
		cancelButtonText = Res.string.unsaved_entity_dialog_neutral_button.get(),
		onSave = {
			lifecycleScope.launch {
				component.storeDirtyBuffers()
				component.closeRequestDealtWith(CloseConfirm.Scenes)
			}
		},
		onDiscard = {
			component.closeRequestDealtWith(CloseConfirm.Scenes)
		},
		onCancel = {
			component.cancelCloseRequest()
		}
	)
}

@Composable
fun ConfirmCloseUnsavedEncyclopediaDialog(component: ProjectRoot) {
	SimpleConfirm(
		title = Res.string.unsaved_encyclopedia_dialog_title.get(),
		message = Res.string.unsaved_encyclopedia_dialog_message.get(),
		positiveButton = Res.string.unsaved_entity_dialog_negative_button.get(),
		negativeButton = Res.string.unsaved_entity_dialog_neutral_button.get(),
		onDismiss = {
			component.cancelCloseRequest()
		}
	) {
		component.closeRequestDealtWith(CloseConfirm.Encyclopedia)
	}
}

@Composable
fun ConfirmCloseUnsavedNotesDialog(component: ProjectRoot) {
	SimpleConfirm(
		title = Res.string.unsaved_notes_dialog_title.get(),
		message = Res.string.unsaved_notes_dialog_message.get(),
		positiveButton = Res.string.unsaved_entity_dialog_negative_button.get(),
		negativeButton = Res.string.unsaved_entity_dialog_neutral_button.get(),
		onDismiss = {
			component.cancelCloseRequest()
		}
	) {
		component.closeRequestDealtWith(CloseConfirm.Notes)
	}
}