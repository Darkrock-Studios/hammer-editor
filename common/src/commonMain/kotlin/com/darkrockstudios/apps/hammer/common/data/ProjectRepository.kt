package com.darkrockstudios.apps.hammer.common.data

abstract class ProjectRepository(
    protected val projectsRepository: ProjectsRepository
) {

    private val projectEditors = mutableMapOf<Project, ProjectEditorRepository>()

    fun getProjectEditor(project: Project): ProjectEditorRepository {
        val existingEditor = projectEditors[project]
        return if (existingEditor != null) {
            existingEditor
        } else {
            val newEditor = createEditor(project)
            projectEditors[project] = newEditor
            newEditor
        }
    }

    fun clearEditorCache() = projectEditors.clear()

    protected abstract fun createEditor(project: Project): ProjectEditorRepository
}