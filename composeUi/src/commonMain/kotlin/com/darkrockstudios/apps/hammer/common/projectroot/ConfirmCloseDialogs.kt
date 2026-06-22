package com.darkrockstudios.apps.hammer.common.projectroot

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.UnsavedScenesConfirmDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.unsaved_encyclopedia_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_encyclopedia_dialog_title
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_negative_button
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_neutral_button
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_positive_button
import com.darkrockstudios.apps.hammer.unsaved_notes_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_notes_dialog_title
import com.darkrockstudios.apps.hammer.unsaved_scenes_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_scenes_dialog_title
import com.darkrockstudios.apps.hammer.unsaved_timeline_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_timeline_dialog_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ConfirmUnsavedScenesDialog(
	component: ProjectRoot,
	scope: CoroutineScope,
) {
	UnsavedScenesConfirmDialog(
		title = Res.string.unsaved_scenes_dialog_title.get(),
		message = Res.string.unsaved_scenes_dialog_message.get(),
		saveButtonText = Res.string.unsaved_entity_dialog_positive_button.get(),
		discardButtonText = Res.string.unsaved_entity_dialog_negative_button.get(),
		cancelButtonText = Res.string.unsaved_entity_dialog_neutral_button.get(),
		onSave = {
			scope.launch {
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

@Composable
fun ConfirmCloseUnsavedTimelineDialog(component: ProjectRoot) {
	SimpleConfirm(
		title = Res.string.unsaved_timeline_dialog_title.get(),
		message = Res.string.unsaved_timeline_dialog_message.get(),
		positiveButton = Res.string.unsaved_entity_dialog_negative_button.get(),
		negativeButton = Res.string.unsaved_entity_dialog_neutral_button.get(),
		onDismiss = {
			component.cancelCloseRequest()
		}
	) {
		component.closeRequestDealtWith(CloseConfirm.Timeline)
	}
}
