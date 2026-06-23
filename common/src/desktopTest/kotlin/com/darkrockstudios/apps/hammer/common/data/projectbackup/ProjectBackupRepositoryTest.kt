package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ProjectBackupRepositoryTest {

	private class MutableClock(var instant: Instant) : Clock {
		override fun now(): Instant = instant
	}

	private val fsClock = MutableClock(Instant.parse("2020-01-01T00:00:00Z"))
	private val fileSystem = FakeFileSystem(fsClock)
	private val projectsRepository = mockk<ProjectsRepository>()
	private val globalSettingsStore = mockk<GlobalSettingsStore>()
	private val clock = mockk<Clock>()

	private class TestRepo(
		fileSystem: FileSystem,
		projectsRepository: ProjectsRepository,
		globalSettingsStore: GlobalSettingsStore,
		clock: Clock
	) : ProjectBackupRepository(fileSystem, projectsRepository, globalSettingsStore, clock) {
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
		val repo = TestRepo(fileSystem, projectsRepository, globalSettingsStore, clock)
		val backupDef = repo.testCreateNewProjectBackupDef(projectDef)

		val filename = backupDef.path.name
		assertEquals("Test Project-2025-12-28T162900Z.zip", filename)
	}

	@Test
	fun `test parsing backup def`() {
		val modified = Instant.parse("2025-12-28T16:29:00Z")
		val path = "/projects/.backups/Test_Project-2025-12-28T162900Z.zip".toPath()
		writeBackupFileNamed(path, modified)

		val projectDef = ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath())
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns "/projects/Test Project".toPath()
			.toHPath()

		val repo = TestRepo(fileSystem, projectsRepository, globalSettingsStore, clock)
		val backups = repo.getBackups(projectDef)

		assertEquals(1, backups.size)
		val backupDef = backups[0]
		assertEquals("Test Project", backupDef.projectDef.name)
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

		val backups = realRepo().getBackups(ProjectDef(name, "/projects/$name".toPath().toHPath()))

		assertEquals(1, backups.size)
		assertEquals(name, backups.first().projectDef.name)
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

		val projectDef = ProjectDef(name, projectDir.toHPath())
		val backupDef = realRepo().createBackup(projectDef)

		assertNotNull(backupDef)
		val filename = backupDef.path.name
		assertFalse(filename.contains(':'), "Backup filename must not contain OS-forbidden chars: $filename")
		assertEquals(1, realRepo().getBackups(projectDef).size)
	}

	@Test
	fun `getBackups recognizes project names with punctuation`() {
		val name = "Tom's Story-Notes"
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory(name) } returns
			"/projects/$name".toPath().toHPath()

		writeBackupFile(name, Instant.parse("2026-06-20T08:00:00Z"))

		val backups = realRepo().getBackups(ProjectDef(name, "/projects/$name".toPath().toHPath()))

		assertEquals(1, backups.size)
		assertEquals(name, backups.first().projectDef.name)
	}

	private fun realRepo() =
		ProjectBackupRepository(fileSystem, projectsRepository, globalSettingsStore, clock)

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
	fun `getBackupsForProject returns only the requested project's backups`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Other Project") } returns
			"/projects/Other Project".toPath().toHPath()

		writeBackupFile("Test Project", Instant.parse("2025-12-28T16:29:00Z"))
		writeBackupFile("Other Project", Instant.parse("2025-12-28T17:00:00Z"))

		val target = ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath())
		val backups = realRepo().getBackupsForProject(target)

		assertEquals(1, backups.size)
		assertEquals("Test Project", backups.first().projectDef.name)
	}

	@Test
	fun `deleteBackup removes an existing backup file`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()
		every { projectsRepository.getProjectDirectory("Test Project") } returns
			"/projects/Test Project".toPath().toHPath()

		val path = writeBackupFile("Test Project", Instant.parse("2025-12-28T16:29:00Z"))
		val backups = realRepo().getBackups(ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath()))

		realRepo().deleteBackup(backups.first())

		assertFalse(fileSystem.exists(path))
	}

	@Test
	fun `deleteBackup is a no-op when the file is already gone`() {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		val missing = ProjectBackupDef(
			path = "/projects/.backups/Test_Project-2025-12-28T162900Z.zip".toPath().toHPath(),
			projectDef = ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath()),
			date = Instant.parse("2025-12-28T16:29:00Z"),
		)

		realRepo().deleteBackup(missing)
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
			projectDef = ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath()),
			date = Instant.parse("2025-12-28T16:29:00Z"),
		)

		assertFailsWith<IOException> { realRepo().deleteBackup(backup) }
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

		realRepo().cullBackups(ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath()))

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

		val projectDef = ProjectDef("Test Project", projectDir.toHPath())
		val repo = realRepo()

		// Simulate several syncs, each creating a backup a minute apart.
		val base = Instant.parse("2026-06-20T09:00:00Z")
		val created = mutableListOf<ProjectBackupDef>()
		for (i in 0 until 6) {
			val now = base.plus(i.minutes)
			fsClock.instant = now
			every { clock.now() } returns now
			repo.createBackup(projectDef)?.let { created.add(it) }
		}

		val remaining = repo.getBackups(projectDef).map { it.path.name }.toSet()
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

		realRepo().cullBackups(ProjectDef("Alice In Wonderland", "/projects/Alice In Wonderland".toPath().toHPath()))

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

		realRepo().cullBackups(ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath()))

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

		val projectDef = ProjectDef("Test Project", projectDir.toHPath())
		val backupDef = realRepo().createBackup(projectDef)

		assertNotNull(backupDef)
		assertTrue(fileSystem.exists(backupDef.path.toOkioPath()))
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

		val repo = realRepo()
		val backupDef = repo.createBackup(ProjectDef("Test Project", projectDir.toHPath()))
		assertNotNull(backupDef)

		val target = "/restored".toPath()
		val restored = repo.restoreBackup(backupDef, target.toHPath())

		assertTrue(restored)
		assertTrue(fileSystem.exists(target))
	}

	@Test
	fun `restoreBackup returns false when the backup file is missing`() = runTest {
		every { projectsRepository.getProjectsDirectory() } returns "/projects".toPath().toHPath()

		val missing = ProjectBackupDef(
			path = "/projects/.backups/Nope-2025-12-28T162900Z.zip".toPath().toHPath(),
			projectDef = ProjectDef("Nope", "/projects/Nope".toPath().toHPath()),
			date = Instant.parse("2025-12-28T16:29:00Z"),
		)

		assertFalse(realRepo().restoreBackup(missing, "/restored".toPath().toHPath()))
	}
}
