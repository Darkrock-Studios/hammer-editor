package com.darkrockstudios.apps.hammer.common.data.projectrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectrepository.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectrepository.projecteditorrepository.ProjectEditorRepositoryOkio
import okio.FileSystem

class ProjectRepositoryOkio(
    private val fileSystem: FileSystem,
    projectsRepository: ProjectsRepository,
    private val projectEditorFactory: ProjectEditorFactory = Factory()
) : ProjectRepository(projectsRepository) {

    override fun createEditor(projectDef: ProjectDef): ProjectEditorRepository {
        return projectEditorFactory.createEditor(projectDef, projectsRepository, fileSystem)
    }

    companion object {
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
}

interface ProjectEditorFactory {
    fun createEditor(
        projectDef: ProjectDef,
        projectsRepository: ProjectsRepository,
        fileSystem: FileSystem
    ): ProjectEditorRepository
}