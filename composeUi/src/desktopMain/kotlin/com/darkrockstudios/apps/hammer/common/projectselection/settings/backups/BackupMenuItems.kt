package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.backup_manager_show_button
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
actual fun BackupExportAction(
	onExport: () -> Unit,
	modifier: Modifier,
) {
	HdHairlineButton(
		label = Res.string.backup_manager_show_button.get(),
		onClick = onExport,
		modifier = modifier,
	)
}
