package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.getRootDataDirectory
import com.darkrockstudios.apps.hammer.project.synchronizers.SceneSynchronizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectRepository(
	private val fileSystem: FileSystem,
	private val json: Json,
	private val sceneSynchronizer: SceneSynchronizer
) {
	fun getRootDirectory(): Path = getRootDirectory(fileSystem)
	fun getUserDirectory(userId: Long): Path = getUserDirectory(userId, fileSystem)
	fun getEntityDirectory(userId: Long, projectDef: ProjectDefinition): Path =
		getEntityDirectory(userId, projectDef, fileSystem)

	fun createUserData(userId: Long) {
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

	fun userDataExists(userId: Long): Boolean {
		val userDir = getUserDirectory(userId)
		return fileSystem.exists(userDir)
	}

	fun hasProject(userId: Long, projectName: String): Boolean {
		val userDir = getUserDirectory(userId)
		val projectDir = userDir / projectName

		return fileSystem.exists(projectDir)
	}

	fun saveEntity(userId: Long, projectDef: ProjectDefinition, entity: ProjectEntity): Result<Boolean> {
		ensureEntityDir(userId, projectDef, entity)

		val result = when (entity) {
			is ProjectEntity.SceneEntity -> sceneSynchronizer.saveScene(userId, projectDef, entity)
		}
		return result
	}

	private fun ensureEntityDir(userId: Long, projectDef: ProjectDefinition, entity: ProjectEntity) {
		val entityDir = getEntityDirectory(userId, projectDef, fileSystem)
		fileSystem.createDirectories(entityDir)
	}

	companion object {
		private const val DATA_DIRECTORY = "user_data"
		private const val DATA_FILE = "data.json"

		const val ENTITY_DIR = "entities"

		fun defaultData(userId: Long): SyncData {
			return SyncData()
		}

		fun getRootDirectory(fileSystem: FileSystem): Path = getRootDataDirectory(fileSystem) / DATA_DIRECTORY

		fun getUserDirectory(userId: Long, fileSystem: FileSystem): Path {
			val dir = getRootDirectory(fileSystem)
			return dir / userId.toString()
		}

		fun getProjectDirectory(userId: Long, projectDef: ProjectDefinition, fileSystem: FileSystem): Path {
			val dir = getUserDirectory(userId, fileSystem)
			return dir / projectDef.name
		}

		fun getEntityDirectory(userId: Long, projectDef: ProjectDefinition, fileSystem: FileSystem): Path {
			val dir = getProjectDirectory(userId, projectDef, fileSystem)
			return dir / ENTITY_DIR
		}
	}
}