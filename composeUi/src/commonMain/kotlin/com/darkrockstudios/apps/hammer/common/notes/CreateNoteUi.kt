package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.notes.CreateNote
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CreateNoteUi(
	component: CreateNote,
	modifier: Modifier,
	snackbarState: SnackbarHostState
) {
	val state by component.state.subscribeAsState()
	val noteText by component.noteText.subscribeAsState()
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val strRes = rememberStrRes()
	var newNoteError by remember { mutableStateOf(false) }

	Card(
		modifier = modifier.padding(Ui.Padding.XL)
			.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth()
		) {
			Text(
				MR.strings.notes_create_header.get(),
				style = MaterialTheme.typography.displayMedium
			)

			Spacer(modifier = Modifier.size(Ui.Padding.XL))

			OutlinedTextField(
				value = noteText,
				onValueChange = { component.onTextChanged(it) },
				modifier = Modifier.fillMaxWidth()
					.widthIn(max = TextEditorDefaults.MAX_WIDTH)
					.weight(1f),
				isError = newNoteError,
				placeholder = {
					Text(MR.strings.notes_create_body_hint.get())
				}
			)

			Spacer(modifier = Modifier.size(Ui.Padding.XL))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Button(onClick = { component.closeCreate() }) {
					Text(MR.strings.notes_create_cancel_button.get())
				}

				Button(onClick = {
					scope.launch {
						val result = component.createNote(noteText)
						newNoteError = !result.isSuccess
						when (result) {
							NoteError.TOO_LONG -> scope.launch {
								snackbarState.showSnackbar(strRes.get(MR.strings.notes_create_toast_too_long))
							}

							NoteError.EMPTY -> scope.launch {
								snackbarState.showSnackbar(strRes.get(MR.strings.notes_create_toast_empty))
							}

							NoteError.NONE -> {
								withContext(mainDispatcher) {
									component.clearText()
								}
								scope.launch {
									snackbarState.showSnackbar(strRes.get(MR.strings.notes_create_toast_success))
								}
							}
						}
					}
				}) {
					Text(MR.strings.notes_create_create_button.get())
				}
			}
		}
	}

	if (state.confirmDiscard) {
		SimpleConfirm(
			title = MR.strings.notes_discard_dialog_title.get(),
			message = MR.strings.notes_discard_dialog_message.get(),
			onDismiss = {
				component.cancelDiscard()
			}
		) {
			component.clearText()
			component.closeCreate()
		}
	}
}