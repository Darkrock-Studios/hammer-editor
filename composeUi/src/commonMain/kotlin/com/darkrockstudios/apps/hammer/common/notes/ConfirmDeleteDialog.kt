package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ConfirmDeleteDialog(
	note: NoteContent,
	component: Notes,
	snackbarHostState: SnackbarHostState,
	scope: CoroutineScope,
) {
	SimpleConfirm(
		title = "Delete Note?",
		message = "This action can not be un-done.",
		onDismiss = { component.dismissConfirmDelete() }
	) {
		scope.launch {
			component.deleteNote(note.id)
			component.dismissConfirmDelete()
			scope.launch { snackbarHostState.showSnackbar("Note ${note.id} Deleted") }
		}
	}
}