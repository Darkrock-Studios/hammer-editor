package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
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

    override fun getProjects(projectsDir: HPath): List<ProjectDef> {
        val projPath = projectsDir.toOkioPath()
        return fileSystem.list(projPath)
            .filter { fileSystem.metadata(it).isDirectory }
            .map { path -> ProjectDef(path.name, path.toHPath()) }
    }

    override fun getProjectDirectory(projectName: String): HPath {
        val projectsDir = getProjectsDirectory().toOkioPath()
        val projectDir = projectsDir.div(projectName)
        return projectDir.toHPath()
    }

    override fun createProject(projectName: String): Boolean {
        val strippedName = projectName.trim()
        return if (validateFileName(strippedName)) {
            val projectsDir = getProjectsDirectory().toOkioPath()
            val newProjectDir = projectsDir.div(strippedName)
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

    override fun deleteProject(projectDef: ProjectDef): Boolean {
        val projectDir = getProjectDirectory(projectDef.name).toOkioPath()
        return if (fileSystem.exists(projectDir)) {
            fileSystem.deleteRecursively(projectDir)
            true
        } else {
            false
        }
    }
}