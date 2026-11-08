package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.account.isActive
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectMemberDao

/**
 * Marks code that names a project's owner without requireProjectAccess(): account-scoped routes and
 * pages, where every project the caller can name is their own, and reads of a project row's user_id.
 */
@RequiresOptIn(
	message = "Project routes must take the owner from requireProjectAccess(); an unchecked OwnerId is only for the caller's own projects or a project row's user_id.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class UncheckedOwner

/** The account whose user id, rows and cipher secret hold a project's storage. */
@JvmInline
value class OwnerId private constructor(val id: Long) {
	companion object {
		fun of(project: Project) = OwnerId(project.user_id)

		@UncheckedOwner
		fun unchecked(userId: Long) = OwnerId(userId)
	}
}

data class ProjectAccess(val projectDef: ProjectDefinition, val owner: OwnerId, val isOwner: Boolean)

sealed interface ProjectAccessResult {
	data class Granted(val access: ProjectAccess) : ProjectAccessResult
	/** No project holds the id. */
	data object Unknown : ProjectAccessResult
	/** The project exists but the caller may not use it. */
	data object NotMember : ProjectAccessResult
}

class ProjectAccessResolver(
	private val projectDao: ProjectDao,
	private val projectMemberDao: ProjectMemberDao,
	private val accountDao: AccountDao,
	private val whiteListRepository: WhiteListRepository,
) {
	suspend fun resolve(callerId: Long, projectId: ProjectId): ProjectAccessResult {
		val project = projectDao.getProjectByUuid(projectId) ?: return ProjectAccessResult.Unknown
		val isOwner = project.user_id == callerId
		// The caller's own account was checked by authentication; a member also depends on the owner's.
		val allowed = isOwner || (projectMemberDao.isMember(project.id, callerId) && ownerIsActive(project))
		if (!allowed) return ProjectAccessResult.NotMember

		return ProjectAccessResult.Granted(
			ProjectAccess(
				projectDef = ProjectDefinition.wrap(project.name, project.uuid),
				owner = OwnerId.of(project),
				isOwner = isOwner,
			)
		)
	}

	private suspend fun ownerIsActive(project: Project): Boolean =
		accountDao.getAccount(project.user_id)?.isActive(whiteListRepository) == true
}
