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
import com.darkrockstudios.apps.hammer.project_settings_sync_off_cancel
import com.darkrockstudios.apps.hammer.project_settings_sync_off_confirm
import com.darkrockstudios.apps.hammer.project_settings_sync_off_delete
import com.darkrockstudios.apps.hammer.project_settings_sync_off_title
import com.darkrockstudios.apps.hammer.project_settings_sync_off_unaffected

@Composable
fun ProjectSyncOffDialog(
	component: ProjectsList,
	projectDef: ProjectDef,
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
		ProjectSyncOffContent(
			projectName = projectDef.name,
			onConfirm = {
				component.confirmProjectSyncOff(projectDef)
				isOpen = false
			},
			onCancel = { isOpen = false },
		)
	}
}

@Composable
fun ProjectSyncOffContent(
	projectName: String,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	FormDialogScaffold(
		title = Res.string.project_settings_sync_off_title.get(),
		confirmLabel = Res.string.project_settings_sync_off_confirm.get(),
		cancelLabel = Res.string.project_settings_sync_off_cancel.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
	) {
		Text(
			text = projectName,
			style = MaterialTheme.typography.headlineMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
		listOf(
			Res.string.project_settings_sync_off_unaffected.get(),
			Res.string.project_settings_sync_off_delete.get(),
		).forEach { paragraph ->
			Text(
				text = paragraph,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
