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
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun SceneDeleteDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
	val strRes = rememberStrRes()

	MpDialog(
		onCloseRequest = {},
		visible = true,
		title = MR.strings.scene_delete_dialog_title.get()
	) {
		Box(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.M)) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
					.padding(Ui.Padding.XL)
			) {
				Text(
					strRes.get(MR.strings.scene_delete_dialog_message, scene.name),
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.L),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(onClick = { dismissDialog(true) }) {
						Text(MR.strings.scene_delete_dialog_delete_button.get())
					}
					Button(onClick = { dismissDialog(false) }) {
						Text(MR.strings.scene_delete_dialog_dismiss_button.get())
					}
				}
			}
		}
	}
}