package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.notes.NoteError
import com.darkrockstudios.apps.hammer.common.data.notes.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.text.markdownToAnnotatedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun NotesUi(
	component: Notes
) {
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	var newNoteText by remember { mutableStateOf("") }
	var newNoteError by remember { mutableStateOf(false) }

	val snackbarHostState = remember { SnackbarHostState() }

	Column {
		Text("New Note:")
		TextField(
			value = newNoteText,
			onValueChange = { newNoteText = it },
			isError = newNoteError
		)
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
		Spacer(modifier = Modifier)

		Text("Notes")
		LazyVerticalGrid(
			columns = GridCells.Adaptive(512.dp),
			modifier = Modifier.fillMaxWidth(),
			contentPadding = PaddingValues(Ui.PADDING)
		) {
			state.apply {
				if (notes.isEmpty()) {
					item {
						Text("No Notes Found")
					}
				} else {
					items(notes.size) { index ->
						NoteItem(
							note = notes[index],
							component = component,
							snackbarHostState = snackbarHostState,
							scope = scope
						)
					}
				}
			}
		}

		SnackbarHost(snackbarHostState, modifier = Modifier)
	}
}

@Composable
fun NoteItem(
	note: NoteContent,
	component: Notes,
	snackbarHostState: SnackbarHostState,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
) {
	var isEditing by remember { mutableStateOf(false) }
	var confirmDelete by remember { mutableStateOf(false) }
	var updatedNoteText by remember { mutableStateOf(note.content) }

	Card(
		modifier = modifier
			.fillMaxWidth()
			.padding(Ui.PADDING),
		elevation = Ui.ELEVATION
	) {
		Column {
			Text(note.id.toString())
			Text(note.created.toLocalDateTime(TimeZone.currentSystemDefault()).toString())
			if (isEditing) {
				TextField(
					value = updatedNoteText,
					onValueChange = { updatedNoteText = it },
				)
				Button(onClick = {
					component.updateNote(note.copy(content = updatedNoteText))
					isEditing = false
				}) {
					Text("Save")
				}
				Button(onClick = { isEditing = false }) {
					Text("Cancel")
				}
			} else {
				ClickableText(note.content.markdownToAnnotatedString()) {
					isEditing = true
				}
			}
			Button(onClick = { confirmDelete = true }) {
				Text("Delete")
			}
		}
	}

	ConfirmDeleteDialog(note, component, snackbarHostState, scope, confirmDelete) {
		confirmDelete = false
	}
}

@Composable
fun ConfirmDeleteDialog(
	note: NoteContent,
	component: Notes,
	snackbarHostState: SnackbarHostState,
	scope: CoroutineScope,
	confirmDelete: Boolean,
	closeDialog: () -> Unit
) {
	MpDialog(
		onCloseRequest = { },
		title = "Delete Note?",
		visible = confirmDelete,
		resizable = false
	) {
		Row(Modifier.wrapContentSize()) {
			Button(onClick = {
				component.deleteNote(note.id)
				scope.launch { snackbarHostState.showSnackbar("Note ${note.id} Deleted") }
				closeDialog()
			}) {
				Text("DELETE")
			}

			Button(onClick = {
				closeDialog()
			}) {
				Text("Cancel")
			}
		}
	}
}