package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

@Composable
fun ProjectRenameDialog(
	component: ProjectsList,
	projectDef: ProjectDef?,
	close: () -> Unit
) {
	SimpleDialog(
		onCloseRequest = close,
		visible = projectDef != null,
		title = MR.strings.rename_project_title.get(),
	) {
		if (projectDef == null) return@SimpleDialog

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
					label = { Text(MR.strings.rename_project_heading.get()) },
					singleLine = true,
				)

				SpacerL()

				Button(onClick = {
					component.renameProject(projectDef, nameTextField)
					close()
				}) {
					Text(MR.strings.rename_project_button.get())
				}
			}
		}
	}
}