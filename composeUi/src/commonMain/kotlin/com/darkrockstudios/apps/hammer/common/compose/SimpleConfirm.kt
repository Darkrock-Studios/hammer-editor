package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SimpleConfirm(
	title: String,
	message: String,
	implicitCancel: Boolean = true,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
) {
	AlertDialog(
		modifier = Modifier.requiredWidth(256.dp),
		onDismissRequest = { if (implicitCancel) onDismiss() },
		title = {
			Text(
				title,
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onBackground,
			)
		},
		text = {
			Text(
				message,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onBackground,
			)
		},
		buttons = {
			Row(modifier = Modifier.padding(Ui.Padding.M)) {
				Button(onClick = { onConfirm() }) {
					Text("Yes")
				}

				Spacer(modifier = Modifier.weight(1f))

				Button(onClick = { onDismiss() }) {
					Text(
						"No",
						fontStyle = FontStyle.Italic
					)
				}
			}
		}
	)
}