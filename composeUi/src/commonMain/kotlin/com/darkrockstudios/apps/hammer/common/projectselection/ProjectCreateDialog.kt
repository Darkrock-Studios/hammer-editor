package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
fun ProjectCreateDialog(show: Boolean, component: ProjectsList, close: () -> Unit) {
	val state by component.state.subscribeAsState()
	val projectName = state.createDialogProjectName

	val validation = rememberNameValidation(projectName, NameKind.Project)
	var showImportHelp by remember { mutableStateOf(false) }

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
			ProjectCreateMastheadActions(
				onHelp = { showImportHelp = true },
				onImport = component::beginProjectImport,
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

	if (showImportHelp) {
		ImportHelpDialog(onDismiss = { showImportHelp = false })
	}
}

/**
 * The create-dialog masthead actions: a help affordance and the emphasised "Import" button.
 * Shared with the preview so both render the real affordances.
 */
@Composable
internal fun ProjectCreateMastheadActions(onHelp: () -> Unit, onImport: () -> Unit) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
	) {
		HdMastheadAction(label = "?", onClick = onHelp)
		ProjectCreateImportAction(onImport)
	}
}

/** The "Import" masthead action shared by the create dialog and its preview. */
@Composable
internal fun ProjectCreateImportAction(onClick: () -> Unit) {
	HdHairlineButton(
		label = "↓  ${Res.string.create_project_import_button.get()}",
		onClick = onClick,
		emphasised = true,
	)
}
