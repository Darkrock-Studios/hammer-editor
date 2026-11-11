package com.darkrockstudios.apps.hammer.common.data.projectbackup

import co.touchlab.kermit.Logger
import java.awt.Desktop
import java.io.File

actual class BackupManagerService {
	actual fun exportBackup(backup: ProjectBackupDef) {
		try {
			val backupFile = File(backup.path.path)
			val parentDir = backupFile.parentFile

			if (parentDir?.exists() == true && Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(parentDir)
				Logger.i("Opened backup directory: ${parentDir.path}")
			} else {
				Logger.w("Cannot open directory: ${parentDir?.path}")
			}
			// Desktop.open can throw IO/security/unsupported errors; report and continue.
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Logger.e("Failed to open backup directory", e)
		}
	}
}
