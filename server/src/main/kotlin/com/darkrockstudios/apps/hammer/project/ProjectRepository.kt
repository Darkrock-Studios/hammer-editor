package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.getRootDataDirectory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectRepository(
    private val fileSystem: FileSystem,
    private val json: Json,
) {
    fun getRootDirectory(): Path = getRootDataDirectory(fileSystem) / DATA_DIRECTORY

    fun getUserDirectory(userId: Long): Path {
        val dir = getRootDirectory()
        return dir / userId.toString()
    }

    private fun getProjectsFs(userId: Long): FileSystem {
        val userDir = getUserDirectory(userId)
        val projectsFile = userDir / PROJECT_FILE
        return fileSystem.openZip(projectsFile)
    }

    fun createUserData(userId: Long) {
        val userDir = getUserDirectory(userId)

        fileSystem.createDirectories(userDir)

        val dataFile = userDir / DATA_FILE
        val data = defaultData(userId)
        val dataJson = json.encodeToString(data)
        fileSystem.write(dataFile) {
            writeUtf8(dataJson)
        }

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
    }

    fun userDataExists(userId: Long): Boolean {
        val userDir = getUserDirectory(userId)
        return fileSystem.exists(userDir)
    }

    fun hasProject(id: Long, projectName: String): Boolean {
        val zipFs = getProjectsFs(id)
        val projectDir = projectName.toPath()
        return zipFs.exists(projectDir)
    }

    companion object {
        private const val DATA_DIRECTORY = "user_data"
        private const val PROJECT_FILE = "projects.zip"
        private const val DATA_FILE = "data.json"

        fun defaultData(userId: Long): SyncData {
            return SyncData()
        }
    }
}