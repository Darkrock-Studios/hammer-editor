package usecases

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.groupmetadata.GroupMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncLock
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncProgress
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncProgressTracker
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncAccountUseCaseTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var metadataDatasource: ProjectMetadataDatasource
	private lateinit var accountSynchronizer: ClientAccountSynchronizer
	private lateinit var listener: RecordingListener
	private lateinit var syncLock: AccountSyncLock

	private val projectSynchronizers = mutableMapOf<String, ClientProjectSynchronizer>()

	private class RecordingListener : SyncAccountListener {
		val logs = mutableListOf<SyncLogMessage>()
		val outcomes = mutableMapOf<String, ProjectSyncOutcome>()
		var unauthorized = 0
		val tracker = AccountSyncProgressTracker()
		val progress: AccountSyncProgress get() = tracker.progress.value

		override suspend fun onLog(message: SyncLogMessage) {
			logs += message
		}

		override suspend fun onProjectsDiscovered(projects: List<ProjectDef>) {
			tracker.onProjectsDiscovered(projects.size)
		}

		override suspend fun onProjectProgress(projectDef: ProjectDef, progress: Float?) {}

		override suspend fun onProjectOutcome(projectDef: ProjectDef, outcome: ProjectSyncOutcome) {
			outcomes[projectDef.name] = outcome
			tracker.onProjectFinished()
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
				scoped<DuplicateIdService> { mockk(relaxed = true) }
				scoped<SceneEditorService> { mockk(relaxed = true) }
				scoped<TimeLineRepository> { mockk(relaxed = true) }
			}
		})

		metadataDatasource = ProjectMetadataDatasource(ffs, toml)
		projectsRepository = ProjectsRepository(
			fileSystem = ffs,
			globalSettingsStore = globalSettingsStore,
			projectsMetadataDatasource = metadataDatasource,
			groupMetadataDatasource = GroupMetadataDatasource(ffs, toml),
			toml = toml,
			json = createJsonSerializer(),
			deviceLocaleResolver = deviceLocaleResolver,
		)

		accountSynchronizer = mockk(relaxed = true)
		coEvery { accountSynchronizer.syncProjects(any(), any(), any(), any()) } returns true
		coEvery { accountSynchronizer.probeUnchangedProjects(any()) } returns emptySet()

		projectSynchronizers.clear()
		listener = RecordingListener()
		syncLock = AccountSyncLock()
	}

	private fun useCase() = SyncAccountUseCase(
		projectsRepository = projectsRepository,
		accountSynchronizer = accountSynchronizer,
		syncLock = syncLock,
		strRes = TestStrRes(),
	)

	private fun localProject(name: String, projectId: String = name.lowercase()): ProjectDef {
		val result = projectsRepository.createProject(
			name,
			seedDefaultLanguage = false,
			projectId = ProjectId(projectId),
		)
		if (!isSuccess(result)) error("Failed to create $name")
		return result.data
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
	fun `a project that has never synced is synced too`() = runTest {
		localProject("Draft")
		projectSyncReturns("Draft", true)

		val result = useCase().execute(listener)

		assertTrue(result.allSuccess)
		assertEquals(ProjectSyncOutcome.Success, result.projects["Draft"])
	}

	@Test
	fun `the filter skips excluded projects and keeps them out of the probe`() = runTest {
		localProject("Alpha", "a")
		localProject("Beta", "b")
		projectSyncReturns("Alpha", true)
		val betaSync = projectSyncReturns("Beta", true)

		val result = useCase().execute(listener) { it.id == ProjectId("a") }

		assertTrue(result.allSuccess)
		assertEquals(ProjectSyncOutcome.Success, result.projects["Alpha"])
		assertEquals(ProjectSyncOutcome.Skipped, result.projects["Beta"])
		coVerify(exactly = 0) { betaSync.sync(any(), any(), any(), any(), any(), any()) }
		coVerify {
			accountSynchronizer.probeUnchangedProjects(match { list -> list.map { it.id } == listOf(ProjectId("a")) })
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

	@Test
	fun `executeIfIdle skips while another account sync is running`() = runTest {
		localProject("Alpha", "a")
		projectSyncReturns("Alpha", true)
		val accountSyncStarted = CompletableDeferred<Unit>()
		val releaseAccountSync = CompletableDeferred<Unit>()
		coEvery { accountSynchronizer.syncProjects(any(), any(), any(), any()) } coAnswers {
			accountSyncStarted.complete(Unit)
			releaseAccountSync.await()
			true
		}
		val running = launch { useCase().execute(RecordingListener()) }
		accountSyncStarted.await()

		val skipped = useCase().executeIfIdle(listener)

		assertNull(skipped)
		assertTrue(listener.outcomes.isEmpty())
		assertEquals(AccountSyncProgress.Waiting, listener.progress)
		releaseAccountSync.complete(Unit)
		running.join()
		assertTrue(useCase().executeIfIdle(listener)!!.allSuccess)
	}

	@Test
	fun `progress follows the account sync and then every project to the end`() = runTest {
		localProject("Alpha", "a")
		localProject("Beta", "b")
		localProject("Gamma", "c")
		var duringAccountSync: AccountSyncProgress? = null
		coEvery { accountSynchronizer.syncProjects(any(), any(), any(), any()) } coAnswers {
			duringAccountSync = listener.progress
			true
		}
		coEvery { accountSynchronizer.probeUnchangedProjects(any()) } returns setOf(ProjectId("b"))
		var duringAlpha: AccountSyncProgress? = null
		val alphaSync = mockk<ClientProjectSynchronizer>()
		coEvery { alphaSync.sync(any(), any(), any(), any(), any(), any()) } coAnswers {
			duringAlpha = listener.progress
			true
		}
		projectSynchronizers["Alpha"] = alphaSync

		useCase().execute(listener) { it.id != ProjectId("c") }

		assertEquals(AccountSyncProgress.Account, duringAccountSync)
		assertEquals(3, (duringAlpha as AccountSyncProgress.Projects).total)
		assertEquals(AccountSyncProgress.Projects(done = 3, total = 3), listener.progress)
	}

	@Test
	fun `progress stays on the account when account sync fails`() = runTest {
		localProject("Alpha", "a")
		coEvery { accountSynchronizer.syncProjects(any(), any(), any(), any()) } returns false

		useCase().execute(listener)

		assertEquals(AccountSyncProgress.Account, listener.progress)
	}
}
