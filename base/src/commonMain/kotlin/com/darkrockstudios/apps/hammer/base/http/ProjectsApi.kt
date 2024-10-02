package com.darkrockstudios.apps.hammer.base.http

import com.darkrockstudios.apps.hammer.base.ProjectId
import kotlinx.serialization.Serializable

@Serializable
data class ApiProjectDefinition(
	val name: String,
	val uuid: ProjectId,
)

@Serializable
data class BeginProjectsSyncResponse(
	val syncId: String,
	val projects: Set<ApiProjectDefinition>,
	val deletedProjects: Set<ProjectId>,
)

@Serializable
data class CreateProjectResponse(
	val projectId: ProjectId,
	val alreadyExisted: Boolean,
)
