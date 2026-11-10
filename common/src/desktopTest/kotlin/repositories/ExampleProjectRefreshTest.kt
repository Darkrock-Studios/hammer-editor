package repositories

import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepositoryDesktop
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.RefreshExampleProjectUseCase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.NewUserExperience
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import testProjectId
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class ExampleProjectRefreshTest : BaseTest() {

	private val toml = createTomlSerializer()
	private val projectsDir = "/projects".toPath()
	private val exampleDir = projectsDir / ExampleProjectRepository.PROJECT_NAME
	private val existing = ProjectDef(
		id = testProjectId(ExampleProjectRepository.PROJECT_NAME),
		name = ExampleProjectRepository.PROJECT_NAME,
		path = exampleDir.toHPath(),
	)
	private val usersOwnScene = exampleDir / "scenes" / "99~Written by the user~999.md"

	private lateinit var ffs: FakeFileSystem
	private lateinit var settings: GlobalSettings
	private lateinit var backups: ProjectBackupService
	private lateinit var refreshExample: RefreshExampleProjectUseCase

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		ffs.createDirectories(exampleDir / "scenes")
		ffs.write(usersOwnScene) { writeUtf8("The user's own words.") }

		backups = mockk(relaxed = true)
		every { backups.supportsBackup() } returns true
		coEvery { backups.createBackup(any()) } returns mockk<ProjectBackupDef>()
		setupKoin(module {
			single { ProjectMetadataDatasource(ffs, toml) }
		})

		val store = mockk<GlobalSettingsStore>()
		every { store.globalSettings } answers { settings }
		coEvery { store.updateSettings(any()) } answers {
			settings = firstArg<(GlobalSettings) -> GlobalSettings>()(settings)
		}
		refreshExample = RefreshExampleProjectUseCase(ExampleProjectRepositoryDesktop(store, ffs, toml, Clock.System), backups)
	}

	private fun installed(revision: Int, created: Boolean = true) {
		settings = GlobalSettings(
			projectsDirectory = projectsDir.toString(),
			nux = NewUserExperience(exampleProjectCreated = created, exampleProjectRevision = revision),
		)
	}

	private fun refresh(existing: ProjectDef?) = scope.runTest {
		refreshExample(existing)
		testScheduler.advanceUntilIdle()
	}

	@Test
	fun `an outdated example is backed up and replaced with the bundle`() {
		installed(revision = 0)

		refresh(existing)

		coVerify(exactly = 1) { backups.createBackup(existing) }
		assertFalse(ffs.exists(usersOwnScene), "the old copy is gone")
		assertTrue(ffs.exists(exampleDir / "scenes" / "00~Title~1.md"), "the bundle is in its place")
		assertEquals(ExampleProjectRepository.BUNDLE_REVISION, settings.nux.exampleProjectRevision)
	}

	@Test
	fun `a synced example is kept and its revision recorded`() {
		installed(revision = 0)
		ffs.write(exampleDir / SyncJournalDatasource.SYNC_FILE_NAME) { writeUtf8("{}") }

		refresh(existing)

		coVerify(exactly = 0) { backups.createBackup(any()) }
		assertTrue(ffs.exists(usersOwnScene))
		assertEquals(ExampleProjectRepository.BUNDLE_REVISION, settings.nux.exampleProjectRevision)
	}

	@Test
	fun `a deleted example stays deleted`() {
		installed(revision = 0)
		ffs.deleteRecursively(exampleDir)

		refresh(existing = null)

		assertFalse(ffs.exists(exampleDir))
		assertEquals(ExampleProjectRepository.BUNDLE_REVISION, settings.nux.exampleProjectRevision)
	}

	@Test
	fun `an example from the current bundle is left alone`() {
		installed(revision = ExampleProjectRepository.BUNDLE_REVISION)

		refresh(existing)

		coVerify(exactly = 0) { backups.createBackup(any()) }
		assertTrue(ffs.exists(usersOwnScene))
	}

	@Test
	fun `a failed backup leaves the example and retries next start`() {
		installed(revision = 0)
		coEvery { backups.createBackup(any()) } returns null

		refresh(existing)

		assertTrue(ffs.exists(usersOwnScene))
		assertEquals(0, settings.nux.exampleProjectRevision)
	}

	@Test
	fun `nothing happens before the example was ever installed`() {
		installed(revision = 0, created = false)

		refresh(existing)

		assertTrue(ffs.exists(usersOwnScene))
		assertFalse(settings.nux.exampleProjectCreated)
	}
}
