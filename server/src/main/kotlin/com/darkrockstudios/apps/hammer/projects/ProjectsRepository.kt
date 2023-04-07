package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.getRootDataDirectory
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectRepository
import com.darkrockstudios.apps.hammer.project.ProjectsSyncData
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class ProjectsRepository(
    private val fileSystem: FileSystem,
    private val json: Json
) {
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

    private fun getUserDirectory(userId: Long): Path {
        return getUserDirectory(userId, fileSystem)
    }

    suspend fun getProjects(userId: Long): Set<ProjectDefinition> {
        val projectsDir = getUserDirectory(userId)
        return fileSystem.list(projectsDir)
            .filter { fileSystem.metadata(it).isDirectory }
            .map { path -> ProjectDefinition(path.name) }
            .toSet()
    }

    fun getDeletedProjects(userId: Long): Set<String> {
        return loadSyncData(userId).deletedProjects
    }

    private fun loadSyncData(userId: Long): ProjectsSyncData {
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

    private fun saveSyncData(userId: Long, data: ProjectsSyncData) {
        val path = getSyncDataPath(userId)
        fileSystem.write(path) {
            val syncDataJson = json.encodeToString(data)
            writeUtf8(syncDataJson)
        }
    }

    private fun updateSyncData(userId: Long, action: (ProjectsSyncData) -> ProjectsSyncData): ProjectsSyncData {
        val data = loadSyncData(userId)
        val update = action(data)
        saveSyncData(userId, update)
        return update
    }

    private fun getSyncDataPath(userId: Long): Path = getUserDirectory(userId) / DATA_FILE

    fun deleteProject(userId: Long, projectName: String) {
        val projectDef = ProjectDefinition(projectName)
        val projectDir = ProjectRepository.getProjectDirectory(userId, projectDef, fileSystem)
        fileSystem.deleteRecursively(projectDir)

        updateSyncData(userId) { data ->
            data.copy(
                deletedProjects = data.deletedProjects + projectName
            )
        }
    }

    fun createProject(userId: Long, projectName: String) {
        updateSyncData(userId) { data ->
            data.copy(
                deletedProjects = data.deletedProjects - projectName
            )
        }
    }

    companion object {
        private const val DATA_DIRECTORY = "user_data"
        private const val DATA_FILE = "data.json"

        fun defaultData(userId: Long): ProjectsSyncData {
            return ProjectsSyncData(
                lastSync = Instant.DISTANT_PAST,
                deletedProjects = emptySet()
            )
        }

        fun getRootDirectory(fileSystem: FileSystem): Path = getRootDataDirectory(fileSystem) / DATA_DIRECTORY

        fun getUserDirectory(userId: Long, fileSystem: FileSystem): Path {
            val dir = getRootDirectory(fileSystem)
            return dir / userId.toString()
        }
    }
}