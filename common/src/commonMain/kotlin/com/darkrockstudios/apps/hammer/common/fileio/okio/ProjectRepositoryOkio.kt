package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import okio.FileSystem

class ProjectRepositoryOkio(
    private val fileSystem: FileSystem,
    projectsRepository: ProjectsRepository
) : ProjectRepository(projectsRepository) {

    override fun createEditor(projectDef: ProjectDef): ProjectEditorRepository {
        return ProjectEditorRepositoryOkio(projectDef, projectsRepository, fileSystem)
    }
}