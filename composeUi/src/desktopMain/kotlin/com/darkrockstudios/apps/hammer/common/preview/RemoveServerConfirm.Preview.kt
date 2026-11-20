package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationActions
import com.darkrockstudios.apps.hammer.common.compose.IndexStripCard
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_cancel
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_confirm
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_message
import com.darkrockstudios.apps.hammer.settings_remove_server_dialog_title

/** The Remove server confirm from the Server settings tab, over a scrim. */
@Composable
internal fun RemoveServerConfirmScrimPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		IndexStripCard(
			title = Res.string.settings_remove_server_dialog_title.get(),
			message = Res.string.settings_remove_server_dialog_message.get(),
			destructive = true,
			kind = "REMOVE",
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
