package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.rename_project_button
import com.darkrockstudios.apps.hammer.rename_project_heading
import com.darkrockstudios.apps.hammer.rename_project_title

@Composable
fun ProjectRenameDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	close: () -> Unit
) {
	SimpleDialog(
		onCloseRequest = close,
		visible = true,
		title = Res.string.rename_project_title.get(),
	) {
		var nameTextField by rememberSaveable { mutableStateOf(projectDef.name) }
		Box(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL)) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
			) {
				TextField(
					value = nameTextField,
					onValueChange = { nameTextField = it },
					label = { Text(Res.string.rename_project_heading.get()) },
					singleLine = true,
				)

				SpacerL()

				Button(onClick = {
					component.renameProject(projectDef, nameTextField)
					close()
				}) {
					Text(Res.string.rename_project_button.get())
				}
			}
		}
	}
}