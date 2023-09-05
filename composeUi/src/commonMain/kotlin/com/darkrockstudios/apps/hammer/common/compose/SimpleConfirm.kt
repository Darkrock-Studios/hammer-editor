package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.moko.get

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
		//modifier = Modifier.requiredWidthIn(256.dp, 768.dp),
		onDismissRequest = { if (implicitCancel) onDismiss() },
		title = {
			Text(
				title,
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onBackground,
				fontWeight = FontWeight.Bold,
				modifier = Modifier.padding(Ui.Padding.XL)
			)
		},
		text = if (message != null) {
			{
				Text(
					message,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.padding(Ui.Padding.XL)
				)
			}
		} else {
			null
		},
		confirmButton = {
			Button(onClick = { onConfirm() }) {
				Text(positiveButton ?: MR.strings.confirm_dialog_positive.get())
			}
		},
		dismissButton = {
			Button(onClick = {
				if (onNegative != null) {
					onNegative()
				} else {
					onDismiss()
				}
			}) {
				Text(
					negativeButton ?: MR.strings.confirm_dialog_negative.get(),
					fontStyle = FontStyle.Italic
				)
			}
		}
	)
}