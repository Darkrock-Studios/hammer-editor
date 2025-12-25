package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
	AlertDialog(
		onDismissRequest = { if (implicitCancel) onDismiss() },
		title = {
			Text(
				title,
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onSurface,
				fontWeight = FontWeight.Bold,
				modifier = Modifier.padding(Ui.Padding.L)
			)
		},
		text = if (message != null) {
			{
				Text(
					message,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(horizontal = Ui.Padding.L)
				)
			}
		} else {
			null
		},
		confirmButton = {
			Button(onClick = { onConfirm() }) {
				Text(positiveButton ?: Res.string.confirm_dialog_positive.get())
			}
		},
		dismissButton = {
			TextButton(onClick = {
				if (onNegative != null) {
					onNegative()
				} else {
					onDismiss()
				}
			}) {
				Text(negativeButton ?: Res.string.confirm_dialog_negative.get())
			}
		}
	)
}