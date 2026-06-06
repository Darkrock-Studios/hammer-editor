package repositories.syncdata

import PROJECT_1_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.readJson
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource.Companion.SYNC_FILE_NAME
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import createProject
import getProject1Def
import getProjectDef
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncJournalTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var json: Json
	lateinit var datasource: SyncDataDatasource

	@MockK
	lateinit var idAllocator: IdAllocator

	@MockK
	lateinit var entitySynchronizers: EntitySynchronizers

	@MockK
	lateinit var globalSettingsStore: GlobalSettingsStore

	@MockK
	lateinit var networkConnectivity: NetworkConnectivity

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		json = createJsonSerializer()
		MockKAnnotations.init(this)
	}

	private fun createRepository(projectDef: ProjectDef): SyncJournal {
		datasource = SyncDataDatasource(
			projectDef = projectDef,
			fileSystem = ffs,
			json = json,
			idAllocator = idAllocator,
			entitySynchronizers = entitySynchronizers,
		)
		return SyncJournal(
			globalSettingsStore = globalSettingsStore,
			networkConnectivity = networkConnectivity,
			datasource = datasource,
		)
	}

	@Test
	fun `SyncJournal Init`() {
		val repo = createRepository(getProject1Def())
	}

	@Test
	fun `Check if server is synchronized`() {
		every { globalSettingsStore.serverSettings } returns mockk()

		val repo = createRepository(getProject1Def())
		assertTrue(repo.isServerSynchronized())
	}

	@Test
	fun `Check that server is not synchronized`() {
		every { globalSettingsStore.serverSettings } returns null

		val repo = createRepository(getProject1Def())
		assertFalse(repo.isServerSynchronized())
	}

	@Test
	fun `Create new sync data with missing IDs`() = runTest {
		createProject(ffs, PROJECT_1_NAME)
		val projectDef = getProject1Def()

		every { globalSettingsStore.serverSettings } returns null
		every { idAllocator.peekLastId() } returns 4
		val idSlot = slot<Int>()
		coEvery { entitySynchronizers.findEntityType(capture(idSlot)) } answers {
			when (idSlot.captured) {
				1 -> EntityType.Note
				3 -> EntityType.Scene
				else -> null
			}
		}

		val repo = createRepository(projectDef)
		val path = syncPath(projectDef)

		assertFalse(ffs.exists(path))

		assertTrue(repo.createSyncData())

		assertTrue(ffs.exists(path))

		val loadedData = ffs.readJson<ProjectSynchronizationData>(path, json)
		assertEquals(
			ProjectSynchronizationData(
				currentSyncId = null,
				lastId = 4,
				newIds = emptyList(),
				lastSync = Instant.DISTANT_PAST,
				dirty = emptyList(),
				deletedIds = setOf(2, 4)
			),
			loadedData
		)
	}

	@Test
	fun `Create new sync data but it already exists`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)

		every { globalSettingsStore.serverSettings } returns null
		every { idAllocator.peekLastId() } returns 4
		coEvery { entitySynchronizers.findEntityType(any()) } returns null

		val repo = createRepository(projectDef)
		val path = syncPath(projectDef)
		ffs.createDirectories(path.parent!!)

		assertTrue(ffs.exists(path))

		assertFalse(repo.createSyncData())
	}

	@MethodSource("syncTestData")
	@ParameterizedTest
	fun `Check that it needs sync`(syncData: ProjectSynchronizationData, needsSync: Boolean) =
		runTest {
			createProject(ffs, PROJECT_2_NAME)
			val projectDef = getProjectDef(PROJECT_2_NAME)
			every { globalSettingsStore.serverSettings } returns mockk()

			val repo = createRepository(projectDef)
			val path = syncPath(projectDef)

			ffs.writeJson(path, json, syncData)

			assertEquals(needsSync, repo.needsSync(), syncData.toString())
		}

	@Test
	fun `Check if Entity is Dirty`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.serverSettings } returns mockk()

		val repo = createRepository(projectDef)
		val path = syncPath(projectDef)

		ffs.writeJson(
			path, json, ProjectSynchronizationData(
				currentSyncId = null,
				lastId = 4,
				newIds = emptyList(),
				lastSync = Instant.DISTANT_PAST,
				dirty = listOf(EntityOriginalState(1, "asd")),
				deletedIds = setOf(2, 4)
			)
		)

		assertTrue(repo.isEntityDirty(1))
		assertFalse(repo.isEntityDirty(2))
	}

	@Test
	fun `Check that we should auto sync`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		writeDirtyData(syncPath(projectDef))
		every { globalSettingsStore.serverIsSetup() } returns true
		every { globalSettingsStore.globalSettings.automaticSyncing } returns true
		coEvery { networkConnectivity.hasActiveConnection() } returns true

		val repo = createRepository(projectDef)

		assertTrue(repo.shouldAutoSync())
	}

	@Test
	fun `Check that we should auto sync but not dirty`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		ffs.writeJson(
			syncPath(projectDef), json, ProjectSynchronizationData(
				currentSyncId = null,
				lastId = 4,
				newIds = emptyList(),
				lastSync = Instant.DISTANT_PAST,
				dirty = emptyList(),
				deletedIds = setOf(2, 4)
			)
		)
		every { globalSettingsStore.serverIsSetup() } returns true
		every { globalSettingsStore.globalSettings.automaticSyncing } returns true
		coEvery { networkConnectivity.hasActiveConnection() } returns true

		val repo = createRepository(projectDef)

		assertFalse(repo.shouldAutoSync())
	}

	@Test
	fun `Check that we should auto sync but no internet`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		writeDirtyData(syncPath(projectDef))
		every { globalSettingsStore.serverIsSetup() } returns true
		every { globalSettingsStore.globalSettings.automaticSyncing } returns true
		coEvery { networkConnectivity.hasActiveConnection() } returns false

		val repo = createRepository(projectDef)

		assertFalse(repo.shouldAutoSync())
	}

	@Test
	fun `Check that we should auto sync but global auto sync is off`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		writeDirtyData(syncPath(projectDef))
		every { globalSettingsStore.serverIsSetup() } returns true
		every { globalSettingsStore.globalSettings.automaticSyncing } returns false
		coEvery { networkConnectivity.hasActiveConnection() } returns true

		val repo = createRepository(projectDef)

		assertFalse(repo.shouldAutoSync())
	}

	@Test
	fun `Check that we should auto sync but no server setup`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		writeDirtyData(syncPath(projectDef))
		every { idAllocator.peekLastId() } returns 25
		every { globalSettingsStore.serverIsSetup() } returns false
		every { globalSettingsStore.globalSettings.automaticSyncing } returns true
		coEvery { networkConnectivity.hasActiveConnection() } returns true

		val repo = createRepository(projectDef)

		assertFalse(repo.shouldAutoSync())
	}

	private fun writeDirtyData(path: Path) {
		ffs.writeJson(
			path, json, ProjectSynchronizationData(
				currentSyncId = null,
				lastId = 4,
				newIds = emptyList(),
				lastSync = Instant.DISTANT_PAST,
				dirty = listOf(EntityOriginalState(1, "asd")),
				deletedIds = setOf(2, 4)
			)
		)
	}

	@Test
	fun `Mark Entity Dirty`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val repo = createRepository(projectDef)

		repo.markEntityAsDirty(1, "old-hash")

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)
		assertEquals(
			listOf(EntityOriginalState(1, "old-hash")),
			loadedData?.dirty
		)
	}

	@Test
	fun `Record new ID`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val newId1 = 26
		val newId2 = 27

		val repo = createRepository(projectDef)

		repo.recordNewId(newId1)
		repo.recordNewId(newId2)

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)
		assertEquals(
			listOf(newId1, newId2),
			loadedData?.newIds
		)
	}

	@Test
	fun `Record deleted ID`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val newId1 = 14
		val newId2 = 16

		val repo = createRepository(projectDef)

		repo.recordIdDeletion(newId1)
		repo.recordIdDeletion(newId2)

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)
		assertEquals(
			setOf(24, 25, newId1, newId2),
			loadedData?.deletedIds
		)
	}

	@Test
	fun `Record deleted ID prunes a brand-new entity from newIds`() = runTest {
		// A never-synced entity (in newIds) that gets deleted must be dropped from
		// newIds, not left behind as a phantom. The server never saw it, so there
		// is nothing to add to deletedIds either. Leaving it in newIds is what
		// wedged sync with "Entity X not found for reId".
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val repo = createRepository(projectDef)

		repo.saveSyncData(
			ProjectSynchronizationData(
				currentSyncId = null,
				lastId = 26,
				newIds = listOf(26),
				lastSync = Instant.DISTANT_PAST,
				dirty = emptyList(),
				deletedIds = emptySet(),
			)
		)

		repo.recordIdDeletion(26)

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)
		assertEquals(emptyList<Int>(), loadedData?.newIds)
		assertFalse(loadedData?.deletedIds?.contains(26) ?: true)
	}

	@Test
	fun `Load sync data`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val repo = createRepository(projectDef)

		val loadedData = repo.loadSyncData()

		assertEquals(
			ProjectSynchronizationData(
				currentSyncId = null,
				lastId = 25,
				newIds = emptyList(),
				lastSync = Instant.parse("2024-10-28T06:31:18.189825600Z"),
				dirty = emptyList(),
				deletedIds = setOf(24, 25)
			),
			loadedData
		)
	}

	@Test
	fun `Save sync data`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val repo = createRepository(projectDef)

		val newData = ProjectSynchronizationData(
			currentSyncId = "abc-123",
			lastId = 26,
			newIds = listOf(27),
			lastSync = Instant.parse("2024-10-30T07:31:18.189825600Z"),
			dirty = emptyList(),
			deletedIds = setOf(24, 25)
		)
		repo.saveSyncData(newData)

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)
		assertEquals(
			newData,
			loadedData
		)
	}

	@Test
	fun `Get Deleted IDs`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val repo = createRepository(projectDef)

		val deletedIds = repo.deletedIds()

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)
		assertEquals(
			loadedData?.deletedIds,
			deletedIds
		)
	}

	companion object {
		@JvmStatic
		private fun syncTestData() = listOf(
			Arguments.of(
				ProjectSynchronizationData(
					currentSyncId = null,
					lastId = 4,
					newIds = emptyList(),
					lastSync = Instant.DISTANT_PAST,
					dirty = listOf(EntityOriginalState(1, "asd")),
					deletedIds = setOf(2, 4)
				), true
			),
			Arguments.of(
				ProjectSynchronizationData(
					currentSyncId = null,
					lastId = 4,
					newIds = listOf(5),
					lastSync = Instant.DISTANT_PAST,
					dirty = listOf(EntityOriginalState(1, "asd")),
					deletedIds = emptySet()
				), true
			),
			Arguments.of(
				ProjectSynchronizationData(
					currentSyncId = null,
					lastId = 4,
					newIds = listOf(5),
					lastSync = Instant.DISTANT_PAST,
					dirty = listOf(EntityOriginalState(1, "asd")),
					deletedIds = emptySet()
				), true
			),
			Arguments.of(
				ProjectSynchronizationData(
					currentSyncId = null,
					lastId = 4,
					newIds = emptyList(),
					lastSync = Instant.DISTANT_PAST,
					dirty = emptyList(),
					deletedIds = emptySet()
				), false
			),
		)

		private fun syncPath(projectDef: ProjectDef) = projectDef.path.toOkioPath() / SYNC_FILE_NAME
	}

}