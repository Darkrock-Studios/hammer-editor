package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun SceneDeleteDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()

	var messageText by remember { mutableStateOf("") }
	LaunchedEffect(scene.name) {
		messageText = strRes.get(Res.string.scene_delete_dialog_message, scene.name)
	}

	SimpleDialog(
		onCloseRequest = { dismissDialog(false) },
		visible = true,
		title = Res.string.scene_delete_dialog_title.get()
	) {
		Box(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.M)) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
					.padding(Ui.Padding.XL)
			) {
				Text(
					messageText,
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.L),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(onClick = { dismissDialog(true) }) {
						Text(Res.string.scene_delete_dialog_delete_button.get())
					}
					Button(onClick = { dismissDialog(false) }) {
						Text(Res.string.scene_delete_dialog_dismiss_button.get())
					}
				}
			}
		}
	}
}