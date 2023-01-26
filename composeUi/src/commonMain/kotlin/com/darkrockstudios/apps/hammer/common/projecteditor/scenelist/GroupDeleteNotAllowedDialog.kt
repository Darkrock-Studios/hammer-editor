package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun GroupDeleteNotAllowedDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
	MpDialog(
		onCloseRequest = {},
		visible = true,
		modifier = Modifier.padding(Ui.Padding.XL),
		title = "Cannot Delete Group"
	) {
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
					.padding(Ui.Padding.XL)
			) {
				Text(
					"You cannot delete a group while it still has children:\n\"${scene.name}\"",
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Button(onClick = { dismissDialog(false) }) {
					Text("Okay")
				}
			}
		}
	}
}