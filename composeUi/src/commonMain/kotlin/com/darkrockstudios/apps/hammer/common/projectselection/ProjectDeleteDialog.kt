package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

const val DeleteOnServerToggleTestTag = "delete-project-on-server"
const val DeleteCoauthorsNoteTestTag = "delete-project-coauthors"

@Composable
fun ProjectDeleteDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	close: () -> Unit,
	offerServerDelete: Boolean = false,
	deletesOnServer: Boolean = false,
) {
	var isOpen by remember { mutableStateOf(true) }
	var confirmationText by rememberSaveable { mutableStateOf("") }
	var alsoDeleteOnServer by rememberSaveable { mutableStateOf(false) }

	AnimatedDialog(
		visible = isOpen,
		onCloseRequest = { isOpen = false },
		onDismissed = close,
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		ProjectDeleteContent(
			projectName = projectDef.name,
			confirmationText = confirmationText,
			onConfirmationTextChange = { confirmationText = it },
			mentionCoauthors = deletesOnServer || offerServerDelete,
			offerServerDelete = offerServerDelete,
			alsoDeleteOnServer = alsoDeleteOnServer,
			onAlsoDeleteOnServerChange = { alsoDeleteOnServer = it },
			onConfirm = {
				component.deleteProject(projectDef, alsoDeleteOnServer)
				isOpen = false
			},
			onCancel = { isOpen = false },
		)
	}
}

/** The name has to be typed back before Delete enables; it stays selectable above the field for copying. */
@Composable
fun ProjectDeleteContent(
	projectName: String,
	confirmationText: String,
	onConfirmationTextChange: (String) -> Unit,
	mentionCoauthors: Boolean,
	offerServerDelete: Boolean,
	alsoDeleteOnServer: Boolean,
	onAlsoDeleteOnServerChange: (Boolean) -> Unit,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	val isConfirmed = confirmationText.trim().equals(projectName, ignoreCase = true)
	val showMismatch = confirmationText.isNotBlank() && !isConfirmed

	FormDialogScaffold(
		title = Res.string.delete_project_title.get(),
		confirmLabel = Res.string.delete_project_confirm.get(),
		cancelLabel = Res.string.delete_project_cancel.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
		destructive = true,
		confirmEnabled = isConfirmed,
	) {
		Text(
			text = Res.string.delete_project_warning.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.error,
		)

		if (mentionCoauthors) {
			Text(
				text = Res.string.delete_project_coauthors.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.testTag(DeleteCoauthorsNoteTestTag),
			)
		}

		Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(
				text = Res.string.delete_project_confirm_prompt.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			SelectionContainer {
				Text(
					text = projectName,
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}
		}

		FormField(
			value = confirmationText,
			onValueChange = onConfirmationTextChange,
			label = Res.string.delete_project_confirm_label.get(),
			error = if (showMismatch) Res.string.delete_project_confirm_mismatch.get() else null,
			autoFocus = true,
			autoCorrectEnabled = false,
			onImeAction = { if (isConfirmed) onConfirm() },
		)

		if (offerServerDelete) {
			HdHairlineToggleRow(
				checked = alsoDeleteOnServer,
				onCheckedChange = onAlsoDeleteOnServerChange,
				label = Res.string.delete_project_also_server.get(),
				hint = Res.string.delete_project_also_server_hint.get(),
				modifier = Modifier.testTag(DeleteOnServerToggleTestTag),
			)
		}
	}
}
