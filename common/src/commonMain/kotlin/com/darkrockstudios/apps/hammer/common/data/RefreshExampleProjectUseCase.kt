package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import io.github.aakira.napier.Napier

/**
 * Replaces an example installed from an older bundle, [existing] if it is still there, with the
 * current one, backing it up first. A deleted example stays deleted. One that has synced is kept:
 * account sync would adopt a fresh copy onto the server's old one by name.
 */
class RefreshExampleProjectUseCase(
	private val exampleProjectRepository: ExampleProjectRepository,
	private val projectBackupService: ProjectBackupService,
) {
	suspend operator fun invoke(existing: ProjectDef?) {
		if (!exampleProjectRepository.isOutdated()) return

		if (existing == null || exampleProjectRepository.hasSynced(existing)) {
			exampleProjectRepository.recordInstalled()
			return
		}
		if (projectBackupService.supportsBackup() && projectBackupService.createBackup(existing) == null) {
			Napier.w("Could not back up the example project; leaving it as it is")
			return
		}
		Napier.i("Replacing the example project with the current bundle")
		exampleProjectRepository.install(existing.path)
	}
}
