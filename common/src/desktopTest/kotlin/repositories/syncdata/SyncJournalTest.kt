package repositories.syncdata

import PROJECT_EMPTY_NAME
import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.base.http.readJson
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.id.datasources.*
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource.Companion.SYNC_FILE_NAME
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import createProject
import getProject1Def
import getProjectDef
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.ProjectSynchronizationBegan
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.CollateIdsOperation
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.operations.EntityDeleteOperation
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import okio.Buffer
import okio.ForwardingFileSystem
import okio.ForwardingSink
import okio.IOException
import okio.Path
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.koin.dsl.module
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncJournalTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var json: Json
	lateinit var datasource: SyncJournalDatasource

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
		// A missing journal is rebuilt from the entities on disk, found through the project scope
		setupKoin(module {
			single { SceneIdDatasource(ffs) }
			single { NotesIdDatasource(ffs) }
			single { EncyclopediaIdDatasource(ffs) }
			single { TimeLineEventIdDatasource(ffs, createTomlSerializer()) }
			single { SceneDraftIdDatasource(ffs) }
			single { ImageIdDatasource(ffs) }
		})
		datasource = SyncJournalDatasource(
			projectDef = projectDef,
			fileSystem = ffs,
			json = json,
		)
		return SyncJournal(
			projectDef = projectDef,
			globalSettingsStore = globalSettingsStore,
			networkConnectivity = networkConnectivity,
			datasource = datasource,
		)
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

	private fun writeNote(projectDef: ProjectDef, id: Int) {
		val path = NotesDatasource.getNotePath(id, projectDef, ffs).toOkioPath()
		ffs.createDirectories(path.parent!!)
		ffs.write(path) { writeUtf8("") }
	}

	@Test
	fun `Create new sync data infers no deletions from missing IDs`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projectDef = getProjectDef(PROJECT_EMPTY_NAME)
		writeNote(projectDef, 1)
		writeNote(projectDef, 3)

		every { globalSettingsStore.serverSettings } returns null

		val repo = createRepository(projectDef)
		val path = syncPath(projectDef)

		assertFalse(ffs.exists(path))

		assertTrue(repo.createSyncData())

		assertTrue(ffs.exists(path))

		val loadedData = ffs.readJson<ProjectSynchronizationData>(path, json)
		assertEquals(
			ProjectSynchronizationData(
				currentSyncId = null,
				lastId = 3,
				newIds = emptyList(),
				lastSync = Instant.DISTANT_PAST,
				dirty = emptyList(),
				deletedIds = emptySet(),
				verifyAllEntities = true,
			),
			loadedData
		)
	}

	// Test Project 2 holds IDs 1 to 23 with no gaps: scenes and scene groups, drafts, notes,
	// timeline events and encyclopedia entries.
	@Test
	fun `Rebuilding finds every entity type`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		ffs.delete(syncPath(projectDef))

		every { globalSettingsStore.serverSettings } returns null

		val repo = createRepository(projectDef)
		assertTrue(repo.createSyncData())

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
		assertEquals(23, loadedData.lastId)
		assertEquals(emptySet(), loadedData.deletedIds)
	}

	@Test
	fun `Rebuilding counts archived scenes`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		ffs.delete(syncPath(projectDef))
		ffs.delete(NotesDatasource.getNotePath(13, projectDef, ffs).toOkioPath())
		val archivedDir = SceneDatasource.getArchivedDirectory(projectDef, ffs).toOkioPath()
		ffs.write(archivedDir / "Archived Scene-25.md") { writeUtf8("") }

		every { globalSettingsStore.serverSettings } returns null

		val repo = createRepository(projectDef)
		assertTrue(repo.createSyncData())

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
		assertEquals(25, loadedData.lastId)
		assertEquals(emptySet(), loadedData.deletedIds)
	}

	@Test
	fun `A corrupt journal is rebuilt on load`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		ffs.write(syncPath(projectDef)) { writeUtf8("{ not json") }

		val repo = createRepository(projectDef)
		val rebuilt = repo.loadSyncData()

		assertEquals(23, rebuilt.lastId)
		assertEquals(emptySet(), rebuilt.deletedIds)
		assertEquals(rebuilt, ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json))
	}

	@Test
	fun `A missing journal is still rebuilt after the project scope has closed`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		ffs.delete(syncPath(projectDef))

		val repo = createRepository(projectDef)
		val projectScope = ProjectDefScope(projectDef)
		projectScope.scope.close()

		assertEquals(23, repo.loadSyncData().lastId)
		assertNull(
			projectScope.getKoin().getScopeOrNull(projectScope.scopeId),
			"The rebuild must not bring the closed scope back",
		)
	}

	@Test
	fun `An unreadable timeline infers no deleted IDs`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projectDef = getProjectDef(PROJECT_EMPTY_NAME)
		writeNote(projectDef, 1)
		writeNote(projectDef, 3)
		// ID 2 could be a timeline event that can no longer be read
		val timelinePath = TimeLineDatasource.getTimelineFilePath(projectDef).toOkioPath()
		ffs.createDirectories(timelinePath.parent!!)
		ffs.write(timelinePath) { writeUtf8("not = [valid toml") }

		every { globalSettingsStore.serverSettings } returns null

		val repo = createRepository(projectDef)
		assertTrue(repo.createSyncData())

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
		assertEquals(3, loadedData.lastId)
		assertEquals(emptySet(), loadedData.deletedIds)
	}

	@Test
	fun `Deleting the newest entity with no journal still records its ID`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projectDef = getProjectDef(PROJECT_EMPTY_NAME)
		writeNote(projectDef, 1)
		writeNote(projectDef, 3)

		every { globalSettingsStore.isServerSynchronized() } returns true

		val repo = createRepository(projectDef)
		// Entity 5 was the newest and is already gone from disk
		repo.recordIdDeletion(5)

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
		assertEquals(5, loadedData.lastId)
		assertEquals(setOf(5), loadedData.deletedIds)
	}

	@Test
	fun `Claiming an ID with no journal counts every earlier ID as used`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projectDef = getProjectDef(PROJECT_EMPTY_NAME)
		writeNote(projectDef, 1)
		writeNote(projectDef, 3)

		every { globalSettingsStore.isServerSynchronized() } returns true

		val repo = createRepository(projectDef)
		// IDs 4 and 5 were claimed and deleted before the journal existed
		repo.recordNewId(6)

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
		assertEquals(5, loadedData.lastId)
		assertEquals(listOf(6), loadedData.newIds)
		assertEquals(emptySet(), loadedData.deletedIds)
	}

	@Test
	fun `A corrupt journal on a partial copy sends no deletions to the server`() = runTest {
		createProject(ffs, PROJECT_EMPTY_NAME)
		val projectDef = getProjectDef(PROJECT_EMPTY_NAME)
		// Downloaded 1 and 3 of the owner's 1 to 5 before the journal was cut short
		writeNote(projectDef, 1)
		writeNote(projectDef, 3)
		ffs.write(syncPath(projectDef)) { writeUtf8("{\"lastId\":5,\"newI") }

		val repo = createRepository(projectDef)
		val clientSyncData = repo.loadSyncData()
		val server = ProjectSynchronizationBegan(
			syncId = "sync-id",
			lastSync = Instant.fromEpochSeconds(1234567),
			lastId = 5,
			idSequence = listOf(1, 2, 3, 4, 5),
			deletedIds = emptySet(),
		)
		val collated = CollateIdsOperation(projectDef, TestStrRes(), datasource).execute(
			state = FetchServerDataState(
				onlyNew = false,
				clientSyncData = clientSyncData,
				entityState = ClientEntityState(emptySet()),
				serverSyncData = server,
			),
			onProgress = { _, _ -> },
			onLog = {},
			onConflict = mockk(relaxed = true),
			onComplete = {},
		)
		assertTrue(isSuccess(collated))
		val collatedIds = assertIs<CollateIdsState>(collated.data).collatedIds
		val serverProjectApi = mockk<ServerProjectApi>(relaxed = true)
		EntityDeleteOperation(projectDef, serverProjectApi, TestStrRes()).execute(
			state = IdConflictResolutionState(
				onlyNew = false,
				clientSyncData = clientSyncData,
				entityState = ClientEntityState(emptySet()),
				serverSyncData = server,
				collatedIds = collatedIds,
				maxId = 5,
				newClientIds = emptyList(),
			),
			onProgress = { _, _ -> },
			onLog = {},
			onConflict = mockk(relaxed = true),
			onComplete = {},
		)

		assertEquals(emptySet(), collatedIds.newlyDeletedIds)
		coVerify(exactly = 0) { serverProjectApi.deleteId(any(), any(), any()) }
	}

	@Test
	fun `A journal write cut short leaves the previous journal intact`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		val before = ffs.read(syncPath(projectDef)) { readUtf8() }
		val failingWrites = object : ForwardingFileSystem(ffs) {
			override fun sink(file: Path, mustCreate: Boolean): Sink =
				object : ForwardingSink(super.sink(file, mustCreate)) {
					override fun write(source: Buffer, byteCount: Long) {
						super.write(source, byteCount / 2)
						throw IOException("disk full")
					}
				}
		}
		val failing = SyncJournalDatasource(projectDef, failingWrites, json)
		val changed = assertNotNull(failing.loadSyncDataOrNull()).copy(lastId = 99)

		assertFailsWith<IOException> { failing.saveSyncData(changed) }

		assertEquals(before, ffs.read(syncPath(projectDef)) { readUtf8() })
		assertEquals(listOf(SYNC_FILE_NAME), ffs.list(projectDef.path.toOkioPath()).map { it.name }.filter { "sync" in it })
	}

	@Test
	fun `Create new sync data but it already exists`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)

		every { globalSettingsStore.serverSettings } returns null

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
	fun `Mark Entity Dirty uses the server-confirmed hash as the baseline`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val repo = createRepository(projectDef)

		// The baseline is the hash the server last confirmed, not a value re-derived now.
		repo.recordSyncedHash(1, "confirmed-hash")
		repo.markEntityAsDirty(1)

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
		assertEquals(
			listOf(EntityOriginalState(1, "confirmed-hash")),
			loadedData?.dirty
		)
	}

	@Test
	fun `Mark Entity Dirty with no synced hash records a null baseline`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val projectDef = getProjectDef(PROJECT_2_NAME)
		every { globalSettingsStore.isServerSynchronized() } returns true
		val repo = createRepository(projectDef)

		// No prior sync for this entity: the server never confirmed a hash, so the baseline is
		// null and the server will accept the upload without a conflict check.
		repo.markEntityAsDirty(1)

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
		assertEquals(
			listOf(EntityOriginalState(1, null)),
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

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
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

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
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

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
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

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
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

		val loadedData = ffs.readJson<ProjectSynchronizationData>(syncPath(projectDef), json)!!
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
					dirty = emptyList(),
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
			Arguments.of(
				ProjectSynchronizationData(
					currentSyncId = null,
					lastId = 4,
					newIds = emptyList(),
					lastSync = Instant.DISTANT_PAST,
					dirty = emptyList(),
					deletedIds = emptySet(),
					verifyAllEntities = true,
				), true
			),
		)

		private fun syncPath(projectDef: ProjectDef) = projectDef.path.toOkioPath() / SYNC_FILE_NAME
	}

}