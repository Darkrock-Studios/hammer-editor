package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.group_cannot_delete_dialog_message
import com.darkrockstudios.apps.hammer.group_cannot_delete_dialog_title
import com.darkrockstudios.apps.hammer.scene_delete_dialog_dismiss_button

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun GroupDeleteNotAllowedDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
	SimpleDialog(
		onCloseRequest = { dismissDialog(false) },
		visible = true,
		title = Res.string.group_cannot_delete_dialog_title.get()
	) {
		Box(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL)) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
					.padding(Ui.Padding.XL)
			) {
				Text(
					Res.string.group_cannot_delete_dialog_message.get(scene.name),
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Button(onClick = { dismissDialog(false) }) {
					Text(Res.string.scene_delete_dialog_dismiss_button.get())
				}
			}
		}
	}
}