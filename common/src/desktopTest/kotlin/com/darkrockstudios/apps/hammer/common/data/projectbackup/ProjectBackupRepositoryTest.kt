package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.mockk.every
import io.mockk.mockk
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectBackupRepositoryTest {

	private val fileSystem = FakeFileSystem()
	private val projectsRepository = mockk<ProjectsRepository>()
	private val globalSettingsRepository = mockk<GlobalSettingsRepository>()
	private val clock = mockk<Clock>()

	private class TestRepo(
		fileSystem: FileSystem,
		projectsRepository: ProjectsRepository,
		globalSettingsRepository: GlobalSettingsRepository,
		clock: Clock
	) : ProjectBackupRepository(fileSystem, projectsRepository, globalSettingsRepository, clock) {
		override fun supportsBackup(): Boolean = true
		override suspend fun createBackup(projectDef: ProjectDef): ProjectBackupDef? = null
		override suspend fun restoreBackup(backupDef: ProjectBackupDef, targetDir: HPath): Boolean = true

		fun testCreateNewProjectBackupDef(projectDef: ProjectDef) = createNewProjectBackupDef(projectDef)
	}

	@Test
	fun `test date formatting at end of year`() {
		// 2025-12-28 16:29:00 UTC
		val instant = Instant.parse("2025-12-28T16:29:00Z")
		every { clock.now() } returns instant
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		val projectDef = ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath())
		val repo = TestRepo(fileSystem, projectsRepository, globalSettingsRepository, clock)
		val backupDef = repo.testCreateNewProjectBackupDef(projectDef)

		val filename = backupDef.path.name
		assertEquals("Test_Project-2025-12-28T162900Z.zip", filename)
	}

	@Test
	fun `test parsing backup def`() {
		val filename = "Test_Project-2025-12-28T162900Z.zip"
		val dir = "/projects/.backups".toPath()
		val path = dir / filename
		fileSystem.createDirectories(dir)
		fileSystem.write(path) { writeUtf8("fake content") }

		val projectDef = ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath())
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns "/projects/Test Project".toPath()
			.toHPath()

		val repo = TestRepo(fileSystem, projectsRepository, globalSettingsRepository, clock)
		val backups = repo.getBackups(projectDef)

		assertEquals(1, backups.size)
		val backupDef = backups[0]
		assertEquals("Test Project", backupDef.projectDef.name)
		assertEquals(Instant.parse("2025-12-28T16:29:00Z"), backupDef.date)
	}
}
