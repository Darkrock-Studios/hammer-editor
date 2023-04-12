package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import io.mockk.mockk
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

class ProjectsRepositoryTest : BaseTest() {
	private val userId = 1L

	private lateinit var fileSystem: FileSystem
	private lateinit var clock: TestClock

	private lateinit var projectsSessionManager: SyncSessionManager<ProjectsSynchronizationSession>

	private fun createProjectsRepository(): ProjectsRepository {
		return ProjectsRepository(fileSystem, Json, clock)
	}

	@Before
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		clock = TestClock(Clock.System)

		projectsSessionManager = mockk()

		val testModule = module {
			single { fileSystem } bind FileSystem::class
			single { Json } bind Json::class
			single { clock } bind TestClock::class

			single<SyncSessionManager<ProjectsSynchronizationSession>>(named(PROJECTS_SYNC_MANAGER)) {
				projectsSessionManager
			}
		}
		setupKoin(testModule)
	}

	@Test
	fun `Create User Data`() {
		val userDir = ProjectsRepository.getUserDirectory(userId, fileSystem)
		assertFalse { fileSystem.exists(userDir) }

		createProjectsRepository().apply {
			createUserData(userId)
		}

		assertTrue { fileSystem.exists(userDir) }

		val dataFile = ProjectsRepository.getSyncDataPath(userId, fileSystem)
		assertTrue { fileSystem.exists(dataFile) }
	}

	/*
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `hasProject, no project`() = runTest {
		coEvery { projectsSessionManager.hasActiveSyncSession(any()) } returns false
		coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns false

		val projectDir = getProjectDirectory(userId, projectDefinition, fileSystem)
		assertFalse { fileSystem.exists(projectDir) }

		val syncId = "sync-id"

		mockCreateSession(syncId)

		createProjectRepository().apply {
			val beginResult = beginProjectSync(userId, projectDefinition)
			assertTrue(beginResult.isSuccess)

			assertFalse { fileSystem.exists(projectDir) }

			val syncBegan = beginResult.getOrThrow()

			coEvery { projectSessionManager.hasActiveSyncSession(any()) } returns true
			coEvery { projectSessionManager.validateSyncId(any(), any()) } returns true

			val result = getProjectSyncData(userId, projectDefinition, syncBegan.syncId)
			assertFalse(result.isSuccess)
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `hasProject, project exists`() = runTest {
		createProjectDir()

		createProjectRepository().apply {
			val beginResult = beginProjectSync(userId, projectDefinition)
			assertTrue(beginResult.isSuccess)

			val syncBegan = beginResult.getOrThrow()

			val result = getProjectSyncData(userId, projectDefinition, syncBegan.syncId)
			assertTrue(result.isSuccess)
		}
	}
	*/
}