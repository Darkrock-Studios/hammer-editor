package com.darkrockstudios.apps.hammer.common.projectselection.settings.backups

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.backup_manager_show_button
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
actual fun backupExportLabel(): String? = Res.string.backup_manager_show_button.get()
