package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.confirm_kind_unsaved
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
fun UnsavedScenesConfirmDialog(
	title: String,
	message: String,
	saveButtonText: String,
	discardButtonText: String,
	cancelButtonText: String,
	onSave: () -> Unit,
	onDiscard: () -> Unit,
	onCancel: () -> Unit,
) {
	IndexStripDialog(
		visible = true,
		title = title,
		message = message,
		onDismiss = onCancel,
		destructive = true,
		kind = Res.string.confirm_kind_unsaved.get(),
		implicitDismiss = false,
	) {
		UnsavedScenesActions(
			saveButtonText = saveButtonText,
			discardButtonText = discardButtonText,
			cancelButtonText = cancelButtonText,
			onSave = onSave,
			onDiscard = onDiscard,
			onCancel = onCancel,
		)
	}
}

@Composable
internal fun RowScope.UnsavedScenesActions(
	saveButtonText: String,
	discardButtonText: String,
	cancelButtonText: String,
	onSave: () -> Unit,
	onDiscard: () -> Unit,
	onCancel: () -> Unit,
) {
	HdHairlineButton(label = cancelButtonText, onClick = onCancel)
	HdHairlineButton(label = discardButtonText, onClick = onDiscard, danger = true)
	HdHairlineButton(label = saveButtonText, onClick = onSave, emphasised = true)
}
