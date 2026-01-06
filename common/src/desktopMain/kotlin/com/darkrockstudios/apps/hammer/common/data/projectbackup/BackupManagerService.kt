package com.darkrockstudios.apps.hammer.common.data.projectbackup

import io.github.aakira.napier.Napier
import java.awt.Desktop
import java.io.File

actual class BackupManagerService {
	actual fun exportBackup(backup: ProjectBackupDef) {
		try {
			val backupFile = File(backup.path.path)
			val parentDir = backupFile.parentFile

			if (parentDir?.exists() == true && Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(parentDir)
				Napier.i("Opened backup directory: ${parentDir.path}")
			} else {
				Napier.w("Cannot open directory: ${parentDir?.path}")
			}
		} catch (e: Exception) {
			Napier.e("Failed to open backup directory", e)
		}
	}
}
