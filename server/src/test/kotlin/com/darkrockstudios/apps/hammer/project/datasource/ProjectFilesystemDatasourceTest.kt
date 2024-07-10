package com.darkrockstudios.apps.hammer.project.datasource

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.project.EntityDefinition
import com.darkrockstudios.apps.hammer.project.EntityNotFound
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectFilesystemDatasource
import com.darkrockstudios.apps.hammer.project.ProjectSyncData
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utils.BaseTest
import createProject
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectFilesystemDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var json: Json

	private val userId = 1L
	private val projectDef = ProjectDefinition("Test Project")

	@Before
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		json = createJsonSerializer()
	}

	@Test
	fun `Check Project Exists`() {
		val projDir = ProjectFilesystemDatasource.getProjectDirectory(
			userId,
			projectDef,
			fileSystem
		)
		fileSystem.createDirectories(projDir)

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val result = datasource.checkProjectExists(userId, projectDef)
		assertTrue(result)
	}

	@Test
	fun `Check Project Exists - No Project`() {
		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val result = datasource.checkProjectExists(userId, projectDef)
		assertFalse(result)
	}

	@Test
	fun `Create Project`() {
		val projDir = ProjectFilesystemDatasource.getProjectDirectory(
			userId,
			projectDef,
			fileSystem
		)

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		datasource.createProject(userId, projectDef)

		val result = fileSystem.exists(projDir)
		assertTrue(result)
	}

	@Test
	fun `Delete Project`() {
		val userId = 1L
		val projectName = "Test Project"

		val projectDir = ProjectFilesystemDatasource.getProjectDirectory(
			userId,
			ProjectDefinition(projectName),
			fileSystem
		)
		val testFile1 = projectDir / "test1.txt"
		val testFile2 = projectDir / "test2.txt"

		fileSystem.createDirectories(projectDir)
		fileSystem.write(testFile1) {
			writeUtf8("test1")
		}
		fileSystem.write(testFile2) {
			writeUtf8("test2")
		}

		assertTrue { fileSystem.exists(testFile1) }
		assertTrue { fileSystem.exists(testFile2) }
		assertTrue { fileSystem.exists(projectDir) }

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		datasource.deleteProject(userId, projectName)

		assertFalse { fileSystem.exists(testFile1) }
		assertFalse { fileSystem.exists(testFile2) }
		assertFalse { fileSystem.exists(projectDir) }
	}

	@Test
	fun `Load Project Sync Data`() {
		val syncData = ProjectSyncData(
			lastSync = Instant.fromEpochSeconds(123),
			lastId = 456,
			deletedIds = setOf(789)
		)

		val syncDataPath = ProjectFilesystemDatasource.getProjectSyncDataPath(
			userId,
			projectDef,
			fileSystem,
		)

		fileSystem.createDirectories(syncDataPath.parent!!)
		fileSystem.writeJson(syncDataPath, json, syncData)

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val result = datasource.loadProjectSyncData(userId, projectDef)
		assertEquals(syncData, result)
	}

	@Test
	fun `Load Project Sync Data - No Data`() {
		val defaultSyncData = ProjectSyncData(
			lastId = -1,
			lastSync = Instant.DISTANT_PAST,
			deletedIds = emptySet(),
		)

		val syncDataPath = ProjectFilesystemDatasource.getProjectDirectory(
			userId,
			projectDef,
			fileSystem,
		)
		fileSystem.createDirectories(syncDataPath)

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val loadedSyncData = datasource.loadProjectSyncData(userId, projectDef)

		assertEquals(defaultSyncData, loadedSyncData)
	}

	@Test
	fun `Find Last ID`() = runTest {
		setupEntities()

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val lastId = datasource.findLastId(userId, projectDef)
		assertEquals(20, lastId)
	}

	@Test
	fun `Find Entity Types`() = runTest {
		setupEntities()

		val datasource = ProjectFilesystemDatasource(fileSystem, json)

		var entityType = datasource.findEntityType(1, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.SCENE, entityType)

		entityType = datasource.findEntityType(6, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.SCENE_DRAFT, entityType)

		entityType = datasource.findEntityType(10, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.TIMELINE_EVENT, entityType)

		entityType = datasource.findEntityType(14, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY, entityType)

		entityType = datasource.findEntityType(16, userId, projectDef)
		assertEquals(ApiProjectEntity.Type.NOTE, entityType)
	}

	@Test
	fun `Find Entity Defs`() = runTest {
		setupEntities()

		val datasource = ProjectFilesystemDatasource(fileSystem, json)

		val entityDefs = datasource.getEntityDefs(userId, projectDef) { true }
		val expectedDefs = listOf(
			EntityDefinition(1, ApiProjectEntity.Type.SCENE),
			EntityDefinition(2, ApiProjectEntity.Type.SCENE),
			EntityDefinition(4, ApiProjectEntity.Type.SCENE),
			EntityDefinition(5, ApiProjectEntity.Type.SCENE),
			EntityDefinition(6, ApiProjectEntity.Type.SCENE_DRAFT),
			EntityDefinition(7, ApiProjectEntity.Type.SCENE_DRAFT),
			EntityDefinition(10, ApiProjectEntity.Type.TIMELINE_EVENT),
			EntityDefinition(12, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY),
			EntityDefinition(14, ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY),
			EntityDefinition(15, ApiProjectEntity.Type.SCENE),
			EntityDefinition(16, ApiProjectEntity.Type.NOTE),
			EntityDefinition(20, ApiProjectEntity.Type.NOTE),
		)

		assertEquals(expectedDefs, entityDefs.sortedBy { it.id })
	}

	@Test
	fun `Load Entity - Decode Scene JSON - SerializationException`() = runTest {
		val userId = 1L
		val entityId = 1
		val projectDef = ProjectDefinition(PROJECT_1_NAME)

		createProject(userId, projectDef.name, fileSystem)

		json = mockk()

		val exception = SerializationException("test")
		every { json.decodeFromString<ApiProjectEntity.SceneEntity>(any(), any()) } answers {
			throw exception
		}

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val result = datasource.loadEntity(
			userId,
			projectDef,
			entityId,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer()
		)

		assertTrue(isFailure(result))
		assertEquals(exception, result.exception)
	}

	@Test
	fun `Load Entity - Decode Scene JSON - Entity Not Found`() = runTest {
		val userId = 1L
		val entityId = 10 // Not a real Entity ID
		val projectDef = ProjectDefinition(PROJECT_1_NAME)

		createProject(userId, projectDef.name, fileSystem)

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val result = datasource.loadEntity(
			userId,
			projectDef,
			entityId,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer()
		)

		assertTrue(isFailure(result))
		val resultException = result.exception
		assertIs<EntityNotFound>(resultException)
		assertEquals(entityId, resultException.id)
	}

	@Test
	fun `Delete Entity`() = runTest {
		val entityId = 1
		setupEntities()

		val path = ProjectFilesystemDatasource.getEntityPath(
			userId = userId,
			entityType = ApiProjectEntity.Type.SCENE,
			projectDef = projectDef,
			entityId = entityId,
			fileSystem = fileSystem
		)
		assertTrue(fileSystem.exists(path))

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val result =
			datasource.deleteEntity(userId, ApiProjectEntity.Type.SCENE, projectDef, entityId)
		assertTrue(result.isSuccess)
		assertFalse(fileSystem.exists(path))
	}

	@Test
	fun `Delete Entity - Failure - Wrong Type`() = runTest {
		val entityId = 1
		setupEntities()

		val path = ProjectFilesystemDatasource.getEntityPath(
			userId = userId,
			entityType = ApiProjectEntity.Type.SCENE,
			projectDef = projectDef,
			entityId = entityId,
			fileSystem = fileSystem
		)
		assertTrue(fileSystem.exists(path))

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val result =
			datasource.deleteEntity(userId, ApiProjectEntity.Type.NOTE, projectDef, entityId)
		assertTrue(result.isSuccess)
		assertTrue(fileSystem.exists(path))
	}

	@Test
	fun `Store Entity - Scene Entity`() = runTest {
		val entityId = 1
		val entity = ApiProjectEntity.SceneEntity(
			id = entityId,
			name = "test",
			content = "test content",
			order = 0,
			path = listOf(0),
			sceneType = ApiSceneType.Scene,
			outline = "outline",
			notes = "notes",
		)

		val datasource = ProjectFilesystemDatasource(fileSystem, json)
		val result = datasource.storeEntity(
			userId,
			projectDef,
			entity,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer()
		)
		assertTrue(result.isSuccess)

		val path = ProjectFilesystemDatasource.getEntityPath(
			userId = userId,
			entityType = ApiProjectEntity.Type.SCENE,
			projectDef = projectDef,
			entityId = entityId,
			fileSystem = fileSystem
		)
		assertTrue(fileSystem.exists(path))
	}

	private fun setupEntities() {
		val entityDir =
			ProjectFilesystemDatasource.getEntityDirectory(userId, projectDef, fileSystem)
		fileSystem.apply {
			createDirectories(entityDir)
			write(entityDir / "1-scene.json") { writeUtf8("fake-scene-content") }
			write(entityDir / "2-scene.json") { writeUtf8("fake-scene-content") }
			write(entityDir / "4-scene.json") { writeUtf8("fake-scene-content") }
			write(entityDir / "5-scene.json") { writeUtf8("fake-scene-content") }
			write(entityDir / "6-scene_draft.json") { writeUtf8("fake-scene-content") }
			write(entityDir / "7-scene_draft.json") { writeUtf8("fake-scene-content") }
			write(entityDir / "10-timeline_event.json") { writeUtf8("fake-content") }
			write(entityDir / "12-encyclopedia_entry.json") { writeUtf8("fake-content") }
			write(entityDir / "14-encyclopedia_entry.json") { writeUtf8("fake-content") }
			write(entityDir / "15-scene.json") { writeUtf8("fake-content") }
			write(entityDir / "16-note.json") { writeUtf8("fake-content") }
			write(entityDir / "20-note.json") { writeUtf8("fake-content") }
		}
	}
}