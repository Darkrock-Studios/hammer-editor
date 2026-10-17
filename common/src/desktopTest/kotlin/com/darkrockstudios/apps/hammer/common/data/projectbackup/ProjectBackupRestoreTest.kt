package com.darkrockstudios.apps.hammer.common.data.projectbackup

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import repositories.projectsrepository.ProjectsRepositoryBaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectBackupRestoreTest : ProjectsRepositoryBaseTest() {

	private val projectDef = getProjectDef(PROJECT_1_NAME)
	private val laterWork get() = projectDef.path.toOkioPath() / "later work.md"

	private fun backupService(repo: ProjectsRepository): ProjectBackupService {
		every { settings.maxBackups } returns 5
		val clock = object : Clock {
			override fun now(): Instant = Instant.parse("2025-12-28T16:29:00Z")
		}
		return ProjectBackupService(ffs, repo, settingsRepo, clock, mockk(relaxed = true), projectsMetaDatasource)
	}

	@Test
	fun `Restoring over a project in use is refused and leaves the project untouched`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val projectId = repo.getProjectId(projectDef)
		val backups = backupService(repo)
		val backup = assertNotNull(backups.createBackup(projectDef))
		ffs.write(laterWork) { writeUtf8("written after the backup") }
		val projectScope = createProjectScope(projectDef)

		val result = try {
			backups.restoreBackup(backup)
		} finally {
			projectScope.close()
		}

		assertEquals(BackupRestoreResult.ProjectInUse, result)
		assertEquals("written after the backup", ffs.read(laterWork) { readUtf8() })
		assertEquals(projectId, repo.getProjectId(projectDef))
	}

	@Test
	fun `Restoring over a project replaces its content and keeps its id`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val projectId = repo.getProjectId(projectDef)
		val backups = backupService(repo)
		val backup = assertNotNull(backups.createBackup(projectDef))
		ffs.write(laterWork) { writeUtf8("written after the backup") }

		assertEquals(BackupRestoreResult.Restored, backups.restoreBackup(backup))

		assertFalse(ffs.exists(laterWork))
		assertEquals(projectId, repo.getProjectId(projectDef))
	}

	@Test
	fun `Restoring a deleted project's backup puts the project back`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val backups = backupService(repo)
		val backup = assertNotNull(backups.createBackup(projectDef))
		repo.deleteProject(projectDef)

		assertEquals(BackupRestoreResult.Restored, backups.restoreBackup(backup))

		assertEquals(listOf(PROJECT_1_NAME), repo.getProjects().map { it.name })
	}
}
