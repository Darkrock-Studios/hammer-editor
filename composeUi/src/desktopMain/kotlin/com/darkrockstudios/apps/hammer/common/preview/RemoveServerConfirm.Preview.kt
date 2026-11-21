package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationActions
import com.darkrockstudios.apps.hammer.common.compose.IndexStripCard
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_cancel
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_confirm
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_message
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_title
import com.darkrockstudios.apps.hammer.confirm_kind_remove

/** The Remove server confirm from the Server settings tab, over a scrim. */
@Composable
internal fun RemoveServerConfirmScrimPreview() {
	PreviewScrim {
		IndexStripCard(
			title = Res.string.settings_remove_server_dialog_title.get(),
			message = Res.string.settings_remove_server_dialog_message.get(),
			destructive = true,
			kind = Res.string.confirm_kind_remove.get(),
		) {
			ConfirmationActions(
				confirmLabel = Res.string.settings_remove_server_dialog_confirm.get(),
				onConfirm = {},
				cancelLabel = Res.string.settings_remove_server_dialog_cancel.get(),
				onCancel = {},
				destructive = true,
			)
		}
	}
}
