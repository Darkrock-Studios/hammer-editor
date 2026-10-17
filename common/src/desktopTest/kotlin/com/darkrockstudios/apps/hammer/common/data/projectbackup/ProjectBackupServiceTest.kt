package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.ForwardingFileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import testProjectId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ProjectBackupServiceTest {

	private class MutableClock(var instant: Instant) : Clock {
		override fun now(): Instant = instant
	}

	private val fsClock = MutableClock(Instant.parse("2020-01-01T00:00:00Z"))
	private val fileSystem = FakeFileSystem(fsClock)
	private val projectsRepository = mockk<ProjectsRepository>()
	private val globalSettingsStore = mockk<GlobalSettingsStore>()
	private val clock = mockk<Clock>()
	private val dataMigrator = mockk<DataMigrator>(relaxed = true)
	private val metadataDatasource = ProjectMetadataDatasource(fileSystem, createTomlSerializer())
	private val backupId = ProjectId("backed-up-project-id")
	private val liveId = ProjectId("live-project-id")

	init {
		every { projectsRepository.isProjectInUse(any<HPath>()) } returns false
		coEvery { projectsRepository.setProjectId(any(), any()) } answers { firstArg<ProjectDef>().copy(id = secondArg()) }
		coEvery { projectsRepository.resetSyncStateAfterRestore(any(), any()) } just Runs
	}

	private class TestService(
		fileSystem: FileSystem,
		projectsRepository: ProjectsRepository,
		globalSettingsStore: GlobalSettingsStore,
		clock: Clock
	) : ProjectBackupService(
		fileSystem,
		projectsRepository,
		globalSettingsStore,
		clock,
		mockk(relaxed = true),
		mockk(relaxed = true),
	) {
		override fun supportsBackup(): Boolean = true
		override suspend fun createBackup(projectDef: ProjectDef): ProjectBackupDef? = null
		override suspend fun restoreBackup(backupDef: ProjectBackupDef, targetDir: HPath) =
			BackupRestoreResult.Restored

		fun testCreateNewProjectBackupDef(projectDef: ProjectDef) = createNewProjectBackupDef(projectDef)
	}

	@Test
	fun `test date formatting at end of year`() {
		// 2025-12-28 16:29:00 UTC
		val instant = Instant.parse("2025-12-28T16:29:00Z")
		every { clock.now() } returns instant
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		val projectDef = ProjectDef(testProjectId("Test Project"), "Test Project", "/projects/Test Project".toPath().toHPath())
		val service = TestService(fileSystem, projectsRepository, globalSettingsStore, clock)
		val backupDef = service.testCreateNewProjectBackupDef(projectDef)

		val filename = backupDef.path.name
		assertEquals("Test Project-2025-12-28T162900Z.zip", filename)
	}

	@Test
	fun `test parsing backup def`() {
		val modified = Instant.parse("2025-12-28T16:29:00Z")
		val path = "/projects/.backups/Test_Project-2025-12-28T162900Z.zip".toPath()
		writeBackupFileNamed(path, modified)

		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns "/projects/Test Project".toPath()
			.toHPath()

		val service = TestService(fileSystem, projectsRepository, globalSettingsStore, clock)
		val backups = service.getBackups("Test Project")

		assertEquals(1, backups.size)
		val backupDef = backups[0]
		assertEquals("Test Project", backupDef.projectName)
		assertEquals(modified, backupDef.date)
	}

	@Test
	fun `getBackups finds legacy backups for project names containing underscores`() {
		val name = "My_Project"
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory(name) } returns
			"/projects/$name".toPath().toHPath()

		// Older clients named the file <projectName with spaces->underscores>-<date>.zip.
		writeBackupFileNamed(
			"/projects/.backups/My_Project-2026-06-20T080000Z.zip".toPath(),
			Instant.parse("2026-06-20T08:00:00Z"),
		)

		val backups = realService().getBackups(name)

		assertEquals(1, backups.size)
		assertEquals(name, backups.first().projectName)
	}

	@Test
	fun `createBackup names the file with a filesystem-safe encoding`() = runTest {
		val name = "Book: One"
		val encodedDir = ProjectsRepository.encodeForFilename(name)
		every { clock.now() } returns Instant.parse("2026-06-20T08:00:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory(name) } returns
			"/projects/$encodedDir".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/$encodedDir".toPath()
		fileSystem.createDirectories(projectDir)
		fileSystem.write(projectDir / "scene.txt") { writeUtf8("once upon a time") }

		val projectDef = ProjectDef(testProjectId(name), name, projectDir.toHPath())
		val backupDef = realService().createBackup(projectDef)

		assertNotNull(backupDef)
		val filename = backupDef.path.name
		assertFalse(filename.contains(':'), "Backup filename must not contain OS-forbidden chars: $filename")
		assertEquals(1, realService().getBackups(name).size)
	}

	@Test
	fun `getBackups recognizes project names with punctuation`() {
		val name = "Tom's Story-Notes"
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory(name) } returns
			"/projects/$name".toPath().toHPath()

		writeBackupFile(name, Instant.parse("2026-06-20T08:00:00Z"))

		val backups = realService().getBackups(name)

		assertEquals(1, backups.size)
		assertEquals(name, backups.first().projectName)
	}

	private fun realService(fs: FileSystem = fileSystem) =
		ProjectBackupService(fs, projectsRepository, globalSettingsStore, clock, dataMigrator, metadataDatasource)

	private fun writeBackupFile(projectName: String, instant: Instant): Path {
		val backupName = projectName.replace(" ", "_")
		val dir = "/projects/.backups".toPath()
		val path = dir / "$backupName-${backupDateString(instant)}.zip"
		return writeBackupFileNamed(path, instant)
	}

	// Sets the file's modification time to [modified] so backup ordering is deterministic.
	private fun writeBackupFileNamed(path: Path, modified: Instant): Path {
		fileSystem.createDirectories(path.parent!!)
		fsClock.instant = modified
		fileSystem.write(path) { writeUtf8("backup content") }
		return path
	}

	private fun backupDateString(instant: Instant): String {
		val s = instant.toString() // e.g. 2025-12-28T16:29:00Z
		val date = s.substringBefore('T')
		val time = s.substringAfter('T').removeSuffix("Z").replace(":", "")
		return "${date}T${time}Z"
	}

	@Test
	fun `getBackups returns only the requested project's backups`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Other Project") } returns
			"/projects/Other Project".toPath().toHPath()

		writeBackupFile("Test Project", Instant.parse("2025-12-28T16:29:00Z"))
		writeBackupFile("Other Project", Instant.parse("2025-12-28T17:00:00Z"))

		val backups = realService().getBackups("Test Project")

		assertEquals(1, backups.size)
		assertEquals("Test Project", backups.first().projectName)
	}

	@Test
	fun `deleteBackup removes an existing backup file`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()

		val path = writeBackupFile("Test Project", Instant.parse("2025-12-28T16:29:00Z"))
		val backups = realService().getBackups("Test Project")

		realService().deleteBackup(backups.first())

		assertFalse(fileSystem.exists(path))
	}

	@Test
	fun `deleteBackup is a no-op when the file is already gone`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		val missing = ProjectBackupDef(
			path = "/projects/.backups/Test_Project-2025-12-28T162900Z.zip".toPath().toHPath(),
			projectName = "Test Project",
			date = Instant.parse("2025-12-28T16:29:00Z"),
		)

		realService().deleteBackup(missing)
	}

	@Test
	fun `deleteBackup rethrows when deletion fails`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		// A non-empty directory at the backup path makes okio's file delete throw.
		val backupPath = "/projects/.backups/Test_Project-2025-12-28T162900Z.zip".toPath()
		fileSystem.createDirectories(backupPath)
		fileSystem.write(backupPath / "child") { writeUtf8("x") }

		val backup = ProjectBackupDef(
			path = backupPath.toHPath(),
			projectName = "Test Project",
			date = Instant.parse("2025-12-28T16:29:00Z"),
		)

		assertFailsWith<IOException> { realService().deleteBackup(backup) }
	}

	@Test
	fun `cullBackups deletes the oldest backups beyond the budget`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 2)

		val oldest = writeBackupFile("Test Project", Instant.parse("2025-12-26T10:00:00Z"))
		val middle = writeBackupFile("Test Project", Instant.parse("2025-12-27T10:00:00Z"))
		val newest = writeBackupFile("Test Project", Instant.parse("2025-12-28T10:00:00Z"))

		realService().cullBackups(ProjectDef(testProjectId("Test Project"), "Test Project", "/projects/Test Project".toPath().toHPath()))

		assertFalse(fileSystem.exists(oldest))
		assertTrue(fileSystem.exists(middle))
		assertTrue(fileSystem.exists(newest))
	}

	@Test
	fun `repeated createBackup keeps the newest backups`() = runTest {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 3)

		val projectDir = "/projects/Test Project".toPath()
		fileSystem.createDirectories(projectDir)
		fileSystem.write(projectDir / "scene.txt") { writeUtf8("once upon a time") }

		val projectDef = ProjectDef(testProjectId("Test Project"), "Test Project", projectDir.toHPath())
		val service = realService()

		// Simulate several syncs, each creating a backup a minute apart.
		val base = Instant.parse("2026-06-20T09:00:00Z")
		val created = mutableListOf<ProjectBackupDef>()
		for (i in 0 until 6) {
			val now = base.plus(i.minutes)
			fsClock.instant = now
			every { clock.now() } returns now
			service.createBackup(projectDef)?.let { created.add(it) }
		}

		val remaining = service.getBackups(projectDef.name).map { it.path.name }.toSet()
		val newestThree = created.takeLast(3).map { it.path.name }.toSet()

		assertEquals(3, remaining.size)
		assertEquals(newestThree, remaining)
	}

	@Test
	fun `cullBackups deletes oldest by file time even when filename dates are corrupt`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Alice In Wonderland") } returns
			"/projects/Alice In Wonderland".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 2)

		// A legacy backup whose filename encodes a future date (the old YYYY/hh format bug)
		// but whose file was actually written months before the others.
		val legacy = writeBackupFileNamed(
			"/projects/.backups/Alice_In_Wonderland-2026-12-29T122658Z.zip".toPath(),
			Instant.parse("2025-12-28T16:26:00Z"),
		)
		val older = writeBackupFile("Alice In Wonderland", Instant.parse("2026-06-20T08:00:11Z"))
		val newer = writeBackupFile("Alice In Wonderland", Instant.parse("2026-06-20T08:01:56Z"))
		val newest = writeBackupFile("Alice In Wonderland", Instant.parse("2026-06-20T08:02:53Z"))

		realService().cullBackups(ProjectDef(testProjectId("Alice In Wonderland"), "Alice In Wonderland", "/projects/Alice In Wonderland".toPath().toHPath()))

		assertFalse(fileSystem.exists(legacy))
		assertFalse(fileSystem.exists(older))
		assertTrue(fileSystem.exists(newer))
		assertTrue(fileSystem.exists(newest))
	}

	@Test
	fun `cullBackups keeps everything when under budget`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val a = writeBackupFile("Test Project", Instant.parse("2025-12-26T10:00:00Z"))
		val b = writeBackupFile("Test Project", Instant.parse("2025-12-27T10:00:00Z"))

		realService().cullBackups(ProjectDef(testProjectId("Test Project"), "Test Project", "/projects/Test Project".toPath().toHPath()))

		assertTrue(fileSystem.exists(a))
		assertTrue(fileSystem.exists(b))
	}

	@Test
	fun `createBackup zips the project and records it`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		fileSystem.createDirectories(projectDir)
		fileSystem.write(projectDir / "scene.txt") { writeUtf8("once upon a time") }

		val projectDef = ProjectDef(testProjectId("Test Project"), "Test Project", projectDir.toHPath())
		val service = realService()
		val backupDef = service.createBackup(projectDef)

		assertNotNull(backupDef)
		assertTrue(fileSystem.exists(backupDef.path.toOkioPath()))
		assertEquals(
			listOf(backupDef.path.name),
			service.getBackups(projectDef.name).map { it.path.name },
			"The new backup should be recorded for the project",
		)
	}

	@Test
	fun `restoreBackup unzips a backup into the target directory`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		fileSystem.createDirectories(projectDir)
		fileSystem.write(projectDir / "scene.txt") { writeUtf8("once upon a time") }

		val service = realService()
		val backupDef = service.createBackup(ProjectDef(testProjectId("Test Project"), "Test Project", projectDir.toHPath()))
		assertNotNull(backupDef)

		val target = "/restored".toPath()
		val restored = service.restoreBackup(backupDef, target.toHPath())

		assertEquals(BackupRestoreResult.Restored, restored)
		val restoredScene = fileSystem.listRecursively(target).firstOrNull { it.name == "scene.txt" }
		assertNotNull(restoredScene, "Restored project should contain its scene file")
		assertEquals("once upon a time", fileSystem.read(restoredScene) { readUtf8() })
	}

	@Test
	fun `restoreBackup puts the project files back at the root of the project directory`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		fileSystem.createDirectories(projectDir / "scenes")
		fileSystem.write(projectDir / "settings.toml") { writeUtf8("original") }
		fileSystem.write(projectDir / "scenes" / "scene.md") { writeUtf8("once upon a time") }

		val service = realService()
		val projectDef = ProjectDef(testProjectId("Test Project"), "Test Project", projectDir.toHPath())
		val backupDef = service.createBackup(projectDef)
		assertNotNull(backupDef)

		fileSystem.write(projectDir / "settings.toml") { writeUtf8("changed") }

		assertEquals(BackupRestoreResult.Restored, service.restoreBackup(backupDef, projectDef.path))

		assertEquals("original", fileSystem.read(projectDir / "settings.toml") { readUtf8() })
		assertEquals("once upon a time", fileSystem.read(projectDir / "scenes" / "scene.md") { readUtf8() })
		assertFalse(fileSystem.exists(projectDir / "Test Project"), "Project must not be nested inside itself")
	}

	@Test
	fun `restoreBackup migrates the restored project and keeps the live project's id`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		val projectDef = ProjectDef(liveId, "Test Project", projectDir.toHPath())
		fileSystem.createDirectories(projectDir)
		metadataDatasource.createMetadata(projectDef, liveId)

		val service = realService()
		val backupDef = service.createBackup(projectDef)
		assertNotNull(backupDef)

		assertEquals(BackupRestoreResult.Restored, service.restoreBackup(backupDef, projectDef.path))

		coVerifyOrder {
			dataMigrator.migrateProject(projectDef)
			projectsRepository.setProjectId(projectDef, liveId)
		}
	}

	@Test
	fun `restoreBackup keeps the live project's id when the migration fails`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)
		every { dataMigrator.migrateProject(any()) } throws IllegalStateException("migration failed")

		val projectDir = "/projects/Test Project".toPath()
		val projectDef = ProjectDef(liveId, "Test Project", projectDir.toHPath())
		fileSystem.createDirectories(projectDir)
		metadataDatasource.createMetadata(projectDef, liveId)

		val service = realService()
		val backupDef = service.createBackup(projectDef)
		assertNotNull(backupDef)

		assertEquals(BackupRestoreResult.Restored, service.restoreBackup(backupDef, projectDef.path))

		coVerify { projectsRepository.setProjectId(projectDef, liveId) }
	}

	@Test
	fun `restoreBackup gives a fresh id when no live project is replaced`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		val projectDef = ProjectDef(backupId, "Test Project", projectDir.toHPath())
		fileSystem.createDirectories(projectDir)
		metadataDatasource.createMetadata(projectDef, backupId)

		val service = realService()
		val backupDef = service.createBackup(projectDef)
		assertNotNull(backupDef)
		fileSystem.deleteRecursively(projectDir)

		assertEquals(BackupRestoreResult.Restored, service.restoreBackup(backupDef, projectDef.path))

		// The backup's own id may be tombstoned on the server, and a tombstoned id stays dead.
		val assigned = slot<ProjectId>()
		coVerify { projectsRepository.setProjectId(projectDef, capture(assigned)) }
		assertNotEquals(backupId, assigned.captured)
	}

	@Test
	fun `restoreBackup over the live project resets its sync state on the migrated files`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		val projectDef = ProjectDef(liveId, "Test Project", projectDir.toHPath())
		fileSystem.createDirectories(projectDir)
		metadataDatasource.createMetadata(projectDef, liveId)

		val service = realService()
		val backupDef = service.createBackup(projectDef)
		assertNotNull(backupDef)

		assertEquals(BackupRestoreResult.Restored, service.restoreBackup(backupDef, projectDef.path))

		// The id is unchanged, so setProjectId clears nothing; the reset must still run.
		coVerifyOrder {
			dataMigrator.migrateProject(projectDef)
			projectsRepository.resetSyncStateAfterRestore(projectDef, serverKnowsProject = false)
			projectsRepository.setProjectId(projectDef, liveId)
		}
	}

	@Test
	fun `restoreBackup flags the sync state before giving a fresh id`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		val projectDef = ProjectDef(backupId, "Test Project", projectDir.toHPath())
		fileSystem.createDirectories(projectDir)
		metadataDatasource.createMetadata(projectDef, backupId)

		val service = realService()
		val backupDef = service.createBackup(projectDef)
		assertNotNull(backupDef)
		fileSystem.deleteRecursively(projectDir)

		assertEquals(BackupRestoreResult.Restored, service.restoreBackup(backupDef, projectDef.path))

		// A flagged journal keeps its conflict baselines through the id change.
		coVerifyOrder {
			projectsRepository.resetSyncStateAfterRestore(projectDef, serverKnowsProject = false)
			projectsRepository.setProjectId(projectDef, any())
		}
	}

	@Test
	fun `restoreBackup succeeds when resetting the sync state fails`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)
		coEvery { projectsRepository.resetSyncStateAfterRestore(any(), any()) } throws IOException("disk full")

		val projectDir = "/projects/Test Project".toPath()
		fileSystem.createDirectories(projectDir)
		fileSystem.write(projectDir / "settings.toml") { writeUtf8("original") }

		val service = realService()
		val projectDef = ProjectDef(testProjectId("Test Project"), "Test Project", projectDir.toHPath())
		val backupDef = service.createBackup(projectDef)
		assertNotNull(backupDef)
		fileSystem.write(projectDir / "settings.toml") { writeUtf8("changed") }

		assertEquals(
			BackupRestoreResult.Restored,
			service.restoreBackup(backupDef, projectDef.path),
			"the content is already in place",
		)
		assertEquals("original", fileSystem.read(projectDir / "settings.toml") { readUtf8() })
	}

	@Test
	fun `restoreBackup succeeds when the restored project's id cannot be read`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		fileSystem.createDirectories(projectDir)
		fileSystem.write(projectDir / "settings.toml") { writeUtf8("original") }

		val projectDef = ProjectDef(testProjectId("Test Project"), "Test Project", projectDir.toHPath())
		val backupDef = realService().createBackup(projectDef)
		assertNotNull(backupDef)
		fileSystem.write(projectDir / "settings.toml") { writeUtf8("changed") }

		// The backup has no project.toml, so reading its id must write one.
		val unwritableMetadata = object : ForwardingFileSystem(fileSystem) {
			override fun sink(file: Path, mustCreate: Boolean): Sink {
				if (file.name == ProjectMetadata.FILENAME) throw IOException("disk full")
				return super.sink(file, mustCreate)
			}
		}
		val service = ProjectBackupService(
			fileSystem,
			projectsRepository,
			globalSettingsStore,
			clock,
			dataMigrator,
			ProjectMetadataDatasource(unwritableMetadata, createTomlSerializer()),
		)

		assertEquals(
			BackupRestoreResult.Restored,
			service.restoreBackup(backupDef, projectDef.path),
			"the content is already in place",
		)
		assertEquals("original", fileSystem.read(projectDir / "settings.toml") { readUtf8() })
	}

	@Test
	fun `getBackedUpProjects includes projects that no longer exist`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		val kept = ProjectDef(testProjectId("Kept"), "Kept", "/projects/Kept".toPath().toHPath())
		val noBackups = ProjectDef(testProjectId("No Backups"), "No Backups", "/projects/No Backups".toPath().toHPath())
		every { projectsRepository.getProjects(any()) } returns listOf(kept, noBackups)

		writeBackupFile("Kept", Instant.parse("2025-12-26T10:00:00Z"))
		writeBackupFile("Deleted", Instant.parse("2025-12-26T10:00:00Z"))
		writeBackupFile("Deleted", Instant.parse("2025-12-27T10:00:00Z"))

		assertEquals(listOf("Kept", "Deleted"), realService().getBackedUpProjects())
	}

	@Test
	fun `getBackedUpProjects lists a missing project once when it has legacy and current backups`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		every { projectsRepository.getProjects(any()) } returns emptyList()

		val legacy = writeBackupFile("My Story", Instant.parse("2025-12-26T10:00:00Z"))
		fileSystem.write(legacy.parent!! / "My Story-2025-12-27T100000Z.zip") { writeUtf8("zip") }

		assertEquals(listOf("My Story"), realService().getBackedUpProjects())
	}

	@Test
	fun `restoreBackup leaves the project untouched when it cannot be replaced`() = runTest {
		every { clock.now() } returns Instant.parse("2025-12-28T16:29:00Z")
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", maxBackups = 5)

		val projectDir = "/projects/Test Project".toPath()
		val projectDef = ProjectDef(backupId, "Test Project", projectDir.toHPath())
		fileSystem.createDirectories(projectDir)
		metadataDatasource.createMetadata(projectDef, backupId)

		val service = realService()
		val backupDef = service.createBackup(projectDef)
		assertNotNull(backupDef)

		metadataDatasource.createMetadata(projectDef, liveId)
		fileSystem.write(projectDir / "notes.md") { writeUtf8("newer work") }

		val failingSwap = object : ForwardingFileSystem(fileSystem) {
			override fun atomicMove(source: Path, target: Path) {
				if (target == projectDir && "new" in source.segments) throw IOException("locked")
				super.atomicMove(source, target)
			}
		}
		assertEquals(BackupRestoreResult.Failed, realService(failingSwap).restoreBackup(backupDef, projectDef.path))

		assertEquals(liveId, metadataDatasource.loadProjectId(projectDef))
		assertEquals("newer work", fileSystem.read(projectDir / "notes.md") { readUtf8() })
	}

	@Test
	fun `restoreBackup fails when the backup file is missing`() = runTest {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		val missing = ProjectBackupDef(
			path = "/projects/.backups/Nope-2025-12-28T162900Z.zip".toPath().toHPath(),
			projectName = "Nope",
			date = Instant.parse("2025-12-28T16:29:00Z"),
		)

		assertEquals(BackupRestoreResult.Failed, realService().restoreBackup(missing, "/restored".toPath().toHPath()))
	}
}
