package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectsFileSystemDatasourceTest : BaseTest() {

	private lateinit var fileSystem: FileSystem
	private lateinit var json: Json

//	private fun createProjectDir() {
//		val userDir = ProjectsRepository.getUserDirectory(userId, fileSystem)
//		val projectDir = userDir / projectDefinition.name
//		fileSystem.createDirectories(projectDir)
//	}

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
}