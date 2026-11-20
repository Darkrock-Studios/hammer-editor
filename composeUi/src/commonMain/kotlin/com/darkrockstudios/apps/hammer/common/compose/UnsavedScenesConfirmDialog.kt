package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

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
		kind = "UNSAVED",
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
	TextButton(
		onClick = onCancel,
		shape = RoundedCornerShape(4.dp),
	) {
		Text(cancelButtonText)
	}
	TextButton(
		onClick = onDiscard,
		shape = RoundedCornerShape(4.dp),
		colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
	) {
		Text(discardButtonText)
	}
	Button(
		onClick = onSave,
		shape = RoundedCornerShape(4.dp),
	) {
		Text(saveButtonText)
	}
}
