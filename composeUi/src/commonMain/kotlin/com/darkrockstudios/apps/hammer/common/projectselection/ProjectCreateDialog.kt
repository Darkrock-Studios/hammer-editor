package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException

@Composable
fun ProjectCreateDialog(show: Boolean, component: ProjectsList, close: () -> Unit) {
	val state by component.state.subscribeAsState()
	val strRes = rememberStrRes()
	val projectName = state.createDialogProjectName

	val validationResult = remember(projectName) {
		ProjectsRepository.validateFileName(projectName.trim().ifEmpty { null })
	}
	val isValid = validationResult.isSuccess

	val errorMessage by produceState<String?>(null, validationResult) {
		value = if (isFailure(validationResult)) {
			when (val exception = validationResult.exception) {
				is ValidationFailedException -> strRes.get(exception.errorMessage)
				else -> validationResult.displayMessage?.text(strRes)
			}
		} else null
	}

	fun submit() {
		if (isValid) component.createProject(projectName)
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
		confirmEnabled = isValid,
	) {
		FormField(
			value = projectName,
			onValueChange = { component.onProjectNameUpdate(it) },
			label = Res.string.create_project_heading.get(),
			autoFocus = true,
			error = if (projectName.isNotEmpty() && !isValid) errorMessage else null,
			onImeAction = ::submit,
		)
	}
}
