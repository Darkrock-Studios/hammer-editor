package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
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
	AlertDialog(
		onDismissRequest = { /* Noop - require explicit button press */ },
		modifier = Modifier.width(340.dp)
	) {
		Card(
			shape = MaterialTheme.shapes.extraLarge,
			colors = CardDefaults.cardColors(
				containerColor = MaterialTheme.colorScheme.surface
			)
		) {
			Column(
				modifier = Modifier.padding(Ui.Padding.XL),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(
					title,
					color = MaterialTheme.colorScheme.onSurface,
					style = MaterialTheme.typography.headlineSmall
				)

				Spacer(modifier = Modifier.height(Ui.Padding.M))

				Text(
					message,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					style = MaterialTheme.typography.bodyMedium,
				)

				Spacer(modifier = Modifier.height(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.CenterHorizontally)
				) {
					TextButton(onClick = onCancel) {
						Text(cancelButtonText)
					}
					Button(
						onClick = onDiscard,
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.error,
							contentColor = MaterialTheme.colorScheme.onError
						)
					) {
						Text(discardButtonText)
					}
					Button(onClick = onSave) {
						Text(saveButtonText)
					}
				}
			}
		}
	}
}
