package com.darkrockstudios.apps.hammer.android

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import kotlinx.coroutines.launch

fun AppCompatActivity.confirmUnsavedScenesDialog(component: ProjectRootComponent) {
	AlertDialog.Builder(this)
		.setTitle(R.string.unsaved_scenes_dialog_title)
		.setMessage(R.string.unsaved_scenes_dialog_message)
		.setNegativeButton(R.string.unsaved_scenes_dialog_negative_button) { _, _ ->
			component.closeRequestDealtWith(CloseConfirm.Scenes)
		}
		.setNeutralButton(R.string.unsaved_scenes_dialog_neutral_button) { dialog, _ ->
			component.cancelCloseRequest()
			dialog.dismiss()
		}
		.setPositiveButton(R.string.unsaved_scenes_dialog_positive_button) { _, _ ->
			lifecycleScope.launch {
				component.storeDirtyBuffers()
				component.closeRequestDealtWith(CloseConfirm.Scenes)
			}
		}
		.create()
		.show()
}

fun AppCompatActivity.confirmCloseUnsavedEncyclopediaDialog(component: ProjectRootComponent) {
	AlertDialog.Builder(this)
		.setTitle(MR.strings.unsaved_encyclopedia_dialog_title.getString(this))
		.setMessage(MR.strings.unsaved_encyclopedia_dialog_message.getString(this))
		.setPositiveButton(R.string.unsaved_scenes_dialog_negative_button) { _, _ ->
			component.closeRequestDealtWith(CloseConfirm.Encyclopedia)
		}
		.setNegativeButton(R.string.unsaved_scenes_dialog_neutral_button) { dialog, _ ->
			component.cancelCloseRequest()
			dialog.dismiss()
		}
		.create()
		.show()
}

fun AppCompatActivity.confirmCloseUnsavedNotesDialog(component: ProjectRootComponent) {
	AlertDialog.Builder(this)
		.setTitle(MR.strings.unsaved_notes_dialog_title.getString(this))
		.setMessage(MR.strings.unsaved_notes_dialog_message.getString(this))
		.setPositiveButton(R.string.unsaved_scenes_dialog_negative_button) { _, _ ->
			component.closeRequestDealtWith(CloseConfirm.Notes)
		}
		.setNegativeButton(R.string.unsaved_scenes_dialog_neutral_button) { dialog, _ ->
			component.cancelCloseRequest()
			dialog.dismiss()
		}
		.create()
		.show()
}

