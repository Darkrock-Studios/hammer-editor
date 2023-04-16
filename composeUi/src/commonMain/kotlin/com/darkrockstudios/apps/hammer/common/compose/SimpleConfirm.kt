package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SimpleConfirm(
	title: String,
	message: String? = null,
	implicitCancel: Boolean = true,
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
		} else null,
		buttons = {
			Row(
				modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Button(onClick = { onConfirm() }) {
					Text("Yes")
				}

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