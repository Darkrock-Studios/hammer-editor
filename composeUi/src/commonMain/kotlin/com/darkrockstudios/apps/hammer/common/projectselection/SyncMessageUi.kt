package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.darkrockstudios.apps.hammer.common.data.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.projectsync.SyncLogMessage

fun SyncLogMessage.getBackgroundColor(): Color =
	when (level) {
		SyncLogLevel.DEBUG -> Color.LightGray
		SyncLogLevel.INFO -> Color.Blue
		SyncLogLevel.WARN -> Color.Yellow
		SyncLogLevel.ERROR -> Color.Red
	}

fun SyncLogMessage.getForegroundColor(): Color =
	when (level) {
		SyncLogLevel.DEBUG -> Color.Black
		SyncLogLevel.INFO -> Color.White
		SyncLogLevel.WARN -> Color.Black
		SyncLogLevel.ERROR -> Color.White
	}

fun SyncLogMessage.getIcon(): ImageVector =
	when (level) {
		SyncLogLevel.DEBUG -> Icons.Default.BugReport
		SyncLogLevel.INFO -> Icons.Default.Notes
		SyncLogLevel.WARN -> Icons.Default.Warning
		SyncLogLevel.ERROR -> Icons.Default.Error
	}