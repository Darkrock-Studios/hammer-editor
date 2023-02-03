package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.projecteditor.metadata.ProjectMetadata
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath

class ProjectsRepositoryOkio(
    private val fileSystem: FileSystem,
    private val toml: Toml,
    globalSettingsRepository: GlobalSettingsRepository,
) : ProjectsRepository() {

    private var globalSettings = globalSettingsRepository.globalSettings

    init {
        projectsScope.launch {
            globalSettingsRepository.globalSettingsUpdates.collect { newSettings ->
                globalSettings = newSettings
            }
        }

        val projectsDir = getProjectsDirectory().toOkioPath()
        if (!fileSystem.exists(projectsDir)) throw IllegalStateException("Projects dir does not exist")
    }

    override fun getProjectsDirectory(): HPath {
        val projectsDir = globalSettings.projectsDirectory.toPath()

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

    override suspend fun loadMetadata(projectDef: ProjectDef): ProjectMetadata? {
        val path = ProjectEditorRepository.getMetadataPath(projectDef).toOkioPath()

        val metadata = try {
            val metadataText = fileSystem.read(path) {
                readUtf8()
            }
            toml.decodeFromString<ProjectMetadata>(metadataText)
        } catch (e: IOException) {
            Napier.e("Failed to project metadata")
            null
        }

        return metadata
    }
}