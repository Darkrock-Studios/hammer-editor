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

/** One project's locally-computed project-wide content hash, sent to the change probe. */
@Serializable
data class ProjectHashItem(
	val projectId: ProjectId,
	val hash: String,
)

/** Batched pre-sync probe: the client's current project-wide hash for each eligible project. */
@Serializable
data class ProjectsSyncProbeRequest(
	val projects: List<ProjectHashItem>,
)

/** Projects whose server-side hash matches the client's — safe to skip syncing this session. */
@Serializable
data class ProjectsSyncProbeResponse(
	val unchangedProjects: Set<ProjectId>,
)
