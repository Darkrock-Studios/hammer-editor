package com.darkrockstudios.apps.hammer.android

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import kotlinx.coroutines.launch

fun AppCompatActivity.confirmUnsavedScenesDialog(component: ProjectRoot) {
	AlertDialog.Builder(this)
		.setTitle(MR.strings.unsaved_scenes_dialog_title.resourceId)
		.setMessage(MR.strings.unsaved_scenes_dialog_message.resourceId)
		.setNegativeButton(MR.strings.unsaved_scenes_dialog_negative_button.resourceId) { _, _ ->
			component.closeRequestDealtWith(CloseConfirm.Scenes)
		}
		.setNeutralButton(MR.strings.unsaved_scenes_dialog_neutral_button.resourceId) { dialog, _ ->
			component.cancelCloseRequest()
			dialog.dismiss()
		}
		.setPositiveButton(MR.strings.unsaved_scenes_dialog_positive_button.resourceId) { _, _ ->
			lifecycleScope.launch {
				component.storeDirtyBuffers()
				component.closeRequestDealtWith(CloseConfirm.Scenes)
			}
		}
		.create()
		.show()
}

fun AppCompatActivity.confirmCloseUnsavedEncyclopediaDialog(component: ProjectRoot) {
	AlertDialog.Builder(this)
		.setTitle(MR.strings.unsaved_encyclopedia_dialog_title.getString(this))
		.setMessage(MR.strings.unsaved_encyclopedia_dialog_message.getString(this))
		.setPositiveButton(MR.strings.unsaved_scenes_dialog_negative_button.resourceId) { _, _ ->
			component.closeRequestDealtWith(CloseConfirm.Encyclopedia)
		}
		.setNegativeButton(MR.strings.unsaved_scenes_dialog_neutral_button.resourceId) { dialog, _ ->
			component.cancelCloseRequest()
			dialog.dismiss()
		}
		.create()
		.show()
}

fun AppCompatActivity.confirmCloseUnsavedNotesDialog(component: ProjectRoot) {
	AlertDialog.Builder(this)
		.setTitle(MR.strings.unsaved_notes_dialog_title.getString(this))
		.setMessage(MR.strings.unsaved_notes_dialog_message.getString(this))
		.setPositiveButton(MR.strings.unsaved_scenes_dialog_negative_button.resourceId) { _, _ ->
			component.closeRequestDealtWith(CloseConfirm.Notes)
		}
		.setNegativeButton(MR.strings.unsaved_scenes_dialog_neutral_button.resourceId) { dialog, _ ->
			component.cancelCloseRequest()
			dialog.dismiss()
		}
		.create()
		.show()
}

