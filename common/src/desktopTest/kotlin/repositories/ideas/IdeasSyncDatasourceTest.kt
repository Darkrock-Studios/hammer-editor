package repositories.ideas

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSynchronizationData
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdeasSyncDatasourceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var datasource: IdeasSyncDatasource

	private val ideaId = IdeaId("0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c")

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		setupKoin()

		val ideasDatasource = IdeasDatasource(ffs, StoryIdeaCodec(createTomlSerializer()), globalSettingsStore)
		datasource = IdeasSyncDatasource(ffs, createJsonSerializer(), ideasDatasource)
	}

	@Test
	fun `Load without a sidecar returns empty bookkeeping and hasSynced is false`() = runTest {
		assertFalse(datasource.hasSynced())
		assertEquals(IdeasSynchronizationData(), datasource.load())
	}

	@Test
	fun `Save creates the sidecar and marks the install as synced`() = runTest {
		datasource.save(IdeasSynchronizationData(baselines = mapOf(ideaId to "hash-1")))

		assertTrue(datasource.hasSynced())
		assertEquals(mapOf(ideaId to "hash-1"), datasource.load().baselines)
	}

	@Test
	fun `Corrupt sidecar recovers as empty bookkeeping`() = runTest {
		val path = datasource.getSyncDataPath().toOkioPath()
		ffs.write(path) { writeUtf8("{ not json !!!") }

		assertEquals(IdeasSynchronizationData(), datasource.load())
	}

	@Test
	fun `Pending delete is only recorded once a sync has happened`() = runTest {
		datasource.recordPendingDelete(ideaId)
		assertFalse(datasource.hasSynced())

		datasource.save(IdeasSynchronizationData(baselines = mapOf(ideaId to "hash-1")))
		datasource.recordPendingDelete(ideaId)

		val data = datasource.load()
		assertEquals(setOf(ideaId), data.pendingDeletes)
		assertTrue(data.baselines.isEmpty(), "delete must drop the idea's baseline")
	}

	@Test
	fun `Update round-trips through disk`() = runTest {
		datasource.save(IdeasSynchronizationData())

		datasource.update { it.copy(baselines = it.baselines + (ideaId to "h1")) }
		datasource.update { it.copy(pendingDeletes = it.pendingDeletes + IdeaId("other")) }

		val data = datasource.load()
		assertEquals(mapOf(ideaId to "h1"), data.baselines)
		assertEquals(setOf(IdeaId("other")), data.pendingDeletes)
	}
}
