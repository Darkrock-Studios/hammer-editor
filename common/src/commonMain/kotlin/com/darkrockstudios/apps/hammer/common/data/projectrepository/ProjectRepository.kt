package com.darkrockstudios.apps.hammer.common.data.projectrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectrepository.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

abstract class ProjectRepository(
    protected val projectsRepository: ProjectsRepository
) {
    private val projectDefEditors = mutableMapOf<ProjectDef, ProjectEditorRepository>()

    fun numActiveEditors(): Int = projectDefEditors.size

    fun getProjectEditor(projectDef: ProjectDef): ProjectEditorRepository {
        val existingEditor = projectDefEditors[projectDef]
        return if (existingEditor != null) {
            existingEditor
        } else {
            val newEditor = createEditor(projectDef)
            newEditor.initializeProjectEditor()
            projectDefEditors[projectDef] = newEditor
            newEditor
        }
    }

    fun closeEditor(projectDef: ProjectDef) {
        projectDefEditors.remove(projectDef)?.close()
    }

    fun closeEditors() {
        projectDefEditors.values.forEach { it.close() }
        projectDefEditors.clear()
    }

    protected abstract fun createEditor(projectDef: ProjectDef): ProjectEditorRepository
}