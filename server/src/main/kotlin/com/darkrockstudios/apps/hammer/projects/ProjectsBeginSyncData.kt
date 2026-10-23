package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition

data class ProjectsBeginSyncData(
	val syncId: String,
	val projects: Set<ProjectDefinition>,
	val deletedProjects: Set<ProjectId>,
	val groups: Set<ProjectGroup> = emptySet(),
	/** Only projects inside a group appear. */
	val projectPlacements: Map<ProjectId, GroupId> = emptyMap(),
)
