package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.delete_project_cancel
import com.darkrockstudios.apps.hammer.leave_project_confirm
import com.darkrockstudios.apps.hammer.leave_project_copy_kept
import com.darkrockstudios.apps.hammer.leave_project_owner_untouched
import com.darkrockstudios.apps.hammer.leave_project_owner_untouched_unnamed
import com.darkrockstudios.apps.hammer.leave_project_title

/** In place of the delete dialog for a project shared with this account. */
@Composable
fun ProjectLeaveDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
	ownerName: String?,
	close: () -> Unit,
) {
	var isOpen by remember { mutableStateOf(true) }

	AnimatedDialog(
		visible = isOpen,
		onCloseRequest = { isOpen = false },
		onDismissed = close,
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		ProjectLeaveContent(
			projectName = projectDef.name,
			ownerName = ownerName,
			onConfirm = {
				component.leaveProject(projectDef)
				isOpen = false
			},
			onCancel = { isOpen = false },
		)
	}
}

@Composable
fun ProjectLeaveContent(
	projectName: String,
	ownerName: String?,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	val ownerUntouched = ownerName?.takeIf { it.isNotBlank() }
		?.let { Res.string.leave_project_owner_untouched.get(it) }
		?: Res.string.leave_project_owner_untouched_unnamed.get()

	FormDialogScaffold(
		title = Res.string.leave_project_title.get(),
		subject = projectName,
		confirmLabel = Res.string.leave_project_confirm.get(),
		cancelLabel = Res.string.delete_project_cancel.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
	) {
		listOf(ownerUntouched, Res.string.leave_project_copy_kept.get()).forEach { paragraph ->
			Text(
				text = paragraph,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
