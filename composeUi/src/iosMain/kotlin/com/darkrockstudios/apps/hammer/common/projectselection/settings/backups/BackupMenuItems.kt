package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun BackupExportAction(
	onExport: () -> Unit,
	modifier: Modifier,
) {
	// no-op on iOS
}
