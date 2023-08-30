package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCreateDialog(show: Boolean, component: ProjectsList, close: () -> Unit) {
	SimpleDialog(
		onCloseRequest = close,
		visible = show,
		title = MR.strings.create_project_title.get(),
	) {
		val state by component.state.subscribeAsState()
		Box(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL)) {
			Column(
				modifier = Modifier
					.width(IntrinsicSize.Max)
					.align(Alignment.Center)
			) {
				TextField(
					value = state.createDialogProjectName,
					onValueChange = { component.onProjectNameUpdate(it) },
					label = { Text(MR.strings.create_project_heading.get()) },
				)

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				Button(onClick = {
					component.createProject(state.createDialogProjectName)
				}) {
					Text(MR.strings.create_project_button.get())
				}
			}
		}
	}
}