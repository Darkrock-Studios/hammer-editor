package com.darkrockstudios.apps.hammer.projects.datasource

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.readJson
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectsSyncData
import com.darkrockstudios.apps.hammer.projects.ProjectsFileSystemDatasource
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsFileSystemDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FileSystem
	private lateinit var json: Json

	@Before
	override fun setup() {
		super.setup()

		fileSystem = FakeFileSystem()
		json = createJsonSerializer()
	}

	@Test
	fun `Create User Data`() {
		val userId = 1L

		val userDir = ProjectsFileSystemDatasource.getUserDirectory(userId, fileSystem)
		assertFalse(userDir.toString()) { fileSystem.exists(userDir) }

		val datasource = ProjectsFileSystemDatasource(fileSystem, json)
		datasource.createUserData(userId)

		assertTrue { fileSystem.exists(userDir) }

		val dataFile = ProjectsFileSystemDatasource.getSyncDataPath(userId, fileSystem)
		assertTrue { fileSystem.exists(dataFile) }
	}

	@Test
	fun `Get Projects`() {
		val userId = 1L
		val projectName1 = "Test Project 1"
		val projectName2 = "Test Project 2"

		val userDir = ProjectsFileSystemDatasource.getUserDirectory(userId, fileSystem)
		val project1Dir = userDir / projectName1
		val project2Dir = userDir / projectName2

		fileSystem.createDirectories(project1Dir)
		fileSystem.createDirectories(project2Dir)
		fileSystem.createDirectories(userDir / ".hidden")

		val datasource = ProjectsFileSystemDatasource(fileSystem, json)
		val projects = datasource.getProjects(userId)

		assertEquals(
			setOf(
				ProjectDefinition("Test Project 1"),
				ProjectDefinition("Test Project 2"),
			),
			projects
		)
	}

	@Test
	fun `Save SyncData`() {
		val userId = 1L
		val syncDataPath = ProjectsFileSystemDatasource.getSyncDataPath(userId, fileSystem)
		fileSystem.createDirectories(syncDataPath.parent!!)

		val syncData = ProjectsSyncData(
			lastSync = Instant.fromEpochSeconds(123),
			deletedProjects = setOf(
				"Test Project 1",
				"Test Project 2",
			)
		)

		val datasource = ProjectsFileSystemDatasource(fileSystem, json)
		datasource.saveSyncData(userId, syncData)

		val data: ProjectsSyncData? = fileSystem.readJson(syncDataPath, json)
		assertEquals(syncData, data)
	}

	@Test
	fun `Load SyncData`() {
		val userId = 1L
		val syncDataPath = ProjectsFileSystemDatasource.getSyncDataPath(userId, fileSystem)
		fileSystem.createDirectories(syncDataPath.parent!!)

		val syncData = ProjectsSyncData(
			lastSync = Instant.fromEpochSeconds(123),
			deletedProjects = setOf(
				"Test Project 1",
				"Test Project 2",
			)
		)

		fileSystem.writeJson(syncDataPath, json, syncData)

		val datasource = ProjectsFileSystemDatasource(fileSystem, json)
		val loadedSyncData = datasource.loadSyncData(userId)

		assertEquals(syncData, loadedSyncData)
	}

	@Test
	fun `Update SyncData`() {
		val userId = 1L
		val syncDataPath = ProjectsFileSystemDatasource.getSyncDataPath(userId, fileSystem)
		fileSystem.createDirectories(syncDataPath.parent!!)

		val syncData = ProjectsSyncData(
			lastSync = Instant.fromEpochSeconds(123),
			deletedProjects = setOf(
				"Test Project 1",
				"Test Project 2",
			)
		)

		fileSystem.writeJson(syncDataPath, json, syncData)

		val datasource = ProjectsFileSystemDatasource(fileSystem, json)
		val loadedSyncData = datasource.updateSyncData(userId) { data ->
			data.copy(
				lastSync = Instant.fromEpochSeconds(456),
				deletedProjects = data.deletedProjects + "Test Project 3"
			)
		}

		val updatedSyncData = ProjectsSyncData(
			lastSync = Instant.fromEpochSeconds(456),
			deletedProjects = setOf(
				"Test Project 1",
				"Test Project 2",
				"Test Project 3",
			)
		)

		assertEquals(updatedSyncData, loadedSyncData)
	}
}