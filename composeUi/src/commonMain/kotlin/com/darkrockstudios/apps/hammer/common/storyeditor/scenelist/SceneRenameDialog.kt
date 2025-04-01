package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun SceneRenameDialog(
	scene: SceneItem,
	error: String?,
	dismissDialog: (String?) -> Unit
) {
	var nameText by rememberSaveable { mutableStateOf(scene.name) }

	SimpleDialog(
		onCloseRequest = { dismissDialog(null) },
		visible = true,
		title = MR.strings.scene_rename_dialog_title.get()
	) {
		Box(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.M)) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
					.padding(Ui.Padding.XL)
			) {
				OutlinedTextField(
					value = nameText,
					onValueChange = { nameText = it },
					label = { Text(MR.strings.scene_rename_dialog_label.get()) },
					singleLine = true,
				)

				Text(
					error ?: "",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
					fontStyle = FontStyle.Italic,
					modifier = Modifier.padding(Ui.Padding.S)
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.L),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(onClick = { dismissDialog(nameText) }) {
						Text(MR.strings.scene_rename_dialog_rename_button.get())
					}
					Button(onClick = { dismissDialog(null) }) {
						Text(MR.strings.scene_delete_dialog_dismiss_button.get())
					}
				}
			}
		}
	}
}
