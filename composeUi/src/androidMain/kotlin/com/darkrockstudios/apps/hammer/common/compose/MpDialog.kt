package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Dialog

@Composable
actual fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	size: DpSize?,
	content: @Composable () -> Unit
) {
	if (visible) {
		val modifier = if (size != null) {
			Modifier.size(size)
		} else {
			Modifier
		}

		Dialog(
			onDismissRequest = onCloseRequest,
			content = {
				Card {
					Column(modifier = modifier) {
						Text(
							text = title,
							style = MaterialTheme.typography.titleLarge
						)
						Spacer(modifier = Modifier.size(Ui.Padding.L))
						content()
					}
				}
			}
		)
	}
}