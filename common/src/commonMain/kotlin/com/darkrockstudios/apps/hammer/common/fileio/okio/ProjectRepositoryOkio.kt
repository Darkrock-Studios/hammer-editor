package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import okio.FileSystem

class ProjectRepositoryOkio(
    private val fileSystem: FileSystem,
    projectsRepository: ProjectsRepository,
    private val projectEditorFactory: ProjectEditorFactory = Factory()
) : ProjectRepository(projectsRepository) {

    override fun createEditor(projectDef: ProjectDef): ProjectEditorRepository {
        return projectEditorFactory.createEditor(projectDef, projectsRepository, fileSystem)
    }

    private class Factory : ProjectEditorFactory {
        override fun createEditor(
            projectDef: ProjectDef,
            projectsRepository: ProjectsRepository,
            fileSystem: FileSystem
        ): ProjectEditorRepository {
            return ProjectEditorRepositoryOkio(projectDef, projectsRepository, fileSystem)
        }
    }
}

interface ProjectEditorFactory {
    fun createEditor(
        projectDef: ProjectDef,
        projectsRepository: ProjectsRepository,
        fileSystem: FileSystem
    ): ProjectEditorRepository
}