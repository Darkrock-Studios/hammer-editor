package com.darkrockstudios.apps.hammer.common.data.projectbackup

enum class BackupRestoreResult {
	Restored,

	/** The target project is open in an editor or a background task, so nothing was touched. */
	ProjectInUse,

	Failed,
}
