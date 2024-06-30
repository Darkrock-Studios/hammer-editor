package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerNoteSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerTimelineSynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.syncsessionmanager.SynchronizationSession
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ProjectRepositoryTest : BaseTest() {
	private val userId = 1L
	private val projectDefinition = ProjectDefinition("Test Project")

	private lateinit var fileSystem: FileSystem
	private lateinit var clock: TestClock

	private lateinit var projectsSessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession>
	private lateinit var projectSessionManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>

	private lateinit var sceneSynchronizer: ServerSceneSynchronizer
	private lateinit var noteSynchronizer: ServerNoteSynchronizer
	private lateinit var timelineEventSynchronizer: ServerTimelineSynchronizer
	private lateinit var encyclopediaSynchronizer: ServerEncyclopediaSynchronizer
	private lateinit var sceneDraftSynchronizer: ServerSceneDraftSynchronizer

	private lateinit var clientState: ClientEntityState

	@Before
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		clock = TestClock(Clock.System)

		projectsSessionManager = mockk()
		projectSessionManager = mockk()

		sceneSynchronizer = mockk()
		noteSynchronizer = mockk()
		timelineEventSynchronizer = mockk()
		encyclopediaSynchronizer = mockk()
		sceneDraftSynchronizer = mockk()

		clientState = mockk()

		coEvery { sceneSynchronizer.getUpdateSequence(userId, projectDefinition, clientState) } returns emptyList()
		coEvery { noteSynchronizer.getUpdateSequence(userId, projectDefinition, clientState) } returns emptyList()
		coEvery {
			timelineEventSynchronizer.getUpdateSequence(
				userId,
				projectDefinition,
				clientState
			)
		} returns emptyList()
		coEvery {
			encyclopediaSynchronizer.getUpdateSequence(
				userId,
				projectDefinition,
				clientState
			)
		} returns emptyList()
		coEvery { sceneDraftSynchronizer.getUpdateSequence(userId, projectDefinition, clientState) } returns emptyList()

		val testModule = module {
			single { fileSystem } bind FileSystem::class
			single { Json } bind Json::class
			single { sceneSynchronizer }
			single { noteSynchronizer }
			single { timelineEventSynchronizer }
			single { encyclopediaSynchronizer }
			single { sceneDraftSynchronizer }
			single { clock } bind TestClock::class

			single<SyncSessionManager<Long, ProjectsSynchronizationSession>>(named(PROJECTS_SYNC_MANAGER)) {
				projectsSessionManager
			}

			single<SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>>(named(PROJECT_SYNC_MANAGER)) {
				projectSessionManager
			}
		}
		setupKoin(testModule)
	}

	private fun mockCreateSession(syncId: String) {
		val createSessionSlot = slot<(key: ProjectSyncKey, syncId: String) -> ProjectSynchronizationSession>()
		val key = ProjectSyncKey(userId, projectDefinition)
		coEvery { projectSessionManager.createNewSession(key, capture(createSessionSlot)) } coAnswers {
			val session = createSessionSlot.captured(key, syncId)
			session.syncId
		}
	}

	@Test
	fun `Begin Project Sync`() = runTest {
		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		val syncId = "sync-id"
		mockCreateSession(syncId)

		createProjectRepository().apply {
			val result = beginProjectSync(userId, projectDefinition, clientState, false)

			assertTrue(isSuccess(result))
			val syncBegan = result.data
			assertTrue(syncBegan.syncId.isNotBlank())
		}
	}

	@Test
	fun `End Project Sync`() = runTest {
		createProjectRepository().apply {

			coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
			coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false
			coEvery { projectSessionManager.terminateSession(any()) } returns true

			val syncId = "sync-id"

			mockCreateSession(syncId)

			val beginResult = beginProjectSync(userId, projectDefinition, clientState, false)

			assertTrue(isSuccess(beginResult))

			val syncBegan = beginResult.data
			val session = ProjectSynchronizationSession(userId, projectDefinition, clock.now(), syncBegan.syncId)
			coEvery { projectSessionManager.findSession(any()) } returns session

			val endResult = endProjectSync(
				userId,
				projectDefinition,
				syncBegan.syncId,
				syncBegan.lastSync,
				syncBegan.lastId
			)
			assertTrue { endResult.isSuccess }
		}
	}

	@Test
	fun `End Project Sync - Invalid SyncId`() = runTest {
		coEvery { projectSessionManager.findSession(any()) } returns null

		createProjectRepository().apply {
			val endResult = endProjectSync(userId, projectDefinition, "invalid-id", null, null)
			assertFalse { endResult.isSuccess }
		}
	}

	@Test
	// TODO should probably move this to a SyncSession test
	fun `loadEntity - Expired SyncId`() = runTest {
		val entityId = 1
		val syncId = "sync-id"

		mockCreateSession(syncId)

		every { sceneSynchronizer.loadEntity(userId, projectDefinition, entityId) } returns
				SResult.success(createSceneEntity(entityId))

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns false

		createProjectRepository().apply {
			val beginResult = beginProjectSync(userId, projectDefinition, clientState, false)
			assertTrue(isSuccess(beginResult))

			val syncBegan = beginResult.data
			clock.advanceTime(SynchronizationSession.EXPIRATION_TIME + 1.minutes)

			val result = loadEntity(userId, projectDefinition, 1, syncBegan.syncId)
			assertTrue(isFailure(result))

			val exception = result.exception
			assertTrue(exception is InvalidSyncIdException)
		}
	}

	@Test
	fun `hasProject with invalid SyncId`() = runTest {
		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.validateSyncId(any(), any(), any()) } returns false

		ProjectRepository(fileSystem, Json, clock).apply {
			val result = getProjectSyncData(userId, projectDefinition, "invalid-id")
			assertFalse(result.isSuccess)
		}
	}

	private fun createSceneEntity(entityId: Int): ApiProjectEntity.SceneEntity {
		return ApiProjectEntity.SceneEntity(
			id = entityId,
			sceneType = ApiSceneType.Scene,
			name = "Test Scene",
			order = 1,
			path = emptyList(),
			content = "Test Content",
			outline = "",
			notes = "",
		)
	}

	private fun createProjectRepository(): ProjectRepository {
		return ProjectRepository(fileSystem, Json, clock)
	}
}