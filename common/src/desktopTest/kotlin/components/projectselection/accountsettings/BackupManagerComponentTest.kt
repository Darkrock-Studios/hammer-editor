package components.projectselection.accountsettings

import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.BackupManagerComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.BackupManagerService
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.ComponentTest
import utils.TestStrRes
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BackupManagerComponentTest : ComponentTest() {

	private lateinit var backupRepository: ProjectBackupRepository
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var backupManagerService: BackupManagerService

	private val projAlpha = ProjectDef("Alpha", HPath("/projects/Alpha", "Alpha", false))
	private val projZulu = ProjectDef("Zulu", HPath("/projects/Zulu", "Zulu", false))
	private val projNoBackups = ProjectDef("NoBackups", HPath("/projects/NoBackups", "NoBackups", false))

	private val alphaOld = backup(projAlpha, "alpha-old.zip", at = 100)
	private val alphaNew = backup(projAlpha, "alpha-new.zip", at = 200)
	private val zuluBackup = backup(projZulu, "zulu.zip", at = 150)

	private var alphaBackups = listOf(alphaOld, alphaNew)

	private fun backup(projectDef: ProjectDef, name: String, at: Long) = ProjectBackupDef(
		path = HPath("/backups/$name", name, false),
		projectDef = projectDef,
		date = Instant.fromEpochSeconds(at),
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		alphaBackups = listOf(alphaOld, alphaNew)

		backupRepository = mockk(relaxed = true)
		projectsRepository = mockk(relaxed = true)
		backupManagerService = mockk(relaxed = true)

		every { projectsRepository.getProjects() } returns listOf(projAlpha, projZulu, projNoBackups)
		every { backupRepository.getBackups(projAlpha) } answers { alphaBackups }
		every { backupRepository.getBackups(projZulu) } returns listOf(zuluBackup)
		every { backupRepository.getBackups(projNoBackups) } returns emptyList()

		setupKoin(module {
			single { backupRepository }
			single { projectsRepository }
			single { backupManagerService }
			single<StrRes> { TestStrRes() }
		})
	}

	private fun newComponent() = BackupManagerComponent(componentContext = context)

	@Test
	fun `projects with backups load on creation with the first selected`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val state = comp.state.value
		assertEquals(listOf("Alpha", "Zulu"), state.availableProjects)
		assertEquals("Alpha", state.selectedProject)
		assertEquals(projAlpha, state.selectedProjectDef)
		assertFalse(state.isLoading)
		assertNull(state.error)
	}

	@Test
	fun `backups for the selected project are listed newest first`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf(alphaNew, alphaOld), comp.state.value.backupsForSelectedProject)
	}

	@Test
	fun `a failure while loading projects surfaces an error`() = runTest(mainTestDispatcher) {
		every { projectsRepository.getProjects() } throws RuntimeException("disk exploded")

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val state = comp.state.value
		assertNotNull(state.error)
		assertFalse(state.isLoading)
	}

	@Test
	fun `selectProject switches the selection and loads that project's backups`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.selectProject("Zulu")
			advanceUntilIdle()

			val state = comp.state.value
			assertEquals("Zulu", state.selectedProject)
			assertEquals(projZulu, state.selectedProjectDef)
			assertEquals(listOf(zuluBackup), state.backupsForSelectedProject)
		}

	@Test
	fun `deleteBackup removes the backup and reloads the list`() = runTest(mainTestDispatcher) {
		every { backupRepository.deleteBackup(alphaOld) } answers { alphaBackups = listOf(alphaNew) }

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.deleteBackup(alphaOld)
		advanceUntilIdle()

		verify(exactly = 1) { backupRepository.deleteBackup(alphaOld) }
		assertEquals(listOf(alphaNew), comp.state.value.backupsForSelectedProject)
	}

	@Test
	fun `a failed delete surfaces an error and keeps the list`() = runTest(mainTestDispatcher) {
		every { backupRepository.deleteBackup(any()) } throws RuntimeException("locked")

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.deleteBackup(alphaOld)
		advanceUntilIdle()

		assertNotNull(comp.state.value.error)
		assertEquals(listOf(alphaNew, alphaOld), comp.state.value.backupsForSelectedProject)
	}

	@Test
	fun `restoreBackup restores into the project's directory`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.restoreBackup(alphaNew)
		advanceUntilIdle()

		coVerify(exactly = 1) { backupRepository.restoreBackup(alphaNew, projAlpha.path) }
	}

	@Test
	fun `exportBackup delegates to the backup manager service`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.exportBackup(alphaNew)
		advanceUntilIdle()

		verify(exactly = 1) { backupManagerService.exportBackup(alphaNew) }
	}
}
