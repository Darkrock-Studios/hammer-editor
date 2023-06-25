package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedScenesDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	AlertDialog(
		title = { Text(MR.strings.unsaved_scenes_dialog_title.get()) },
		text = { Text(MR.strings.unsaved_scenes_dialog_message.get()) },
		onDismissRequest = { /* Noop */ },
		buttons = {
			Column(
				modifier = Modifier.fillMaxWidth(),
			) {
				Button(onClick = { dismissDialog(ConfirmCloseResult.SaveAll, closeType) }) {
					Text(MR.strings.unsaved_scenes_dialog_positive_button.get())
				}
				Button(onClick = {
					dismissDialog(
						ConfirmCloseResult.Cancel,
						ApplicationState.CloseType.None
					)
				}) {
					Text(MR.strings.unsaved_scenes_dialog_neutral_button.get())
				}
				Button(onClick = { dismissDialog(ConfirmCloseResult.Discard, closeType) }) {
					Text(MR.strings.unsaved_scenes_dialog_negative_button.get())
				}
			}
		},
		modifier = Modifier.width(300.dp).padding(Ui.Padding.XL)
	)
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedEncyclopediaDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	AlertDialog(
		title = { Text(MR.strings.unsaved_encyclopedia_dialog_title.get()) },
		text = { Text(MR.strings.unsaved_encyclopedia_dialog_message.get()) },
		onDismissRequest = { /* Noop */ },
		buttons = {
			Column(
				modifier = Modifier.fillMaxWidth(),
			) {
				Button(onClick = { dismissDialog(ConfirmCloseResult.Cancel, closeType) }) {
					Text(MR.strings.unsaved_encyclopedia_dialog_negative_button.get())
				}
				Button(onClick = { dismissDialog(ConfirmCloseResult.Discard, closeType) }) {
					Text(MR.strings.unsaved_encyclopedia_dialog_positive_button.get())
				}
			}
		},
		modifier = Modifier.width(300.dp).padding(Ui.Padding.XL)
	)
}