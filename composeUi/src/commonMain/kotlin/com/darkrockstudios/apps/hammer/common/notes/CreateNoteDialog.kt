package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NoteError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateNoteDialog(
    component: Notes,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
	MpDialog(
		visible = true,
		title = "Create Note",
		modifier = Modifier.padding(Ui.Padding.XL),
		onCloseRequest = { component.dismissCreate() }
	) {
		var newNoteText by remember { mutableStateOf("") }
		var newNoteError by remember { mutableStateOf(false) }

		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
			) {
				Text(
					"New Note:",
					style = MaterialTheme.typography.headlineLarge
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				TextField(
					value = newNoteText,
					onValueChange = { newNoteText = it },
					isError = newNoteError,
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(onClick = {
						val result = component.createNote(newNoteText)
						newNoteError = !result.isSuccess
						when (result) {
							NoteError.TOO_LONG -> scope.launch { snackbarHostState.showSnackbar("Note was too long") }
							NoteError.EMPTY -> scope.launch { snackbarHostState.showSnackbar("Note was empty") }
							NoteError.NONE -> {
								newNoteText = ""
								scope.launch { snackbarHostState.showSnackbar("Note Created") }
							}
						}
					}) {
						Text("Create")
					}

					Button(onClick = { component.dismissCreate() }) {
						Text("Cancel")
					}
				}
			}
		}
	}
}