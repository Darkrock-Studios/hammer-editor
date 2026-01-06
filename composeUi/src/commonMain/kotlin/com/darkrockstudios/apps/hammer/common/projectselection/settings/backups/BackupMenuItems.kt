package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.runtime.Composable

/**
 * Platform-specific backup export menu item.
 * Desktop: "Show in Folder" - Opens parent directory in file explorer
 * Android: "Export" - Shares backup file via share intent
 * iOS: No menu item (stub)
 */
@Composable
expect fun BackupExportMenuItem(
	onExport: () -> Unit,
	onDismiss: () -> Unit
)
