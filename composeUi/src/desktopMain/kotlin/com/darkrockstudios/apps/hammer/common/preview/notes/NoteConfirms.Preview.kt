package com.darkrockstudios.apps.hammer.common.preview.notes

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationActions
import com.darkrockstudios.apps.hammer.common.compose.IndexStripCard
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.confirm_kind_delete
import com.darkrockstudios.apps.hammer.confirm_kind_discard
import com.darkrockstudios.apps.hammer.notes_delete_cancel
import com.darkrockstudios.apps.hammer.notes_delete_confirm
import com.darkrockstudios.apps.hammer.notes_discard_confirm
import com.darkrockstudios.apps.hammer.notes_discard_keep
import com.darkrockstudios.apps.hammer.notes_delete_message
import com.darkrockstudios.apps.hammer.notes_delete_title
import com.darkrockstudios.apps.hammer.notes_discard_dialog_message
import com.darkrockstudios.apps.hammer.notes_discard_dialog_title

@Composable
private fun Scrim(content: @Composable () -> Unit) {
	PreviewScrim {
		content()
	}
}

/** Leaving a note with unsaved edits, over a scrim. */
@Composable
internal fun NoteDiscardConfirmScrimPreview() = Scrim {
	IndexStripCard(
		title = Res.string.notes_discard_dialog_title.get(),
		message = Res.string.notes_discard_dialog_message.get(),
		destructive = true,
		kind = Res.string.confirm_kind_discard.get(),
	) {
		ConfirmationActions(
			confirmLabel = Res.string.notes_discard_confirm.get(),
			onConfirm = {},
			cancelLabel = Res.string.notes_discard_keep.get(),
			onCancel = {},
			destructive = true,
		)
	}
}

/** Deleting a note, over a scrim. */
@Composable
internal fun NoteDeleteConfirmScrimPreview() = Scrim {
	IndexStripCard(
		title = Res.string.notes_delete_title.get(),
		message = Res.string.notes_delete_message.get(),
		destructive = true,
		kind = Res.string.confirm_kind_delete.get(),
	) {
		ConfirmationActions(
			confirmLabel = Res.string.notes_delete_confirm.get(),
			onConfirm = {},
			cancelLabel = Res.string.notes_delete_cancel.get(),
			onCancel = {},
			destructive = true,
		)
	}
}
