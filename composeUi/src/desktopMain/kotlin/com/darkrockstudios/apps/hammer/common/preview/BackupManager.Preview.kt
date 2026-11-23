package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.BackupManager
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.projectselection.settings.backups.BackupDeleteConfirmCard
import com.darkrockstudios.apps.hammer.common.projectselection.settings.backups.BackupManagerContent
import com.darkrockstudios.apps.hammer.common.projectselection.settings.backups.BackupRestoreConfirmCard
import kotlin.time.Instant

/** The backup manager with three projects, one selected with four backups, over a scrim. */
@Composable
internal fun BackupManagerScrimPreview() {
	PreviewScrim {
		BackupManagerContent(
			state = BackupManager.State(
				availableProjects = listOf("The Lighthouse Keeper", "Salt and Iron", "Notes for Book Three"),
				selectedProject = "The Lighthouse Keeper",
				backupsForSelectedProject = previewBackups,
			),
			onSelectProject = {},
			onRestore = {},
			onExport = {},
			onDelete = {},
			onClose = {},
		)
	}
}

/** The Restore backup confirm, over a scrim. */
@Composable
internal fun BackupRestoreConfirmScrimPreview() {
	ScrimCentered { BackupRestoreConfirmCard(backup = previewBackups.first(), onConfirm = {}, onCancel = {}) }
}

/** The Delete backup confirm, over a scrim. */
@Composable
internal fun BackupDeleteConfirmScrimPreview() {
	ScrimCentered { BackupDeleteConfirmCard(backup = previewBackups[2], onConfirm = {}, onCancel = {}) }
}

@Composable
private fun ScrimCentered(content: @Composable () -> Unit) {
	PreviewScrim {
		content()
	}
}

private val previewBackups = listOf(
	"2026-11-17T21:42:00Z",
	"2026-11-16T18:05:00Z",
	"2026-11-12T22:30:00Z",
	"2026-11-03T19:15:00Z",
).map { iso ->
	val date = Instant.parse(iso)
	val name = "7f3c9e2a-" + iso.replace(":", "").replace("00Z", "Z") + ".zip"
	ProjectBackupDef(
		path = HPath(path = "/backups/$name", name = name, isAbsolute = true),
		projectName = "The Lighthouse Keeper",
		date = date,
	)
}
