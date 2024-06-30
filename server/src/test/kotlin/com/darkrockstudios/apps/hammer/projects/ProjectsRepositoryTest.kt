package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.ProjectsSyncData
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRepositoryTest : BaseTest() {
	private val userId = 1L

	private lateinit var clock: TestClock

	private lateinit var projectsSessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession>
	private lateinit var projectSessionManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>

	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var projectsDatasource: ProjectsDatasource

	private val projectDefinition = ProjectDefinition("Test Project 1")

	private fun createProjectsRepository(): ProjectsRepository {
		return ProjectsRepository(clock, projectsDatasource)
	}

	private fun mockCreateSession(syncId: String) {
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

	@Before
	override fun setup() {
		super.setup()

		clock = TestClock(Clock.System)

		projectsSessionManager = mockk()
		projectSessionManager = mockk()

		projectsDatasource = mockk()
		projectsRepository = mockk()

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

	@Test
	fun `hasProject, no project`() = runTest {
		val newSyncId = "new-sync-id"
		val expectedData = ProjectsBeginSyncData(
			syncId = newSyncId,
			projects = emptySet(),
			deletedProjects = emptySet()
		)

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectsSessionManager.createNewSession(any(), any()) } returns newSyncId

		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		every { projectsDatasource.getProjects(userId) } returns emptySet()
		every { projectsDatasource.loadSyncData(userId) } returns
			ProjectsSyncData(
				lastSync = Instant.DISTANT_PAST,
				deletedProjects = emptySet()
			)

		mockCreateSession(newSyncId)

		createProjectsRepository().apply {
			val beginResult = beginProjectsSync(userId)
			assertTrue(isSuccess(beginResult))

			verify(exactly = 1) { projectsDatasource.getProjects(userId) }
			verify(exactly = 1) { projectsDatasource.loadSyncData(userId) }

			val syncData = beginResult.data
			assertEquals(expectedData, syncData)
		}
	}

	@Test
	fun `hasProject, has projects`() = runTest {
		val newSyncId = "new-sync-id"

		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectsSessionManager.createNewSession(any(), any()) } returns newSyncId

		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		every { projectsDatasource.getProjects(userId) } returns setOf(
			ProjectDefinition("Project 1"),
			ProjectDefinition("Project 2"),
		)

		every { projectsDatasource.loadSyncData(userId) } returns
			ProjectsSyncData(
				lastSync = Instant.fromEpochSeconds(123),
				deletedProjects = setOf("Project 3")
			)

		mockCreateSession(newSyncId)

		createProjectsRepository().apply {
			val beginResult = beginProjectsSync(userId)
			assertTrue(isSuccess(beginResult))

			val syncData = beginResult.data

			val expectedData = ProjectsBeginSyncData(
				syncId = syncData.syncId,
				projects = setOf(ProjectDefinition("Project 1"), ProjectDefinition("Project 2")),
				deletedProjects = setOf("Project 3")
			)

			assertEquals(expectedData, syncData)
		}
	}
}