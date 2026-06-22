package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

@Composable
fun ProjectRenameDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	close: () -> Unit
) {
	var projectName by rememberSaveable { mutableStateOf(projectDef.name) }

	val validation = rememberNameValidation(projectName, NameKind.Project)

	fun submit() {
		if (validation.isValid) {
			component.renameProject(projectDef, projectName)
			close()
		}
	}

	FormDialog(
		visible = true,
		marker = "§ RENAME",
		meta = "PROJECT",
		title = Res.string.rename_project_title.get(),
		confirmLabel = Res.string.rename_project_button.get(),
		cancelLabel = Res.string.rename_project_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = close,
		onDismiss = close,
		confirmEnabled = validation.isValid,
	) {
		FormField(
			value = projectName,
			onValueChange = { projectName = it },
			label = Res.string.rename_project_heading.get(),
			autoFocus = true,
			error = validation.fieldError(projectName),
			onImeAction = ::submit,
		)
	}
}
