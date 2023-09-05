package com.darkrockstudios.apps.hammer.android

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleCoroutineScope
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import kotlinx.coroutines.launch

@Composable
fun ConfirmUnsavedScenesDialog(
	component: ProjectRoot,
	lifecycleScope: LifecycleCoroutineScope
) {
	SimpleConfirm(
		title = MR.strings.unsaved_scenes_dialog_title.get(),
		message = MR.strings.unsaved_scenes_dialog_message.get(),
		positiveButton = MR.strings.unsaved_entity_dialog_positive_button.get(),
		negativeButton = MR.strings.unsaved_entity_dialog_negative_button.get(),
		onNegative = {
			component.closeRequestDealtWith(CloseConfirm.Scenes)
		},
		onDismiss = {
			component.cancelCloseRequest()
		}
	) {
		lifecycleScope.launch {
			component.storeDirtyBuffers()
			component.closeRequestDealtWith(CloseConfirm.Scenes)
		}
	}
}

@Composable
fun ConfirmCloseUnsavedEncyclopediaDialog(component: ProjectRoot) {
	SimpleConfirm(
		title = MR.strings.unsaved_encyclopedia_dialog_title.get(),
		message = MR.strings.unsaved_encyclopedia_dialog_message.get(),
		positiveButton = MR.strings.unsaved_entity_dialog_negative_button.get(),
		negativeButton = MR.strings.unsaved_entity_dialog_neutral_button.get(),
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
		title = MR.strings.unsaved_notes_dialog_title.get(),
		message = MR.strings.unsaved_notes_dialog_message.get(),
		positiveButton = MR.strings.unsaved_entity_dialog_negative_button.get(),
		negativeButton = MR.strings.unsaved_entity_dialog_neutral_button.get(),
		onDismiss = {
			component.cancelCloseRequest()
		}
	) {
		component.closeRequestDealtWith(CloseConfirm.Notes)
	}
}