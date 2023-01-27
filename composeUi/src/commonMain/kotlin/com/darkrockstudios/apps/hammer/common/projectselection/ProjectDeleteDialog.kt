package com.darkrockstudios.apps.hammer.common.projectselection

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
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun ProjectDeleteDialog(projectDef: ProjectDef, dismissDialog: (Boolean) -> Unit) {
	MpDialog(
		onCloseRequest = {},
		visible = true,
		modifier = Modifier.padding(Ui.Padding.XL),
		title = "Delete Project"
	) {
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
					.padding(Ui.Padding.XL)
			) {
				Text(
					"Are you sure you want to delete this project:\n${projectDef.name}",
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.L),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Button(onClick = { dismissDialog(true) }) {
						Text("DELETE")
					}
					Button(onClick = { dismissDialog(false) }) {
						Text("Dismiss")
					}
				}
			}
		}
	}
}