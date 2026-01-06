package com.darkrockstudios.apps.hammer.common.data.projectbackup

import io.github.aakira.napier.Napier

actual class BackupManagerService {
	actual fun exportBackup(backup: ProjectBackupDef) {
		Napier.w("Backup export not implemented for iOS")
		// iOS implementation - future work
	}
}
