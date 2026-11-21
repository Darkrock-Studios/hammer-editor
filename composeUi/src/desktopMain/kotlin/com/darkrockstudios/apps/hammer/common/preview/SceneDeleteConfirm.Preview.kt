package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationCard
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.scene_delete_dialog_delete_button
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.chapter_cannot_delete_dialog_message
import com.darkrockstudios.apps.hammer.chapter_cannot_delete_dialog_title
import com.darkrockstudios.apps.hammer.confirm_kind_blocked
import com.darkrockstudios.apps.hammer.cannot_delete_dialog_ok
import com.darkrockstudios.apps.hammer.confirm_kind_archive
import com.darkrockstudios.apps.hammer.scene_archive_dialog_archive_button
import com.darkrockstudios.apps.hammer.scene_archive_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.scene_archive_dialog_message
import com.darkrockstudios.apps.hammer.scene_archive_dialog_title
import com.darkrockstudios.apps.hammer.scene_delete_dialog_message
import com.darkrockstudios.apps.hammer.scene_delete_dialog_title
import com.darkrockstudios.apps.hammer.confirm_kind_delete
import com.darkrockstudios.apps.hammer.confirm_kind_discard
import com.darkrockstudios.apps.hammer.discard_buffer_dialog_cancel
import com.darkrockstudios.apps.hammer.discard_buffer_dialog_confirm
import com.darkrockstudios.apps.hammer.discard_buffer_dialog_message
import com.darkrockstudios.apps.hammer.discard_buffer_dialog_title

/** Deleting a scene from the scene list, over a scrim. */
@Composable
internal fun SceneDeleteConfirmScrimPreview() {
	PreviewScrim {
		ConfirmationCard(
			title = Res.string.scene_delete_dialog_title.get("Bottom of Chimney"),
			message = Res.string.scene_delete_dialog_message.get("Bottom of Chimney"),
			destructive = true,
			kind = Res.string.confirm_kind_delete.get(),
			confirmLabel = Res.string.scene_delete_dialog_delete_button.get(),
			onConfirm = {},
			cancelLabel = Res.string.create_sceneitem_dialog_dismiss_button.get(),
			onCancel = {},
		)
	}
}

/** Trying to delete a chapter that still has scenes, over a scrim. */
@Composable
internal fun ChapterCannotDeleteScrimPreview() {
	PreviewScrim {
		ConfirmationCard(
			title = Res.string.chapter_cannot_delete_dialog_title.get("Chapter IV"),
			message = Res.string.chapter_cannot_delete_dialog_message.get("Chapter IV"),
			kind = Res.string.confirm_kind_blocked.get(),
			confirmLabel = Res.string.cannot_delete_dialog_ok.get(),
			onConfirm = {},
			cancelLabel = null,
			onCancel = {},
		)
	}
}

/** Archiving a scene, over a scrim. */
@Composable
internal fun SceneArchiveConfirmScrimPreview() {
	PreviewScrim {
		ConfirmationCard(
			title = Res.string.scene_archive_dialog_title.get("Bottom of Chimney"),
			message = Res.string.scene_archive_dialog_message.get("Bottom of Chimney"),
			kind = Res.string.confirm_kind_archive.get(),
			confirmLabel = Res.string.scene_archive_dialog_archive_button.get(),
			onConfirm = {},
			cancelLabel = Res.string.scene_archive_dialog_dismiss_button.get(),
			onCancel = {},
		)
	}
}

/** Discarding unsaved edits from the scene editor, over a scrim. */
@Composable
internal fun DiscardChangesScrimPreview() {
	PreviewScrim {
		ConfirmationCard(
			title = Res.string.discard_buffer_dialog_title.get("The Lamp Room"),
			message = Res.string.discard_buffer_dialog_message.get("The Lamp Room"),
			destructive = true,
			kind = Res.string.confirm_kind_discard.get(),
			confirmLabel = Res.string.discard_buffer_dialog_confirm.get(),
			onConfirm = {},
			cancelLabel = Res.string.discard_buffer_dialog_cancel.get(),
			onCancel = {},
		)
	}
}
