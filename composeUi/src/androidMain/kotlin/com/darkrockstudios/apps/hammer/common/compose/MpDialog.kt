package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = title,
								style = MaterialTheme.typography.titleLarge,
								fontWeight = FontWeight.Bold,
								modifier = Modifier
									.weight(1f)
									.padding(Ui.Padding.XL)
							)

							Icon(
								Icons.Default.Close,
								contentDescription = "Close Dialog",
								modifier = Modifier
									.padding(Ui.Padding.L)
									.clickable {
										onCloseRequest()
									}
							)
						}
						Spacer(modifier = Modifier.size(Ui.Padding.L))
						content()
					}
				}
			}
		)
	}
}