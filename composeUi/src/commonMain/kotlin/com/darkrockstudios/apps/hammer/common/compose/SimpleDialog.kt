package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
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
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
fun SimpleDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	modifier: Modifier = Modifier,
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
								contentDescription = MR.strings.close_dialog_button.get(),
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