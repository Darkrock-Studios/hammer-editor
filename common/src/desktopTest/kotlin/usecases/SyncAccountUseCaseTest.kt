package usecases

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountListener
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountUseCase
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityConflictHandler
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.Locale
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncAccountUseCaseTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var metadataDatasource: ProjectMetadataDatasource
	private lateinit var accountSynchronizer: ClientAccountSynchronizer
	private lateinit var listener: RecordingListener

	private val projectSynchronizers = mutableMapOf<String, ClientProjectSynchronizer>()

	private class RecordingListener : SyncAccountListener {
		val logs = mutableListOf<SyncLogMessage>()
		val outcomes = mutableMapOf<String, ProjectSyncOutcome>()
		var unauthorized = 0

		override suspend fun onLog(message: SyncLogMessage) {
			logs += message
		}

		override suspend fun onProjectsDiscovered(projects: List<ProjectDef>) {}

		override suspend fun onProjectProgress(projectDef: ProjectDef, progress: Float?) {}

		override suspend fun onProjectOutcome(projectDef: ProjectDef, outcome: ProjectSyncOutcome) {
			outcomes[projectDef.name] = outcome
		}

		override suspend fun onUnauthorized() {
			unauthorized++
		}
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		ffs.createDirectories("/projects".toPath())
		toml = createTomlSerializer()

		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.globalSettingsUpdates } returns MutableSharedFlow()

		val deviceLocaleResolver = mockk<DeviceLocaleResolver>()
		every { deviceLocaleResolver.getCurrentLocale() } returns Locale.forLanguage("en", "US")

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ClientProjectSynchronizer> { params ->
					projectSynchronizers.getValue(params.get<ProjectDef>().name)
				}
				scoped { params -> ProjectDataConflictBroker(params.get()) }
				scoped<SceneEditorService> { mockk(relaxed = true) }
				scoped<TimeLineRepository> { mockk(relaxed = true) }
			}
		})

		metadataDatasource = ProjectMetadataDatasource(ffs, toml)
		projectsRepository = ProjectsRepository(
			fileSystem = ffs,
			globalSettingsStore = globalSettingsStore,
			projectsMetadataDatasource = metadataDatasource,
			toml = toml,
			json = createJsonSerializer(),
			deviceLocaleResolver = deviceLocaleResolver,
		)

		accountSynchronizer = mockk(relaxed = true)
		coEvery { accountSynchronizer.syncProjects(any(), any(), any(), any()) } returns true
		coEvery { accountSynchronizer.probeUnchangedProjects(any()) } returns emptySet()

		projectSynchronizers.clear()
		listener = RecordingListener()
	}

	private fun useCase() = SyncAccountUseCase(
		projectsRepository = projectsRepository,
		accountSynchronizer = accountSynchronizer,
		projectMetadataDatasource = metadataDatasource,
		strRes = TestStrRes(),
	)

	private fun localProject(name: String, serverId: String? = null): ProjectDef {
		val result = projectsRepository.createProject(name, seedDefaultLanguage = false)
		if (!isSuccess(result)) error("Failed to create $name")
		val def = result.data
		if (serverId != null) {
			metadataDatasource.updateMetadata(def) {
				it.copy(info = it.info.copy(serverProjectId = ProjectId(serverId)))
			}
		}
		return def
	}

	private fun projectSyncReturns(name: String, success: Boolean): ClientProjectSynchronizer {
		val synchronizer = mockk<ClientProjectSynchronizer>()
		coEvery { synchronizer.sync(any(), any(), any(), any(), any(), any()) } returns success
		projectSynchronizers[name] = synchronizer
		return synchronizer
	}

	@Test
	fun `account sync failure fails every project without syncing any`() = runTest {
		localProject("Alpha", "a")
		localProject("Beta", "b")
		val alphaSync = projectSyncReturns("Alpha", true)
		coEvery { accountSynchronizer.syncProjects(any(), any(), any(), any()) } returns false

		val result = useCase().execute(listener)

		assertFalse(result.accountSuccess)
		assertFalse(result.allSuccess)
		assertEquals(
			mapOf("Alpha" to ProjectSyncOutcome.Failed, "Beta" to ProjectSyncOutcome.Failed),
			result.projects,
		)
		assertEquals(result.projects, listener.outcomes)
		coVerify(exactly = 0) { alphaSync.sync(any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `every synced project is synced and reported`() = runTest {
		localProject("Alpha", "a")
		localProject("Beta", "b")
		projectSyncReturns("Alpha", true)
		projectSyncReturns("Beta", true)

		val result = useCase().execute(listener)

		assertTrue(result.allSuccess)
		assertEquals(
			mapOf("Alpha" to ProjectSyncOutcome.Success, "Beta" to ProjectSyncOutcome.Success),
			result.projects,
		)
		assertEquals(result.projects, listener.outcomes)
	}

	@Test
	fun `projects the probe reports unchanged are not synced`() = runTest {
		localProject("Alpha", "a")
		val alphaSync = projectSyncReturns("Alpha", true)
		coEvery { accountSynchronizer.probeUnchangedProjects(any()) } returns setOf(ProjectId("a"))

		val result = useCase().execute(listener)

		assertTrue(result.allSuccess)
		assertEquals(ProjectSyncOutcome.Unchanged, result.projects["Alpha"])
		coVerify(exactly = 0) { alphaSync.sync(any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `a project without a server id is reported but not synced`() = runTest {
		localProject("Draft")

		val result = useCase().execute(listener)

		assertTrue(result.allSuccess)
		assertEquals(ProjectSyncOutcome.NotOnServer, result.projects["Draft"])
	}

	@Test
	fun `the filter skips excluded projects and keeps them out of the probe`() = runTest {
		localProject("Alpha", "a")
		localProject("Beta", "b")
		projectSyncReturns("Alpha", true)
		val betaSync = projectSyncReturns("Beta", true)

		val result = useCase().execute(listener) { it.projectId == ProjectId("a") }

		assertTrue(result.allSuccess)
		assertEquals(ProjectSyncOutcome.Success, result.projects["Alpha"])
		assertEquals(ProjectSyncOutcome.Skipped, result.projects["Beta"])
		coVerify(exactly = 0) { betaSync.sync(any(), any(), any(), any(), any(), any()) }
		coVerify {
			accountSynchronizer.probeUnchangedProjects(match { list -> list.map { it.projectId } == listOf(ProjectId("a")) })
		}
	}

	@Test
	fun `an exception in one project fails only that project`() = runTest {
		localProject("Alpha", "a")
		localProject("Beta", "b")
		projectSyncReturns("Alpha", true)
		val betaSync = mockk<ClientProjectSynchronizer>()
		coEvery { betaSync.sync(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("boom")
		projectSynchronizers["Beta"] = betaSync

		val result = useCase().execute(listener)

		assertFalse(result.allSuccess)
		assertEquals(ProjectSyncOutcome.Success, result.projects["Alpha"])
		assertEquals(ProjectSyncOutcome.Failed, result.projects["Beta"])
	}

	@Test
	fun `an entity conflict leaves the project needing resolution`() = runTest {
		localProject("Alpha", "a")
		val alphaSync = mockk<ClientProjectSynchronizer>()
		coEvery { alphaSync.sync(any(), any(), any(), any(), any(), any()) } coAnswers {
			arg<EntityConflictHandler<ApiProjectEntity>>(2).invoke(mockk(relaxed = true))
			true
		}
		projectSynchronizers["Alpha"] = alphaSync

		val result = useCase().execute(listener)

		assertFalse(result.allSuccess)
		assertEquals(ProjectSyncOutcome.NeedsResolution, result.projects["Alpha"])
	}

	@Test
	fun `unauthorized from account and project sync both reach the listener`() = runTest {
		localProject("Alpha", "a")
		coEvery { accountSynchronizer.syncProjects(any(), any(), any(), any()) } coAnswers {
			secondArg<suspend () -> Unit>().invoke()
			true
		}
		val alphaSync = mockk<ClientProjectSynchronizer>()
		coEvery { alphaSync.sync(any(), any(), any(), any(), any(), any()) } coAnswers {
			arg<suspend () -> Unit>(5).invoke()
			false
		}
		projectSynchronizers["Alpha"] = alphaSync

		useCase().execute(listener)

		assertEquals(2, listener.unauthorized)
	}

	@Test
	fun `an ideas failure fails the overall result but projects still sync`() = runTest {
		localProject("Alpha", "a")
		projectSyncReturns("Alpha", true)
		coEvery { accountSynchronizer.syncProjects(any(), any(), any(), any()) } coAnswers {
			arg<(Boolean) -> Unit>(3).invoke(false)
			true
		}

		val result = useCase().execute(listener)

		assertFalse(result.ideasSuccess)
		assertFalse(result.allSuccess)
		assertEquals(ProjectSyncOutcome.Success, result.projects["Alpha"])
	}
}
