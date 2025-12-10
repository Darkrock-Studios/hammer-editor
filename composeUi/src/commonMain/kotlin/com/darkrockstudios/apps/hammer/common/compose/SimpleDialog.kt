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
import androidx.compose.ui.window.Dialog
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.close_dialog_button
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
fun SimpleDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	modifier: Modifier = Modifier.padding(Ui.Padding.XL),
	content: @Composable ColumnScope.() -> Unit
) {
	if (visible) {
		Dialog(
			onDismissRequest = onCloseRequest,
			content = {
				Card(modifier = Modifier.wrapContentSize()) {
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
								contentDescription = Res.string.close_dialog_button.get(),
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