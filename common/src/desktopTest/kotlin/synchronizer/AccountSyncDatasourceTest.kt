package synchronizer

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.GroupBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectsSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.RenamedProject
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.projectsDirectoryOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountSyncDatasourceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var datasource: AccountSyncDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		ffs = FakeFileSystem()
		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		datasource = AccountSyncDatasource(ffs, createJsonSerializer(), projectsDirectoryOf(globalSettingsStore))
	}

	@Test
	fun `A missing file loads empty and is created`() = runTest {
		val loaded = datasource.load()

		assertEquals(ProjectsSynchronizationData(), loaded)
		assertTrue(ffs.exists(datasource.getSyncDataPath().toOkioPath()))
	}

	@Test
	fun `A corrupt file recovers as empty`() = runTest {
		val path = datasource.getSyncDataPath().toOkioPath()
		ffs.createDirectories(path.parent!!)
		ffs.write(path) { writeUtf8("{ not json") }

		assertEquals(ProjectsSynchronizationData(), datasource.load())
	}

	@Test
	fun `A file written before baselines existed keeps its queues`() = runTest {
		val path = datasource.getSyncDataPath().toOkioPath()
		ffs.createDirectories(path.parent!!)
		ffs.write(path) {
			writeUtf8(
				"""{"deletedProjects":["a"],"projectsToDelete":["b"],""" +
					""""projectsToRename":[{"projectId":"c","newName":"New"}]}"""
			)
		}

		val loaded = datasource.load()

		assertEquals(setOf(ProjectId("a")), loaded.deletedProjects)
		assertEquals(setOf(ProjectId("b")), loaded.projectsToDelete)
		assertEquals(setOf(RenamedProject(ProjectId("c"), "New")), loaded.projectsToRename)
		assertTrue(loaded.groupBaselines.isEmpty())
	}

	@Test
	fun `Baselines round trip`() = runTest {
		val data = ProjectsSynchronizationData(
			groupBaselines = mapOf(GroupId("g") to GroupBaseline("Trilogy", GroupId("p"), "Three")),
			projectBaselines = mapOf(ProjectId("x") to ProjectBaseline("Book", GroupId("g"))),
		)

		datasource.save(data)

		assertEquals(data, datasource.load())
	}

	@Test
	fun `Delete removes the file`() = runTest {
		datasource.save(ProjectsSynchronizationData())

		datasource.delete()

		assertFalse(ffs.exists(datasource.getSyncDataPath().toOkioPath()))
	}
}
