package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.getRootDocumentDirectory
import okio.FileSystem
import okio.Path.Companion.toPath

class ProjectsRepositoryOkio(
    private val fileSystem: FileSystem
) : ProjectsRepository() {

    init {
        val projectsDir = getProjectsDirectory().toOkioPath()
        if (!fileSystem.exists(projectsDir)) throw IllegalStateException("Projects dir does not exist")
    }

    override fun getProjectsDirectory(): HPath {
        val rootPath = getRootDocumentDirectory().toPath()
        val proj = PROJECTS_DIR.toPath()
        val projectsDir = rootPath.div(proj)

        if (!fileSystem.exists(projectsDir)) {
            fileSystem.createDirectory(projectsDir)
        }

        return projectsDir.toHPath()
    }

    override fun getProjects(projectsDir: HPath): List<Project> {
        val projPath = projectsDir.toOkioPath()
        return fileSystem.list(projPath)
            .filter { fileSystem.metadata(it).isDirectory }
            .map { path -> Project(path.name, path.toHPath()) }
    }

    override fun createProject(projectName: String): Boolean {
        return if (validateFileName(projectName)) {
            val projectsDir = getProjectsDirectory().toOkioPath()
            val newProjectDir = projectsDir.div(projectName)
            if (fileSystem.exists(newProjectDir)) {
                false
            } else {
                fileSystem.createDirectory(newProjectDir)
                true
            }
        } else {
            false
        }
    }
}