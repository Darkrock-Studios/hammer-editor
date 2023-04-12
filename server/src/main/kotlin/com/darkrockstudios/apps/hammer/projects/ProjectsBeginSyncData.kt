package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.project.ProjectDefinition

data class ProjectsBeginSyncData(
	val syncId: String,
	val projects: Set<ProjectDefinition>,
	val deletedProjects: Set<String>,
)
