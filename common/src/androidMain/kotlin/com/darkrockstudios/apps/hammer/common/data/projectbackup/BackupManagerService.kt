package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.util.AndroidShareDatasource
import java.io.File

actual class BackupManagerService(
	private val shareDatasource: AndroidShareDatasource
) {
	actual fun exportBackup(backup: ProjectBackupDef) {
		val backupFile = File(backup.path.path)

		shareDatasource.shareFile(
			file = backupFile,
			mimeType = "application/zip",
			chooserTitle = "Export Backup"
		)
	}
}
