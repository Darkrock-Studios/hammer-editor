package com.darkrockstudios.apps.hammer.common.preview.storyideas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationActions
import com.darkrockstudios.apps.hammer.common.compose.IndexStripCard
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.ideas_delete_dialog_confirm
import com.darkrockstudios.apps.hammer.ideas_delete_dialog_message
import com.darkrockstudios.apps.hammer.ideas_delete_dialog_title
import com.darkrockstudios.apps.hammer.ideas_dialog_cancel
import com.darkrockstudios.apps.hammer.ideas_discard_dialog_confirm
import com.darkrockstudios.apps.hammer.ideas_discard_dialog_keep
import com.darkrockstudios.apps.hammer.ideas_discard_dialog_message
import com.darkrockstudios.apps.hammer.ideas_discard_dialog_title
import com.darkrockstudios.apps.hammer.ideas_promote_button
import com.darkrockstudios.apps.hammer.ideas_promote_dialog_message
import com.darkrockstudios.apps.hammer.ideas_promote_dialog_title

/** The idea view's discard, delete and promote confirms, stacked over a scrim. */
@Preview(widthDp = 600, heightDp = 900)
@Composable
fun StoryIdeaConfirmsPreview() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		IndexStripCard(
			title = Res.string.ideas_discard_dialog_title.get(),
			message = Res.string.ideas_discard_dialog_message.get(),
			destructive = true,
			kind = "DISCARD",
		) {
			ConfirmationActions(
				confirmLabel = Res.string.ideas_discard_dialog_confirm.get(),
				onConfirm = {},
				cancelLabel = Res.string.ideas_discard_dialog_keep.get(),
				onCancel = {},
				destructive = true,
			)
		}
		IndexStripCard(
			title = Res.string.ideas_delete_dialog_title.get(),
			message = Res.string.ideas_delete_dialog_message.get(),
			destructive = true,
			kind = "DELETE",
		) {
			ConfirmationActions(
				confirmLabel = Res.string.ideas_delete_dialog_confirm.get(),
				onConfirm = {},
				cancelLabel = Res.string.ideas_dialog_cancel.get(),
				onCancel = {},
				destructive = true,
			)
		}
		IndexStripCard(
			title = Res.string.ideas_promote_dialog_title.get(),
			message = Res.string.ideas_promote_dialog_message.get(),
			kind = "PROMOTE",
		) {
			ConfirmationActions(
				confirmLabel = Res.string.ideas_promote_button.get(),
				onConfirm = {},
				cancelLabel = Res.string.ideas_dialog_cancel.get(),
				onCancel = {},
				destructive = false,
			)
		}
	}
}
