package com.darkrockstudios.apps.hammer.common.data.projectbackup

/**
 * Platform-specific service for backup operations.
 * Each platform implements exportBackup() differently.
 */
expect class BackupManagerService {
	/**
	 * Export a backup file using platform-specific mechanism.
	 * - Desktop: Opens parent directory in file explorer
	 * - Android: Shares file via share sheet
	 * - iOS: Platform-specific sharing (future)
	 */
	fun exportBackup(backup: ProjectBackupDef)
}
