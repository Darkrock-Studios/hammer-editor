package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
fun ProjectCreateDialog(show: Boolean, component: ProjectsList, close: () -> Unit) {
	val state by component.state.subscribeAsState()
	val projectName = state.createDialogProjectName

	val validation = rememberNameValidation(projectName, NameKind.Project)

	fun submit() {
		if (validation.isValid) component.createProject(projectName)
	}

	FormDialog(
		visible = show,
		marker = "§ NEW",
		meta = "PROJECT",
		title = Res.string.create_project_title.get(),
		confirmLabel = Res.string.create_project_button.get(),
		cancelLabel = Res.string.create_project_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = close,
		onDismiss = close,
		confirmEnabled = validation.isValid,
		mastheadAction = {
			HdMastheadAction(
				label = Res.string.create_project_import_button.get(),
				onClick = component::beginProjectImport,
			)
		},
	) {
		FormField(
			value = projectName,
			onValueChange = { component.onProjectNameUpdate(it) },
			label = Res.string.create_project_heading.get(),
			autoFocus = true,
			error = validation.fieldError(projectName),
			onImeAction = ::submit,
		)
	}
}
