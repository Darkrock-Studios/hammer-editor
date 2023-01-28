package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ConfirmDialog(
	title: String,
	message: String,
	confirmButton: String,
	onClose: (delete: Boolean) -> Unit
) {
	MpDialog(
		visible = true,
		title = title,
		modifier = Modifier.padding(Ui.Padding.XL),
		onCloseRequest = { onClose(false) }
	) {
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
			) {
				Text(
					message,
					style = MaterialTheme.typography.bodyLarge
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(
						onClick = {
							onClose(true)
						}
					) {
						Text(confirmButton)
					}

					Button(onClick = { onClose(false) }) {
						Text("Cancel")
					}
				}
			}
		}
	}
}