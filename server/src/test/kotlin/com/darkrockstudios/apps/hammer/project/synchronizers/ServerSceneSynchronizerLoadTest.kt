package com.darkrockstudios.apps.hammer.project.synchronizers

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.EntityNotFound
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utils.BaseTest
import createProject
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServerSceneSynchronizerLoadTest : BaseTest() {

	protected lateinit var fileSystem: FakeFileSystem
	protected lateinit var json: Json
	protected lateinit var log: io.ktor.util.logging.Logger

	@Before
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		json = mockk()

		log = mockk(relaxed = true)
	}

	@Test
	fun `Decode Scene JSON - SerializationException`() = runTest {
		val userId = 1L
		val entityId = 1
		val projectDef = ProjectDefinition(PROJECT_1_NAME)

		createProject(userId, projectDef.name, fileSystem)

		val exception = SerializationException("test")
		every { json.decodeFromString<ApiProjectEntity.SceneEntity>(any(), any()) } answers {
			throw exception
		}

		val synchronizer = ServerSceneSynchronizer(fileSystem, json, log)
		val result = synchronizer.loadEntity(userId, projectDef, entityId)
		assertTrue(isFailure(result))
		assertEquals(exception, result.exception)
	}

	@Test
	fun `Decode Scene JSON - Entity Not Found`() = runTest {
		val userId = 1L
		val entityId = 10 // Not a real Entity ID
		val projectDef = ProjectDefinition(PROJECT_1_NAME)

		createProject(userId, projectDef.name, fileSystem)

		val exception = EntityNotFound(entityId)
		every { json.decodeFromString<ApiProjectEntity.SceneEntity>(any(), any()) } answers {
			throw exception
		}

		val synchronizer = ServerSceneSynchronizer(fileSystem, json, log)
		val result = synchronizer.loadEntity(userId, projectDef, entityId)
		assertTrue(isFailure(result))
		val resultException = result.exception
		assertIs<EntityNotFound>(resultException)
		assertEquals(entityId, resultException.id)
	}
}