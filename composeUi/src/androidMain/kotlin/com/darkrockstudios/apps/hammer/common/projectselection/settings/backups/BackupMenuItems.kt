package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.backup_manager_export_button
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
actual fun BackupExportMenuItem(
	onExport: () -> Unit,
	onDismiss: () -> Unit
) {
	DropdownMenuItem(
		text = { Text(Res.string.backup_manager_export_button.get()) },
		onClick = {
			onDismiss()
			onExport()
		}
	)
}
