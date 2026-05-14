package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.confirm_dialog_negative
import com.darkrockstudios.apps.hammer.confirm_dialog_positive

@Composable
fun SimpleConfirm(
	title: String,
	message: String? = null,
	positiveButton: String? = null,
	negativeButton: String? = null,
	implicitCancel: Boolean = true,
	onNegative: (() -> Unit)? = null,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
) {
	ConfirmationDialog(
		visible = true,
		title = title,
		message = message ?: "",
		confirmLabel = positiveButton ?: Res.string.confirm_dialog_positive.get(),
		cancelLabel = negativeButton ?: Res.string.confirm_dialog_negative.get(),
		onConfirm = onConfirm,
		onDismiss = onDismiss,
		onCancel = onNegative,
		implicitDismiss = implicitCancel,
	)
}
