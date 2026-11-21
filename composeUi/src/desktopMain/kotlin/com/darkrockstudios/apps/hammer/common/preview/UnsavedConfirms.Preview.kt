package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationCard
import com.darkrockstudios.apps.hammer.common.compose.IndexStripCard
import com.darkrockstudios.apps.hammer.common.compose.UnsavedScenesActions
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.unsaved_encyclopedia_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_keep
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_negative_button
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_neutral_button
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_positive_button
import com.darkrockstudios.apps.hammer.unsaved_notes_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_scenes_dialog_message
import com.darkrockstudios.apps.hammer.unsaved_scenes_dialog_title
import com.darkrockstudios.apps.hammer.unsaved_entity_dialog_title
import com.darkrockstudios.apps.hammer.unsaved_timeline_dialog_message
import org.jetbrains.compose.resources.StringResource
import com.darkrockstudios.apps.hammer.confirm_kind_discard
import com.darkrockstudios.apps.hammer.confirm_kind_unsaved

/** The unsaved scenes prompt shown when closing a project, over a scrim. */
@Composable
internal fun UnsavedScenesScrimPreview() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.previewScrim(),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		IndexStripCard(
			title = Res.string.unsaved_scenes_dialog_title.get(),
			message = Res.string.unsaved_scenes_dialog_message.get(),
			destructive = true,
			kind = Res.string.confirm_kind_unsaved.get(),
		) {
			UnsavedScenesActions(
				saveButtonText = Res.string.unsaved_entity_dialog_positive_button.get(),
				discardButtonText = Res.string.unsaved_entity_dialog_negative_button.get(),
				cancelButtonText = Res.string.unsaved_entity_dialog_neutral_button.get(),
				onSave = {},
				onDiscard = {},
				onCancel = {},
			)
		}
	}
}

/** The unsaved note, encyclopedia entry and timeline event prompts, stacked over a scrim. */
@Composable
internal fun UnsavedEntityConfirmsScrimPreview() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.previewScrim(),
		verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		UnsavedEntityCard(Res.string.unsaved_notes_dialog_message)
		UnsavedEntityCard(Res.string.unsaved_encyclopedia_dialog_message)
		UnsavedEntityCard(Res.string.unsaved_timeline_dialog_message)
	}
}

@Composable
private fun UnsavedEntityCard(message: StringResource) {
	ConfirmationCard(
		title = Res.string.unsaved_entity_dialog_title.get(),
		message = message.get(),
		destructive = true,
		kind = Res.string.confirm_kind_discard.get(),
		confirmLabel = Res.string.unsaved_entity_dialog_negative_button.get(),
		onConfirm = {},
		cancelLabel = Res.string.unsaved_entity_dialog_keep.get(),
		onCancel = {},
	)
}
