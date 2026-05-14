package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ValidationFailedException

@Composable
fun ProjectRenameDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	close: () -> Unit
) {
	val strRes = rememberStrRes()
	var projectName by rememberSaveable { mutableStateOf(projectDef.name) }

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
		if (isValid) {
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
		confirmEnabled = isValid,
	) {
		FormField(
			value = projectName,
			onValueChange = { projectName = it },
			label = Res.string.rename_project_heading.get(),
			autoFocus = true,
			error = if (projectName.isNotEmpty() && !isValid) errorMessage else null,
			onImeAction = ::submit,
		)
	}
}
