package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.runtime.Composable

/** The backup menu's export item: Show in folder on desktop, Export (share) on Android, none on iOS. */
@Composable
expect fun backupExportLabel(): String?
