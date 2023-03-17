package com.darkrockstudios.apps.hammer.projects

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class ProjectsRepository(
    private val fileSystem: FileSystem
) {


    fun getRootDirectory(): Path = System.getProperty("user.home").toPath() / DATA_DIRECTORY

    fun getUserDirectory(userId: Long): Path {
        val dir = getRootDirectory()
        return dir / userId.toString()
    }

    companion object {
        private const val DATA_DIRECTORY = "user_data"
    }
}