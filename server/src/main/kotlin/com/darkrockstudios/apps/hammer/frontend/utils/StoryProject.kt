package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.OwnerId
import com.darkrockstudios.apps.hammer.project.ProjectAccessResolver
import com.darkrockstudios.apps.hammer.project.ProjectAccessResult
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import kotlin.time.Instant

/** A project the signed-in account owns or co-authors. [ownerName] is set for a co-author only. */
data class StoryProject(
	val name: String,
	val uuid: String,
	val lastSync: Instant,
	val owner: OwnerId,
	val isOwner: Boolean,
	val ownerName: String?,
) {
	val projectId: ProjectId get() = ProjectId(uuid)
	val projectDef: ProjectDefinition get() = ProjectDefinition(name = name, uuid = projectId)
	val segment: String get() = ProjectName.projectSegment(name, uuid)
}

/**
 * Resolves a `/story/{segment}` project among the ones [userId] owns, then the ones they co-author.
 * Owner-only routes keep using [findProjectByUrlSegment], which never finds a shared project.
 */
suspend fun findStoryProject(
	projectsRepository: ProjectsRepository,
	projectAccessResolver: ProjectAccessResolver,
	userId: Long,
	segment: String,
): StoryProject? {
	projectsRepository.findProjectByUrlSegment(userId, segment)?.let { owned ->
		val access = (projectAccessResolver.resolve(userId, ProjectId(owned.uuid)) as? ProjectAccessResult.Granted)
			?.access ?: return null
		return StoryProject(owned.name, owned.uuid, owned.lastSync, access.owner, isOwner = true, ownerName = null)
	}

	val id = ProjectName.idFromSegment(segment)
	val shared = projectAccessResolver.memberProjects(userId)
		.find { ProjectName.shortId(it.projectDef.uuid.id) == id } ?: return null
	return StoryProject(
		name = shared.projectDef.name,
		uuid = shared.projectDef.uuid.id,
		lastSync = shared.lastSync,
		owner = shared.owner,
		isOwner = false,
		ownerName = shared.ownerName,
	)
}
