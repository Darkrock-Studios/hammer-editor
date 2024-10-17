package com.darkrockstudios.apps.hammer.projects.repository

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.projects.ProjectsDatasource
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

abstract class ProjectsRepositoryBaseTest : BaseTest() {
	protected val userId = 1L

	protected lateinit var clock: TestClock

	protected lateinit var projectsSessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession>
	protected lateinit var projectSessionManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>

	protected lateinit var projectsRepository: ProjectsRepository
	protected lateinit var projectsDatasource: ProjectsDatasource
	protected lateinit var projectEntityDatasource: ProjectEntityDatasource

	protected val projectDefinition = ProjectDefinition("Test Project 1", ProjectId("test-uuid-1"))

	protected fun createProjectsRepository(): ProjectsRepository {
		return ProjectsRepository(clock, projectsDatasource, projectEntityDatasource)
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

	@BeforeEach
	override fun setup() {
		super.setup()

		clock = TestClock(Clock.System)

		projectsSessionManager = mockk()
		projectSessionManager = mockk()

		projectsDatasource = mockk()
		projectsRepository = mockk()

		projectEntityDatasource = mockk()

		val testModule = module {
			single { Json } bind Json::class
			single { clock } bind TestClock::class

			single<SyncSessionManager<Long, ProjectsSynchronizationSession>>(
				named(
					PROJECTS_SYNC_MANAGER
				)
			) {
				projectsSessionManager
			}
		}
		setupKoin(testModule)
	}
}