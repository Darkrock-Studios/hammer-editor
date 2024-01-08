package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedScenesDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	AlertDialog(
		onDismissRequest = { /* Noop */ },
		modifier = Modifier.width(300.dp).padding(Ui.Padding.XL)
	) {
		Card {
			Column(modifier = Modifier.padding(Ui.Padding.XL)) {
				Text(
					MR.strings.unsaved_scenes_dialog_title.get(),
					color = MaterialTheme.colorScheme.onSurface,
					style = MaterialTheme.typography.headlineLarge
				)

				Text(
					MR.strings.unsaved_scenes_dialog_message.get(),
					color = MaterialTheme.colorScheme.onSurface,
					style = MaterialTheme.typography.bodyMedium,
				)

				Spacer(modifier = Modifier.padding(Ui.Padding.XL))

				FlowRow(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L, Alignment.CenterHorizontally)
				) {
					Button(onClick = { dismissDialog(ConfirmCloseResult.SaveAll, closeType) }) {
						Text(MR.strings.unsaved_entity_dialog_positive_button.get())
					}
					Button(onClick = { dismissDialog(ConfirmCloseResult.Discard, closeType) }) {
						Text(MR.strings.unsaved_entity_dialog_negative_button.get())
					}
					Button(onClick = {
						dismissDialog(
							ConfirmCloseResult.Cancel,
							ApplicationState.CloseType.None
						)
					}) {
						Text(MR.strings.unsaved_entity_dialog_neutral_button.get())
					}
				}
			}
		}
	}
}

@ExperimentalComposeApi
@Composable
internal fun confirmCloseUnsavedEncyclopediaDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	SimpleConfirm(
		title = MR.strings.unsaved_encyclopedia_dialog_title.get(),
		message = MR.strings.unsaved_encyclopedia_dialog_message.get(),
		positiveButton = MR.strings.unsaved_dialog_positive_button.get(),
		negativeButton = MR.strings.unsaved_dialog_negative_button.get(),
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
		title = MR.strings.unsaved_notes_dialog_title.get(),
		message = MR.strings.unsaved_notes_dialog_message.get(),
		positiveButton = MR.strings.unsaved_dialog_positive_button.get(),
		negativeButton = MR.strings.unsaved_dialog_negative_button.get(),
		onDismiss = { /* Noop */ },
		onNegative = {
			dismissDialog(ConfirmCloseResult.Cancel, closeType)
		},
		onConfirm = {
			dismissDialog(ConfirmCloseResult.Discard, closeType)
		}
	)
}
