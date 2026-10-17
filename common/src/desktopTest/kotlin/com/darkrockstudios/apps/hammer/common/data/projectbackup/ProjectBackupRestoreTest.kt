package com.darkrockstudios.apps.hammer.common.data.projectbackup

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.createProjectScope
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import kotlinx.coroutines.test.runTest
import listedProjectId
import org.junit.jupiter.api.Test
import repositories.projectsrepository.ProjectsRepositoryBaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ProjectBackupRestoreTest : ProjectsRepositoryBaseTest() {

	private val projectDef = getProjectDef(PROJECT_1_NAME)
	private val laterWork get() = projectDef.path.toOkioPath() / "later work.md"

	@Test
	fun `Restoring over a project in use is refused and leaves the project untouched`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val projectId = repo.listedProjectId(projectDef)
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
		assertEquals(projectId, repo.listedProjectId(projectDef))
	}

	@Test
	fun `A project open under an id since lost on disk is still in use`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val backups = backupService(repo)
		val backup = assertNotNull(backups.createBackup(projectDef))
		val projectScope = createProjectScope(projectDef.copy(id = ProjectId.randomUUID()))

		val result = try {
			backups.restoreBackup(backup)
		} finally {
			projectScope.close()
		}

		assertEquals(BackupRestoreResult.ProjectInUse, result)
	}

	@Test
	fun `Restoring over a project replaces its content and keeps its id`() = scope.runTest {
		createProject(ffs, PROJECT_1_NAME)
		val repo = projectsRepository()
		val projectId = repo.listedProjectId(projectDef)
		val backups = backupService(repo)
		val backup = assertNotNull(backups.createBackup(projectDef))
		ffs.write(laterWork) { writeUtf8("written after the backup") }

		assertEquals(BackupRestoreResult.Restored, backups.restoreBackup(backup))

		assertFalse(ffs.exists(laterWork))
		assertEquals(projectId, repo.listedProjectId(projectDef))
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
