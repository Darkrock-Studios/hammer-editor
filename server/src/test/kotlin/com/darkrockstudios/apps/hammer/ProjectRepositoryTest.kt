package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.*
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ProjectRepositoryTest : BaseTest() {
	private val userId = 1L
	private val projectDefinition = ProjectDefinition("Test Project")

	private lateinit var fileSystem: FileSystem
	private lateinit var sceneSynchronizer: ServerSceneSynchronizer
	private lateinit var clock: TestClock

	@Before
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		clock = TestClock(Clock.System)
		sceneSynchronizer = mockk()

		val testModule = module {
			single { fileSystem } bind FileSystem::class
			single { Json } bind Json::class
			single { sceneSynchronizer }
			single { clock } bind TestClock::class
		}
		setupKoin(testModule)
	}

	@Test
	fun createUserData() {
		createProjectRepository().apply {
			createUserData(userId)

			val userDir = getUserDirectory(userId)
			assertTrue { fileSystem.exists(userDir) }

			val dataFile = userDir / ProjectRepository.DATA_FILE
			assertTrue { fileSystem.exists(dataFile) }
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Begin Project Sync`() = runTest {
		createProjectRepository().apply {
			val result = beginProjectSync(userId, projectDefinition)

			assertTrue { result.isSuccess }

			val syncid = result.getOrNull()
			assertNotNull(syncid)
			assertTrue(syncid.isNotBlank())
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `End Project Sync`() = runTest {
		createProjectRepository().apply {
			val beginResult = beginProjectSync(userId, projectDefinition)

			assertTrue { beginResult.isSuccess }
			val syncId = beginResult.getOrThrow()

			val endResult = endProjectSync(userId, projectDefinition, syncId, lastSync, lastId)
			assertTrue { endResult.isSuccess }
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `End Project Sync - Invalid SyncId`() = runTest {
		createProjectRepository().apply {
			val endResult = endProjectSync(userId, projectDefinition, "invalid-id", lastSync, lastId)
			assertFalse { endResult.isSuccess }
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `loadEntity - Expired SyncId`() = runTest {
		val entityId = 1
		every { sceneSynchronizer.loadEntity(userId, projectDefinition, entityId) } returns
				Result.success(createSceneEntity(entityId))

		createProjectRepository().apply {
			val beginResult = beginProjectSync(userId, projectDefinition)
			assertTrue(beginResult.isSuccess)

			val syncId = beginResult.getOrThrow()

			clock.advanceTime(ProjectSynchronizationSession.Companion.EXPIRATION_TIME + 1.minutes)

			val result = loadEntity(userId, projectDefinition, 1, ApiProjectEntity.Type.SCENE, syncId)
			assertTrue(result.isFailure)

			val exception = result.exceptionOrNull()
			assertTrue(exception is InvalidSyncIdException)
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `hasProject with invalid SyncId`() = runTest {
		// Project does indeed exist, but the syncId is invalid
		createProjectDir()

		ProjectRepository(fileSystem, Json, sceneSynchronizer, clock).apply {
			val result = getProjectLastSync(userId, projectDefinition, "invalid-id")
			assertFalse(result.isSuccess)
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `hasProject, no project`() = runTest {
		createProjectRepository().apply {
			val beginResult = beginProjectSync(userId, projectDefinition)
			assertTrue(beginResult.isSuccess)

			val syncId = beginResult.getOrThrow()

			val result = getProjectLastSync(userId, projectDefinition, syncId)
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

			val syncId = beginResult.getOrThrow()


			val result = getProjectLastSync(userId, projectDefinition, syncId)
			assertTrue(result.isSuccess)
		}
	}

	private fun createSceneEntity(entityId: Int): ApiProjectEntity.SceneEntity {
		return ApiProjectEntity.SceneEntity(
			id = entityId,
			sceneType = ApiProjectEntity.SceneEntity.SceneType.Scene,
			name = "Test Scene",
			order = 1,
			path = emptyList(),
			content = "Test Content",
		)
	}

	private fun createProjectRepository(): ProjectRepository {
		return ProjectRepository(fileSystem, Json, sceneSynchronizer, clock)
	}

	private fun createProjectDir() {
		val userDir = ProjectRepository.getUserDirectory(userId, fileSystem)
		val projectDir = userDir / projectDefinition.name
		fileSystem.createDirectories(projectDir)
	}
}