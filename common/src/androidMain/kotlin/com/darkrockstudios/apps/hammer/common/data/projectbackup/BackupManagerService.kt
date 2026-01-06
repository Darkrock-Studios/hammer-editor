package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.util.AndroidShareService
import java.io.File

actual class BackupManagerService(
	private val shareService: AndroidShareService
) {
	actual fun exportBackup(backup: ProjectBackupDef) {
		val backupFile = File(backup.path.path)

		shareService.shareFile(
			file = backupFile,
			mimeType = "application/zip",
			chooserTitle = "Export Backup"
		)
	}
}
