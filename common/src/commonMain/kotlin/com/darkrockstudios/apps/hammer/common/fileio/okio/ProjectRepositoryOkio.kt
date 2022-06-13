package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.data.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.ProjectsRepository
import okio.FileSystem

class ProjectRepositoryOkio(
    private val fileSystem: FileSystem,
    projectsRepository: ProjectsRepository
) : ProjectRepository(projectsRepository) {

    override fun createEditor(project: Project): ProjectEditorRepository {
        return ProjectEditorRepositoryOkio(project, projectsRepository, fileSystem)
    }
}