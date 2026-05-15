package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific backup export action button.
 * Desktop: "Show in Folder" — opens parent directory in file explorer.
 * Android: "Export" — shares backup file via share intent.
 * iOS: renders nothing.
 */
@Composable
expect fun BackupExportAction(
	onExport: () -> Unit,
	modifier: Modifier = Modifier,
)
