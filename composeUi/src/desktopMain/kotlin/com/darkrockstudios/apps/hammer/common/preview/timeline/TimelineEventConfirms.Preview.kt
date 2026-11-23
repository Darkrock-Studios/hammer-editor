package com.darkrockstudios.apps.hammer.common.preview.timeline

import com.darkrockstudios.apps.hammer.common.preview.previewScrim
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationCard
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.confirm_kind_delete
import com.darkrockstudios.apps.hammer.confirm_kind_discard
import com.darkrockstudios.apps.hammer.timeline_view_confirm_cancel
import com.darkrockstudios.apps.hammer.timeline_view_confirm_delete_button
import com.darkrockstudios.apps.hammer.timeline_view_discard_confirm
import com.darkrockstudios.apps.hammer.timeline_view_discard_keep
import com.darkrockstudios.apps.hammer.timeline_view_confirm_delete_message
import com.darkrockstudios.apps.hammer.timeline_view_confirm_delete_title
import com.darkrockstudios.apps.hammer.timeline_view_discard_message
import com.darkrockstudios.apps.hammer.timeline_view_discard_title

@Composable
private fun EventConfirm(
	title: String,
	message: String,
	confirmLabel: String,
	cancelLabel: String,
	kind: String,
	destructive: Boolean,
) {
	ConfirmationCard(
		title = title,
		message = message,
		confirmLabel = confirmLabel,
		onConfirm = {},
		cancelLabel = cancelLabel,
		onCancel = {},
		destructive = destructive,
		kind = kind,
	)
}

/** The timeline event's discard and delete confirms, stacked over a scrim. */
@Composable
internal fun TimelineEventConfirmsScrimPreview() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.previewScrim(),
		verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		EventConfirm(
			title = Res.string.timeline_view_discard_title.get(),
			message = Res.string.timeline_view_discard_message.get(),
			confirmLabel = Res.string.timeline_view_discard_confirm.get(),
			cancelLabel = Res.string.timeline_view_discard_keep.get(),
			kind = Res.string.confirm_kind_discard.get(),
			destructive = true,
		)
		EventConfirm(
			title = Res.string.timeline_view_confirm_delete_title.get(),
			message = Res.string.timeline_view_confirm_delete_message.get(),
			confirmLabel = Res.string.timeline_view_confirm_delete_button.get(),
			cancelLabel = Res.string.timeline_view_confirm_cancel.get(),
			kind = Res.string.confirm_kind_delete.get(),
			destructive = true,
		)
	}
}
