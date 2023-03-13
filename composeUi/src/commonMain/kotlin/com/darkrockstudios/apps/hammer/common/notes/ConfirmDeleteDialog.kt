package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.components.notes.Notes
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
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
	MpDialog(
		visible = true,
		title = "Delete Note?",
		modifier = Modifier.padding(Ui.Padding.XL),
		onCloseRequest = { component.dismissConfirmDelete() }
	) {
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
			) {
				Text(
					"This action can not be un-done.",
					style = MaterialTheme.typography.bodyLarge
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(
						onClick = {
							component.deleteNote(note.id)
							component.dismissConfirmDelete()
							scope.launch { snackbarHostState.showSnackbar("Note ${note.id} Deleted") }
						}
					) {
						Text("Delete")
					}

					Button(onClick = { component.dismissConfirmDelete() }) {
						Text("Cancel")
					}
				}
			}
		}
	}
}