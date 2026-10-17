package components.projectselection.accountsettings

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.backup_manager_error_restore_backup
import com.darkrockstudios.apps.hammer.backup_manager_error_restore_in_use
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.BackupManagerComponent
import com.darkrockstudios.apps.hammer.common.data.projectbackup.BackupManagerService
import com.darkrockstudios.apps.hammer.common.data.projectbackup.BackupRestoreResult
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupDef
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupService
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.coEvery
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
import org.koin.dsl.module
import server.RecordingStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BackupManagerComponentTest : ComponentTest() {

	private lateinit var backupService: ProjectBackupService
	private lateinit var backupManagerService: BackupManagerService
	private lateinit var strRes: RecordingStrRes

	private val alphaOld = backup("Alpha", "alpha-old.zip", at = 100)
	private val alphaNew = backup("Alpha", "alpha-new.zip", at = 200)
	private val zuluBackup = backup("Zulu", "zulu.zip", at = 150)

	private var alphaBackups = listOf(alphaOld, alphaNew)

	private fun backup(projectName: String, name: String, at: Long) = ProjectBackupDef(
		path = HPath("/backups/$name", name, false),
		projectName = projectName,
		date = Instant.fromEpochSeconds(at),
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		alphaBackups = listOf(alphaOld, alphaNew)

		backupService = mockk(relaxed = true)
		backupManagerService = mockk(relaxed = true)
		strRes = RecordingStrRes()

		every { backupService.getBackedUpProjects() } returns listOf("Alpha", "Zulu")
		every { backupService.getBackups("Alpha") } answers { alphaBackups }
		every { backupService.getBackups("Zulu") } returns listOf(zuluBackup)
		coEvery { backupService.restoreBackup(any()) } returns BackupRestoreResult.Restored

		setupKoin(module {
			single { backupService }
			single { backupManagerService }
			single<StrRes> { strRes }
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
		every { backupService.getBackedUpProjects() } throws RuntimeException("disk exploded")

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
			assertEquals(listOf(zuluBackup), state.backupsForSelectedProject)
		}

	@Test
	fun `deleteBackup removes the backup and reloads the list`() = runTest(mainTestDispatcher) {
		every { backupService.deleteBackup(alphaOld) } answers { alphaBackups = listOf(alphaNew) }

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.deleteBackup(alphaOld)
		advanceUntilIdle()

		verify(exactly = 1) { backupService.deleteBackup(alphaOld) }
		assertEquals(listOf(alphaNew), comp.state.value.backupsForSelectedProject)
	}

	@Test
	fun `a failed delete surfaces an error and keeps the list`() = runTest(mainTestDispatcher) {
		every { backupService.deleteBackup(any()) } throws RuntimeException("locked")

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.deleteBackup(alphaOld)
		advanceUntilIdle()

		assertNotNull(comp.state.value.error)
		assertEquals(listOf(alphaNew, alphaOld), comp.state.value.backupsForSelectedProject)
	}

	@Test
	fun `restoreBackup restores the backup over its project`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.restoreBackup(alphaNew)
		advanceUntilIdle()

		coVerify(exactly = 1) { backupService.restoreBackup(alphaNew) }
		assertNull(comp.state.value.error)
	}

	@Test
	fun `a failed restore surfaces an error`() = runTest(mainTestDispatcher) {
		coEvery { backupService.restoreBackup(any()) } returns BackupRestoreResult.Failed

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.restoreBackup(alphaNew)
		advanceUntilIdle()

		assertNotNull(comp.state.value.error)
		assertEquals(listOf(Res.string.backup_manager_error_restore_backup), strRes.requested)
	}

	@Test
	fun `a restore refused for a project in use says to close it`() = runTest(mainTestDispatcher) {
		coEvery { backupService.restoreBackup(any()) } returns BackupRestoreResult.ProjectInUse

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.restoreBackup(alphaNew)
		advanceUntilIdle()

		assertNotNull(comp.state.value.error)
		assertEquals(listOf(Res.string.backup_manager_error_restore_in_use), strRes.requested)
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
