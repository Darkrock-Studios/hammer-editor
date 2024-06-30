package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.project.ProjectsSyncData
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository.Companion.defaultData
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class ProjectsFileSystemDatasource(
	private val fileSystem: FileSystem,
	private val json: Json,
) : ProjectsDatasource {

	override fun createUserData(userId: Long) {
		val userDir = getUserDirectory(userId)

		fileSystem.createDirectories(userDir)

		val dataFile = userDir / DATA_FILE
		val data = defaultData(userId)
		val dataJson = json.encodeToString(data)
		fileSystem.write(dataFile) {
			writeUtf8(dataJson)
		}

		/*
		val projectsFile = userDir / PROJECT_FILE
		// Create the zip file with a place holder entry
		//val zipFs = fileSystem.openZip(projectsFile)
		val zipFile = File(projectsFile.toString())
		val out = ZipOutputStream(FileOutputStream(zipFile))
		val e = ZipEntry(".")
		out.putNextEntry(e)
		val placeHolderData = "\n".toByteArray()
		out.write(placeHolderData, 0, placeHolderData.size)
		out.closeEntry()
		out.close()
		*/
	}

	override fun createProject(userId: Long, projectName: String) {
		val projectDef = ProjectDefinition(projectName)
		val projectDir = ProjectRepository.getProjectDirectory(userId, projectDef, fileSystem)
		fileSystem.createDirectories(projectDir)
	}

	override fun deleteProject(userId: Long, projectName: String): Result<Unit> {
		val projectDef = ProjectDefinition(projectName)
		val projectDir = ProjectRepository.getProjectDirectory(userId, projectDef, fileSystem)
		fileSystem.deleteRecursively(projectDir)

		updateSyncData(userId) { data ->
			data.copy(
				deletedProjects = data.deletedProjects + projectName
			)
		}

		return Result.success(Unit)
	}

	override fun updateSyncData(
		userId: Long,
		action: (ProjectsSyncData) -> ProjectsSyncData
	): ProjectsSyncData {
		val data = loadSyncData(userId)
		val update = action(data)
		saveSyncData(userId, update)
		return update
	}

	override fun saveSyncData(userId: Long, data: ProjectsSyncData) {
		val path = getSyncDataPath(userId)
		fileSystem.write(path) {
			val syncDataJson = json.encodeToString(data)
			writeUtf8(syncDataJson)
		}
	}

	override fun getProjects(userId: Long): Set<ProjectDefinition> {
		val projectsDir = getUserDirectory(userId)
		return fileSystem.list(projectsDir)
			.filter { fileSystem.metadata(it).isDirectory }
			.filter { it.name.startsWith('.').not() }
			.map { path -> ProjectDefinition(path.name) }
			.toSet()
	}

	override fun loadSyncData(userId: Long): ProjectsSyncData {
		val path = getSyncDataPath(userId)
		return if (fileSystem.exists(path)) {
			fileSystem.read(path) {
				val syncDataJson = readUtf8()
				json.decodeFromString(syncDataJson)
			}
		} else {
			val data = defaultData(userId)
			saveSyncData(userId, data)
			data
		}
	}

	private fun getUserDirectory(userId: Long): Path {
		return getUserDirectory(userId, fileSystem)
	}

	private fun getSyncDataPath(userId: Long): Path = getUserDirectory(userId) / DATA_FILE

	companion object {
		private const val DATA_DIRECTORY = "user_data"
		private const val DATA_FILE = "syncData.json"

		fun getRootDirectory(fileSystem: FileSystem): Path =
			getRootDataDirectory(fileSystem) / DATA_DIRECTORY

		fun getUserDirectory(userId: Long, fileSystem: FileSystem): Path {
			val dir = getRootDirectory(fileSystem)
			return dir / userId.toString()
		}

		fun getSyncDataPath(userId: Long, fileSystem: FileSystem): Path =
			getUserDirectory(userId, fileSystem) / DATA_FILE
	}
}