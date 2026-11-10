package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.project_settings_sync_dialog_close
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
	AnimatedDialog(visible = isOpen, onCloseRequest = { isOpen = false }, onDismissed = close) {
		HdHairlineDialogShell(
			title = Res.string.project_settings_sync_off_title.get(),
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.project_settings_sync_dialog_close.get(),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
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
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
				) {
					HdHairlineButton(
						label = Res.string.project_settings_sync_off_cancel.get(),
						onClick = { requestDismiss() },
					)
					HdHairlineButton(
						label = Res.string.project_settings_sync_off_confirm.get(),
						onClick = {
							component.confirmProjectSyncOff(projectDef)
							requestDismiss()
						},
						emphasised = true,
					)
				}
			}
		}
	}
}
