package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationActions
import com.darkrockstudios.apps.hammer.common.compose.IndexStripCard
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_discard_confirm
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_discard_keep
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_discard_message
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_discard_title
import com.darkrockstudios.apps.hammer.confirm_kind_delete
import com.darkrockstudios.apps.hammer.confirm_kind_discard
import com.darkrockstudios.apps.hammer.confirm_kind_remove
import com.darkrockstudios.apps.hammer.encyclopedia_entry_confirm_cancel
import com.darkrockstudios.apps.hammer.encyclopedia_entry_delete_confirm
import com.darkrockstudios.apps.hammer.encyclopedia_entry_discard_confirm
import com.darkrockstudios.apps.hammer.encyclopedia_entry_discard_keep
import com.darkrockstudios.apps.hammer.encyclopedia_entry_remove_image_confirm
import com.darkrockstudios.apps.hammer.encyclopedia_entry_delete_message
import com.darkrockstudios.apps.hammer.encyclopedia_entry_delete_title
import com.darkrockstudios.apps.hammer.encyclopedia_entry_discard_message
import com.darkrockstudios.apps.hammer.encyclopedia_entry_discard_title
import com.darkrockstudios.apps.hammer.encyclopedia_entry_remove_image_message
import com.darkrockstudios.apps.hammer.encyclopedia_entry_remove_image_title

@Composable
private fun EntryConfirm(
	title: String,
	message: String,
	confirmLabel: String,
	cancelLabel: String,
	kind: String,
	destructive: Boolean,
) {
	IndexStripCard(
		title = title,
		message = message,
		destructive = destructive,
		kind = kind,
	) {
		ConfirmationActions(
			confirmLabel = confirmLabel,
			onConfirm = {},
			cancelLabel = cancelLabel,
			onCancel = {},
			destructive = destructive,
		)
	}
}

/** The encyclopedia entry's discard, delete and remove-image confirms, stacked over a scrim. */
@Composable
internal fun EntryConfirmsScrimPreview() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		EntryConfirm(
			title = Res.string.encyclopedia_entry_discard_title.get(),
			message = Res.string.encyclopedia_entry_discard_message.get(),
			confirmLabel = Res.string.encyclopedia_entry_discard_confirm.get(),
			cancelLabel = Res.string.encyclopedia_entry_discard_keep.get(),
			kind = Res.string.confirm_kind_discard.get(),
			destructive = true,
		)
		EntryConfirm(
			title = Res.string.encyclopedia_entry_delete_title.get("The Lighthouse"),
			message = Res.string.encyclopedia_entry_delete_message.get(),
			confirmLabel = Res.string.encyclopedia_entry_delete_confirm.get(),
			cancelLabel = Res.string.encyclopedia_entry_confirm_cancel.get(),
			kind = Res.string.confirm_kind_delete.get(),
			destructive = true,
		)
		EntryConfirm(
			title = Res.string.encyclopedia_entry_remove_image_title.get(),
			message = Res.string.encyclopedia_entry_remove_image_message.get(),
			confirmLabel = Res.string.encyclopedia_entry_remove_image_confirm.get(),
			cancelLabel = Res.string.encyclopedia_entry_confirm_cancel.get(),
			kind = Res.string.confirm_kind_remove.get(),
			destructive = false,
		)
	}
}

/** Leaving a new encyclopedia entry before creating it, over a scrim. */
@Composable
internal fun DiscardNewEntryScrimPreview() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		EntryConfirm(
			title = Res.string.encyclopedia_create_entry_discard_title.get(),
			message = Res.string.encyclopedia_create_entry_discard_message.get(),
			confirmLabel = Res.string.encyclopedia_create_entry_discard_confirm.get(),
			cancelLabel = Res.string.encyclopedia_create_entry_discard_keep.get(),
			kind = Res.string.confirm_kind_discard.get(),
			destructive = true,
		)
	}
}
