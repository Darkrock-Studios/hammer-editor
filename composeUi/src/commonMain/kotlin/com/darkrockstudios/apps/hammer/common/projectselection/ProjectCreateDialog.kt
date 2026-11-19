package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHelpButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef

const val CreateProjectNameFieldTestTag = "create-project-name"

@Composable
fun ProjectCreateDialog(
	show: Boolean,
	component: ProjectsList,
	parent: GroupDef? = null,
	close: () -> Unit,
) {
	val state by component.state.subscribeAsState()
	val projectName = state.createDialogProjectName

	val validation = rememberNameValidation(projectName, NameKind.Project)
	var showImportHelp by remember { mutableStateOf(false) }

	fun submit() {
		if (validation.isValid) component.createProject(projectName, parent)
	}

	FormDialog(
		visible = show,
		meta = groupMeta(parent),
		title = Res.string.create_project_title.get(),
		confirmLabel = Res.string.create_project_button.get(),
		cancelLabel = Res.string.create_project_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = close,
		onDismiss = close,
		confirmEnabled = validation.isValid,
	) {
		FormField(
			value = projectName,
			onValueChange = { component.onProjectNameUpdate(it) },
			label = Res.string.create_project_heading.get(),
			modifier = Modifier.testTag(CreateProjectNameFieldTestTag),
			autoFocus = true,
			error = validation.fieldError(projectName),
			onImeAction = ::submit,
		)
		ProjectCreateImportLine(
			onHelp = { showImportHelp = true },
			onImport = component::beginProjectImport,
		)
	}

	if (showImportHelp) {
		ImportHelpDialog(onDismiss = { showImportHelp = false })
	}
}

/** Importing is the other way to start a project, offered under the name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectCreateImportLine(onHelp: () -> Unit, onImport: () -> Unit) {
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
		itemVerticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = Res.string.create_project_import_prompt.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Row(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdHairlineButton(
				label = Res.string.create_project_import_button.get(),
				onClick = onImport,
			)
			HdHelpButton(
				onClick = onHelp,
				contentDescription = Res.string.create_project_import_help_icon_description.get(),
			)
		}
	}
}
