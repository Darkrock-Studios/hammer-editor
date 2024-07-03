package com.darkrockstudios.apps.hammer.project.repository

import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.ProjectDatasource
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerNoteSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerTimelineSynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

abstract class ProjectRepositoryBaseTest : BaseTest() {
	protected val userId = 1L
	protected val projectDefinition = ProjectDefinition("Test Project")

	protected lateinit var fileSystem: FileSystem
	protected lateinit var clock: TestClock

	protected lateinit var projectsSessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession>
	protected lateinit var projectSessionManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>

	protected lateinit var sceneSynchronizer: ServerSceneSynchronizer
	protected lateinit var noteSynchronizer: ServerNoteSynchronizer
	protected lateinit var timelineEventSynchronizer: ServerTimelineSynchronizer
	protected lateinit var encyclopediaSynchronizer: ServerEncyclopediaSynchronizer
	protected lateinit var sceneDraftSynchronizer: ServerSceneDraftSynchronizer
	protected lateinit var projectDatasource: ProjectDatasource

	protected lateinit var clientState: ClientEntityState

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
		projectDatasource = mockk()

		clientState = mockk()

		coEvery {
			sceneSynchronizer.getUpdateSequence(
				userId,
				projectDefinition,
				clientState
			)
		} returns emptyList()
		coEvery {
			noteSynchronizer.getUpdateSequence(
				userId,
				projectDefinition,
				clientState
			)
		} returns emptyList()
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
		coEvery {
			sceneDraftSynchronizer.getUpdateSequence(
				userId,
				projectDefinition,
				clientState
			)
		} returns emptyList()

		val testModule = module {
			single { fileSystem } bind FileSystem::class
			single { Json } bind Json::class
			single { sceneSynchronizer }
			single { noteSynchronizer }
			single { timelineEventSynchronizer }
			single { encyclopediaSynchronizer }
			single { sceneDraftSynchronizer }
			single { clock } bind TestClock::class

			single<SyncSessionManager<Long, ProjectsSynchronizationSession>>(
				named(
					PROJECTS_SYNC_MANAGER
				)
			) {
				projectsSessionManager
			}

			single<SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>>(
				named(
					PROJECT_SYNC_MANAGER
				)
			) {
				projectSessionManager
			}
		}
		setupKoin(testModule)
	}

	protected fun mockCreateSession(syncId: String) {
		val createSessionSlot =
			slot<(key: ProjectSyncKey, syncId: String) -> ProjectSynchronizationSession>()
		val key = ProjectSyncKey(userId, projectDefinition)
		coEvery {
			projectSessionManager.createNewSession(
				key,
				capture(createSessionSlot)
			)
		} coAnswers {
			val session = createSessionSlot.captured(key, syncId)
			session.syncId
		}
	}

	protected fun createProjectRepository(): ProjectRepository {
		return ProjectRepository(clock, projectDatasource)
	}
}