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
import com.darkrockstudios.apps.hammer.scene_delete_dialog_delete_button
import com.darkrockstudios.apps.hammer.create_sceneitem_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.scene_delete_dialog_message
import com.darkrockstudios.apps.hammer.scene_delete_dialog_title
import com.darkrockstudios.apps.hammer.confirm_kind_delete

/** Deleting a scene from the scene list, over a scrim. */
@Composable
internal fun SceneDeleteConfirmScrimPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		IndexStripCard(
			title = Res.string.scene_delete_dialog_title.get("Bottom of Chimney"),
			message = Res.string.scene_delete_dialog_message.get("Bottom of Chimney"),
			destructive = true,
			kind = Res.string.confirm_kind_delete.get(),
		) {
			ConfirmationActions(
				confirmLabel = Res.string.scene_delete_dialog_delete_button.get(),
				onConfirm = {},
				cancelLabel = Res.string.create_sceneitem_dialog_dismiss_button.get(),
				onCancel = {},
				destructive = true,
			)
		}
	}
}
