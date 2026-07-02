package synchronizer

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.*
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectsSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.RenamedProject
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ClientAccountSynchronizerTest {

	private lateinit var ffs: FakeFileSystem
	private lateinit var json: Json
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var serverProjectsApi: ServerProjectsApi
	private lateinit var networkConnectivity: NetworkConnectivity

	private val projectsDirPath = "/projects".toPath()
	private val syncFilePath = projectsDirPath / "sync.json"

	private fun emptySyncData() = ProjectsSynchronizationData(
		deletedProjects = emptySet(),
		projectsToDelete = emptySet(),
		projectsToRename = emptySet(),
		projectsToCreate = emptySet(),
	)

	private fun writeSyncData(data: ProjectsSynchronizationData) {
		ffs.write(syncFilePath) { writeUtf8(json.encodeToString(data)) }
	}

	private fun readSyncData(): ProjectsSynchronizationData =
		json.decodeFromString(ffs.read(syncFilePath) { readUtf8() })

	private fun emptyServerResponse(syncId: String = "sync-1") = BeginProjectsSyncResponse(
		syncId = syncId,
		projects = emptySet(),
		deletedProjects = emptySet(),
	)

	private fun projectDef(name: String) =
		ProjectDef(name = name, path = HPath("$projectsDirPath/$name", name, true))

	private fun serverSettings(userId: Long) = ServerSettings(
		ssl = false,
		url = "example.com",
		email = "test@example.com",
		userId = userId,
		bearerToken = null,
		refreshToken = null,
	)

	private fun unauthorizedException() = HttpFailureException(
		statusCode = HttpStatusCode.Unauthorized,
		error = HttpResponseError(error = "Unauthorized", displayMessage = "nope"),
	)

	private fun createSynchronizer() = ClientAccountSynchronizer(
		fileSystem = ffs,
		globalSettingsStore = globalSettingsStore,
		projectsRepository = projectsRepository,
		serverProjectsApi = serverProjectsApi,
		networkConnectivity = networkConnectivity,
		json = json,
		toml = Toml,
		strRes = TestStrRes(),
	)

	@BeforeEach
	fun setup() {
		ffs = FakeFileSystem()
		ffs.createDirectories(projectsDirPath)
		json = createJsonSerializer()

		globalSettingsStore = mockk(relaxed = true)
		projectsRepository = mockk(relaxed = true)
		serverProjectsApi = mockk()
		networkConnectivity = mockk()

		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.serverSettings } returns serverSettings(userId = 1)
		every { projectsRepository.getProjectsDirectory() } returns HPath("/projects", "projects", true)

		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.success(emptyServerResponse())
		coEvery { serverProjectsApi.endProjectsSync(any()) } returns Result.success("ok")
		coEvery { networkConnectivity.hasActiveConnection() } returns true
	}

	private fun cleanProjectSyncData(hash: String?, algoVersion: Int = ProjectContentHasher.ALGO_VERSION) =
		ProjectSynchronizationData(
			lastId = 0,
			newIds = emptyList(),
			lastSync = Instant.DISTANT_PAST,
			dirty = emptyList(),
			deletedIds = emptySet(),
			cachedProjectHash = hash,
			hashAlgoVersion = algoVersion,
		)

	private fun writeProjectSyncData(projectDef: ProjectDef, data: ProjectSynchronizationData) {
		val path = projectDef.path.toOkioPath() / "sync.json"
		path.parent?.let { ffs.createDirectories(it) }
		ffs.write(path) { writeUtf8(json.encodeToString(data)) }
	}

	private fun writeProjectData(projectDef: ProjectDef, data: StoredProjectData) {
		val path = projectDef.path.toOkioPath() / "project_data.toml"
		path.parent?.let { ffs.createDirectories(it) }
		ffs.writeToml(path, Toml, data)
	}

	@Test
	fun `probe sends a clean project's cached hash and returns the server's unchanged set`() = runTest {
		val def = projectDef("Alpha")
		val projectId = ProjectId("uuid-alpha")
		writeProjectSyncData(def, cleanProjectSyncData(hash = "alpha-hash"))

		val captured = slot<List<ProjectHashItem>>()
		coEvery { serverProjectsApi.probeProjectChanges(capture(captured)) } returns
			Result.success(ProjectsSyncProbeResponse(unchangedProjects = setOf(projectId)))

		val result = createSynchronizer()
			.probeUnchangedProjects(listOf(SyncedProjectDefinition(def, projectId)))

		assertEquals(setOf(projectId), result)
		assertEquals(listOf(ProjectHashItem(projectId, "alpha-hash")), captured.captured)
	}

	@Test
	fun `probe skips a project with pending journal work and never calls the server`() = runTest {
		val def = projectDef("Beta")
		val projectId = ProjectId("uuid-beta")
		writeProjectSyncData(
			def,
			cleanProjectSyncData(hash = "beta-hash").copy(dirty = listOf(EntityOriginalState(1, "old"))),
		)

		val result = createSynchronizer()
			.probeUnchangedProjects(listOf(SyncedProjectDefinition(def, projectId)))

		assertTrue(result.isEmpty())
		coVerify(exactly = 0) { serverProjectsApi.probeProjectChanges(any()) }
	}

	@Test
	fun `probe ignores a cache written under an older algorithm version`() = runTest {
		val def = projectDef("Gamma")
		val projectId = ProjectId("uuid-gamma")
		writeProjectSyncData(
			def,
			cleanProjectSyncData(hash = "gamma-hash", algoVersion = ProjectContentHasher.ALGO_VERSION - 1),
		)

		val result = createSynchronizer()
			.probeUnchangedProjects(listOf(SyncedProjectDefinition(def, projectId)))

		assertTrue(result.isEmpty())
		coVerify(exactly = 0) { serverProjectsApi.probeProjectChanges(any()) }
	}

	@Test
	fun `probe ignores a project with no cached hash`() = runTest {
		val def = projectDef("Delta")
		val projectId = ProjectId("uuid-delta")
		writeProjectSyncData(def, cleanProjectSyncData(hash = null))

		val result = createSynchronizer()
			.probeUnchangedProjects(listOf(SyncedProjectDefinition(def, projectId)))

		assertTrue(result.isEmpty())
		coVerify(exactly = 0) { serverProjectsApi.probeProjectChanges(any()) }
	}

	@Test
	fun `probe failure falls back to syncing everything`() = runTest {
		val def = projectDef("Epsilon")
		val projectId = ProjectId("uuid-epsilon")
		writeProjectSyncData(def, cleanProjectSyncData(hash = "epsilon-hash"))

		coEvery { serverProjectsApi.probeProjectChanges(any()) } returns
			Result.failure(unauthorizedException())

		val result = createSynchronizer()
			.probeUnchangedProjects(listOf(SyncedProjectDefinition(def, projectId)))

		assertTrue(result.isEmpty())
	}

	@Test
	fun `probe skips a project whose project-data changed but entity journal is clean`() = runTest {
		val def = projectDef("Zeta")
		val projectId = ProjectId("uuid-zeta")
		// Clean entity journal + a cached hash, but project-data diverges from its last-synced hash.
		writeProjectSyncData(def, cleanProjectSyncData(hash = "zeta-hash"))
		writeProjectData(
			def,
			StoredProjectData(
				data = ProjectData(authorName = "Edited"),
				lastSyncedHash = ProjectDataHasher.hash(ProjectData()),
			),
		)

		val result = createSynchronizer()
			.probeUnchangedProjects(listOf(SyncedProjectDefinition(def, projectId)))

		assertTrue(result.isEmpty(), "a project-data-only edit must not be probe-skipped")
		coVerify(exactly = 0) { serverProjectsApi.probeProjectChanges(any()) }
	}

	@Test
	fun `probe treats an unreadable journal as ineligible without throwing`() = runTest {
		val def = projectDef("Eta")
		val projectId = ProjectId("uuid-eta")
		val path = def.path.toOkioPath() / "sync.json"
		path.parent?.let { ffs.createDirectories(it) }
		ffs.write(path) { writeUtf8("}{ not valid json") }

		val result = createSynchronizer()
			.probeUnchangedProjects(listOf(SyncedProjectDefinition(def, projectId)))

		assertTrue(result.isEmpty())
		coVerify(exactly = 0) { serverProjectsApi.probeProjectChanges(any()) }
	}

	@Test
	fun `isServerSynchronized true when a user id is present`() {
		every { globalSettingsStore.serverSettings } returns serverSettings(userId = 5)
		assertTrue(createSynchronizer().isServerSynchronized())
	}

	@Test
	fun `isServerSynchronized false when there are no server settings`() {
		every { globalSettingsStore.serverSettings } returns null
		assertFalse(createSynchronizer().isServerSynchronized())
	}

	@Test
	fun `shouldAutoSync true when enabled and connected`() = runTest {
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", automaticSyncing = true)
		coEvery { networkConnectivity.hasActiveConnection() } returns true
		assertTrue(createSynchronizer().shouldAutoSync())
	}

	@Test
	fun `shouldAutoSync false when automatic syncing disabled`() = runTest {
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", automaticSyncing = false)
		assertFalse(createSynchronizer().shouldAutoSync())
	}

	@Test
	fun `shouldAutoSync false when there is no active connection`() = runTest {
		every { globalSettingsStore.globalSettings } returns
			GlobalSettings(projectsDirectory = "/projects", automaticSyncing = true)
		coEvery { networkConnectivity.hasActiveConnection() } returns false
		assertFalse(createSynchronizer().shouldAutoSync())
	}

	@Test
	fun `createProject queues a project name to be created`() {
		writeSyncData(emptySyncData())

		createSynchronizer().createProject("MyNovel")

		assertEquals(setOf("MyNovel"), readSyncData().projectsToCreate)
	}

	@Test
	fun `renameProject records a pending rename`() {
		writeSyncData(emptySyncData())
		val id = ProjectId.randomUUID()

		createSynchronizer().renameProject(id, "NewName")

		assertEquals(setOf(RenamedProject(id, "NewName")), readSyncData().projectsToRename)
	}

	@Test
	fun `renameProject replaces an earlier rename of the same project`() {
		writeSyncData(emptySyncData())
		val id = ProjectId.randomUUID()
		val sync = createSynchronizer()

		sync.renameProject(id, "FirstName")
		sync.renameProject(id, "SecondName")

		assertEquals(setOf(RenamedProject(id, "SecondName")), readSyncData().projectsToRename)
	}

	@Test
	fun `deleteProject queues deletion and clears pending create and rename`() {
		val id = ProjectId.randomUUID()
		val def = projectDef("MyNovel")
		writeSyncData(
			emptySyncData().copy(
				projectsToCreate = setOf("MyNovel"),
				projectsToRename = setOf(RenamedProject(id, "Renamed")),
			)
		)

		createSynchronizer().deleteProject(SyncedProjectDefinition(def, id))

		val result = readSyncData()
		assertEquals(setOf(id), result.projectsToDelete)
		assertFalse(result.projectsToCreate.contains("MyNovel"))
		assertTrue(result.projectsToRename.isEmpty())
	}

	@Test
	fun `loadSyncData drops the UUID-migration scrub of valid ids`() {
		val validId = ProjectId.randomUUID()
		writeSyncData(
			emptySyncData().copy(
				projectsToDelete = setOf(validId),
				deletedProjects = setOf(validId),
			)
		)

		// Any mutation forces a load+save cycle, which runs the migration scrub.
		createSynchronizer().createProject("AnyProject")

		val result = readSyncData()
		assertEquals(setOf(validId), result.projectsToDelete)
		assertEquals(setOf(validId), result.deletedProjects)
	}

	@Test
	fun `loadSyncData scrubs malformed project ids instead of crashing`() {
		writeSyncData(
			emptySyncData().copy(
				projectsToDelete = setOf(ProjectId("not-a-uuid")),
				deletedProjects = setOf(ProjectId("also-not-a-uuid")),
			)
		)

		// Any mutation forces a load+save cycle, which runs the migration scrub.
		createSynchronizer().createProject("AnyProject")

		val result = readSyncData()
		assertTrue(result.projectsToDelete.isEmpty())
		assertTrue(result.deletedProjects.isEmpty())
	}

	@Test
	fun `syncProjects with no changes ends the sync and reports success`() = runTest {
		writeSyncData(emptySyncData())
		every { projectsRepository.getProjects(any()) } returns emptyList()

		var unauthorized = false
		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = { unauthorized = true })

		assertTrue(result)
		assertFalse(unauthorized)
		coVerify { serverProjectsApi.endProjectsSync("sync-1") }
	}

	@Test
	fun `syncProjects returns false and does not flag auth when begin sync fails generically`() = runTest {
		writeSyncData(emptySyncData())
		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.failure(RuntimeException("boom"))

		var unauthorized = false
		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = { unauthorized = true })

		assertFalse(result)
		assertFalse(unauthorized)
		coVerify(exactly = 0) { serverProjectsApi.endProjectsSync(any()) }
	}

	@Test
	fun `syncProjects calls onUnauthorized when begin sync fails with auth error`() = runTest {
		writeSyncData(emptySyncData())
		coEvery { serverProjectsApi.beginProjectsSync() } returns Result.failure(unauthorizedException())

		var unauthorized = false
		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = { unauthorized = true })

		assertFalse(result)
		assertTrue(unauthorized)
	}

	@Test
	fun `syncProjects creates a local-only project on the server and saves its id`() = runTest {
		writeSyncData(emptySyncData())
		val def = projectDef("LocalNovel")
		val newId = ProjectId.randomUUID()

		every { projectsRepository.getProjects(any()) } returns listOf(def)
		every { projectsRepository.getProjectId(def) } returns null
		every { projectsRepository.getProjectDefinition("LocalNovel") } returns def
		coEvery { serverProjectsApi.createProject("LocalNovel", "sync-1") } returns
			Result.success(CreateProjectResponse(newId, alreadyExisted = false))

		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = {})

		assertTrue(result)
		coVerify { serverProjectsApi.createProject("LocalNovel", "sync-1") }
		verify { projectsRepository.setProjectId(def, newId) }
		assertTrue(readSyncData().projectsToCreate.isEmpty())
	}

	@Test
	fun `syncProjects deletes a server project this client marked for deletion`() = runTest {
		val id = ProjectId.randomUUID()
		writeSyncData(emptySyncData().copy(projectsToDelete = setOf(id)))
		every { projectsRepository.getProjects(any()) } returns emptyList()
		coEvery { serverProjectsApi.deleteProject(id, "sync-1") } returns Result.success("ok")

		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = {})

		assertTrue(result)
		coVerify { serverProjectsApi.deleteProject(id, "sync-1") }
		val sync = readSyncData()
		assertTrue(sync.projectsToDelete.isEmpty())
		assertEquals(setOf(id), sync.deletedProjects)
	}

	@Test
	fun `syncProjects deletes a local project the server has deleted`() = runTest {
		val id = ProjectId.randomUUID()
		val def = projectDef("GoneNovel")
		writeSyncData(emptySyncData())
		coEvery { serverProjectsApi.beginProjectsSync() } returns
			Result.success(emptyServerResponse().copy(deletedProjects = setOf(id)))
		every { projectsRepository.findProject(id) } returns def
		every { projectsRepository.getProjects(any()) } returns emptyList()

		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = {})

		assertTrue(result)
		verify { projectsRepository.deleteProject(def) }
	}

	@Test
	fun `syncProjects renames a project on the server`() = runTest {
		val id = ProjectId.randomUUID()
		writeSyncData(emptySyncData().copy(projectsToRename = setOf(RenamedProject(id, "NewName"))))
		every { projectsRepository.getProjects(any()) } returns emptyList()
		coEvery { serverProjectsApi.renameProject(id, "sync-1", "NewName") } returns Result.success("ok")

		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = {})

		assertTrue(result)
		coVerify { serverProjectsApi.renameProject(id, "sync-1", "NewName") }
		assertTrue(readSyncData().projectsToRename.isEmpty())
	}

	@Test
	fun `syncProjects creates a local project from a new server project`() = runTest {
		writeSyncData(emptySyncData())
		val serverId = ProjectId.randomUUID()
		val serverProject = ApiProjectDefinition(name = "ServerNovel", uuid = serverId)
		val createdDef = projectDef("ServerNovel")

		coEvery { serverProjectsApi.beginProjectsSync() } returns
			Result.success(emptyServerResponse().copy(projects = setOf(serverProject)))
		every { projectsRepository.getProjects(any()) } returns emptyList()
		every { projectsRepository.findProject("ServerNovel") } returns null
		every { projectsRepository.createProject("ServerNovel") } returns CResult.success(createdDef)

		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = {})

		assertTrue(result)
		verify { projectsRepository.createProject("ServerNovel") }
		verify { projectsRepository.setProjectId(createdDef, serverId) }
	}

	@Test
	fun `syncProjects sanitizes an illegal server project name before creating it locally`() = runTest {
		writeSyncData(emptySyncData())
		val serverId = ProjectId.randomUUID()
		val mangledName = "Alice In Wonderland (# Name clash 2026-06-07 fk6fycC #)"
		val safeName = ProjectsRepository.toLocalSafeName(mangledName)
		val serverProject = ApiProjectDefinition(name = mangledName, uuid = serverId)
		val createdDef = projectDef(safeName)

		coEvery { serverProjectsApi.beginProjectsSync() } returns
			Result.success(emptyServerResponse().copy(projects = setOf(serverProject)))
		every { projectsRepository.getProjects(any()) } returns emptyList()
		every { projectsRepository.findProject(safeName) } returns null
		every { projectsRepository.createProject(safeName) } returns CResult.success(createdDef)

		val result = createSynchronizer().syncProjects(onLog = {}, onUnauthorized = {})

		assertTrue(result)
		verify { projectsRepository.createProject(safeName) }
		verify(exactly = 0) { projectsRepository.createProject(mangledName) }
		verify { projectsRepository.setProjectId(createdDef, serverId) }
	}
}
