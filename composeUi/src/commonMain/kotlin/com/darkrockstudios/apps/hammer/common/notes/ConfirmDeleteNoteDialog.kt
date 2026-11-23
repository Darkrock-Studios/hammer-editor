package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.notes.ViewNote
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.confirm_kind_delete
import com.darkrockstudios.apps.hammer.notes_delete_cancel
import com.darkrockstudios.apps.hammer.notes_delete_confirm
import com.darkrockstudios.apps.hammer.notes_delete_message
import com.darkrockstudios.apps.hammer.notes_delete_title
import com.darkrockstudios.apps.hammer.notes_delete_toast_success
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ConfirmDeleteNoteDialog(
	note: NoteContent,
	component: ViewNote,
	rootSnackbar: RootSnackbarHostState,
	scope: CoroutineScope,
) {
	val strRes = rememberStrRes()

	SimpleConfirm(
		title = Res.string.notes_delete_title.get(),
		message = Res.string.notes_delete_message.get(),
		positiveButton = Res.string.notes_delete_confirm.get(),
		negativeButton = Res.string.notes_delete_cancel.get(),
		destructive = true,
		kind = Res.string.confirm_kind_delete.get(),
		onDismiss = { component.dismissConfirmDelete() }
	) {
		scope.launch {
			component.deleteNote(note.id)
			component.dismissConfirmDelete()
			scope.launch {
				rootSnackbar.showSnackbar(
					strRes.get(Res.string.notes_delete_toast_success)
				)
			}
		}
	}
}