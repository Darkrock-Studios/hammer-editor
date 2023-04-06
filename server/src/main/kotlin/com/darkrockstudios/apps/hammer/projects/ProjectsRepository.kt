package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.getRootDataDirectory
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import okio.FileSystem
import okio.Path

class ProjectsRepository(
    private val fileSystem: FileSystem
) {
    fun getUserDirectory(userId: Long): Path {
        return getUserDirectory(userId, fileSystem)
    }

    suspend fun getProjects(userId: Long): List<ProjectDefinition> {
        val projectsDir = getUserDirectory(userId)
        return fileSystem.list(projectsDir)
            .filter { fileSystem.metadata(it).isDirectory }
            .map { path -> ProjectDefinition(path.name) }
    }

    companion object {
        private const val DATA_DIRECTORY = "user_data"
        fun getRootDirectory(fileSystem: FileSystem): Path = getRootDataDirectory(fileSystem) / DATA_DIRECTORY

        fun getUserDirectory(userId: Long, fileSystem: FileSystem): Path {
            val dir = getRootDirectory(fileSystem)
            return dir / userId.toString()
        }
    }
}