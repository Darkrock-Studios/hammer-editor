package com.darkrockstudios.apps.hammer.base.http

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import kotlinx.serialization.Serializable

/** A group with no parent sits at depth 1. */
const val MAX_GROUP_NESTING_DEPTH = 8
const val MAX_GROUP_DESCRIPTION_LENGTH = 2000

@Serializable
data class ApiProjectDefinition(
	val name: String,
	val uuid: ProjectId,
	/** The group holding the project; null at the top level. */
	val groupId: GroupId? = null,
)

@Serializable
data class ApiGroupDefinition(
	val uuid: GroupId,
	val name: String,
	/** Null at the top level. */
	val parentId: GroupId? = null,
	val description: String? = null,
)

@Serializable
data class UpsertGroupResponse(
	/** Differs from the requested id when a same-name sibling was adopted. */
	val groupId: GroupId,
	val alreadyExisted: Boolean,
)

@Serializable
data class BeginProjectsSyncResponse(
	val syncId: String,
	val projects: Set<ApiProjectDefinition>,
	val deletedProjects: Set<ProjectId>,
	/**
	 * [com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeasStateHasher] hash of the
	 * server's live idea set, so a client in agreement can skip the ideas phase. Null from
	 * servers that predate story ideas — the client then runs the phase unconditionally.
	 */
	val ideasStateHash: String? = null,
	val groups: Set<ApiGroupDefinition> = emptySet(),
)

@Serializable
data class CreateProjectResponse(
	/** Differs from the requested id when the account already had a project with that name. */
	val projectId: ProjectId,
	val alreadyExisted: Boolean,
	/** Where the project actually lives; on adoption this is the existing project's group. */
	val groupId: GroupId? = null,
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
