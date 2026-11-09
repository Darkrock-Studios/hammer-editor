package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.NotMemberException
import com.darkrockstudios.apps.hammer.project.NotOwnerException
import com.darkrockstudios.apps.hammer.project.ProjectMemberRepository
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult

/**
 * The owner's delete removes the project and tombstones it for the owner alone. A co-author's
 * delete is a leave and writes no tombstone, so the ex-member can still upload their copy.
 */
class DeleteProjectUseCase(
	private val projectsRepository: ProjectsRepository,
	private val projectMemberRepository: ProjectMemberRepository,
) {
	suspend operator fun invoke(userId: Long, syncId: String, projectId: ProjectId): SResult<Unit> {
		val result = projectsRepository.deleteProject(userId, syncId, projectId)
		if (result !is ServerResult.Failure || result.exception !is NotOwnerException) return result
		return if (projectMemberRepository.leaveProject(userId, projectId)) {
			SResult.success()
		} else {
			SResult.failure(NotMemberException(projectId))
		}
	}
}
