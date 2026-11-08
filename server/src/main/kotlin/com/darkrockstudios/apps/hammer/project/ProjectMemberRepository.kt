package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.validate.EmailValidator
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectInviteDao
import com.darkrockstudios.apps.hammer.database.ProjectMemberDao
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlin.time.Instant

data class ProjectMemberSummary(val userId: Long, val penName: String?, val joinedAt: Instant)

data class ProjectInviteSummary(val id: Long, val email: String, val createdAt: Instant)

/** An invite addressed to the viewing account. */
data class ReceivedInvite(
	val id: Long,
	val projectId: ProjectId,
	val projectName: String,
	val ownerId: Long,
	val ownerName: String?,
	val createdAt: Instant,
)

/**
 * Who may sync a project besides its owner. Owner-only operations take the caller's id and refuse
 * anyone but the project's owner.
 */
class ProjectMemberRepository(
	private val projectDao: ProjectDao,
	private val projectMemberDao: ProjectMemberDao,
	private val projectInviteDao: ProjectInviteDao,
	private val accountDao: AccountDao,
	private val projectSessions: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>,
) {
	suspend fun listMembers(callerId: Long, projectId: ProjectId): SResult<List<ProjectMemberSummary>> {
		val project = ownedProject(callerId, projectId).getOrElse { return SResult.failure(it) }
		return SResult.success(
			projectMemberDao.getMembers(project.id).map { ProjectMemberSummary(it.user_id, it.pen_name, it.joined_at) }
		)
	}

	/** Idempotent. */
	suspend fun removeMember(callerId: Long, projectId: ProjectId, memberId: Long): SResult<Unit> {
		val project = ownedProject(callerId, projectId).getOrElse { return SResult.failure(it) }
		leave(project, memberId)
		return SResult.success()
	}

	/**
	 * The same success whether or not an account holds [email], so inviting confirms nothing.
	 * Inviting an address that already has a pending invite succeeds without a second row.
	 */
	suspend fun createInvite(callerId: Long, projectId: ProjectId, email: String): SResult<Unit> {
		val project = ownedProject(callerId, projectId).getOrElse { return SResult.failure(it) }
		val address = email.trim().lowercase()
		if (!EmailValidator.validate(address)) return refused(InviteRefusal.INVALID_EMAIL)

		val owner = accountDao.getAccount(callerId) ?: return SResult.failure(ProjectNotFound(projectId))
		if (owner.pen_name == null) return refused(InviteRefusal.OWNER_PEN_NAME_REQUIRED)
		if (owner.email.equals(address, ignoreCase = true)) return refused(InviteRefusal.OWN_ADDRESS)
		if (projectMemberDao.isMemberEmail(project.id, address)) return refused(InviteRefusal.ALREADY_MEMBER)

		return when (projectInviteDao.insertInvite(project.id, address, callerId, MAX_CO_AUTHORS)) {
			ProjectInviteDao.InsertResult.FULL -> refused(InviteRefusal.LIMIT_REACHED)
			ProjectInviteDao.InsertResult.INSERTED,
			ProjectInviteDao.InsertResult.ALREADY_INVITED -> SResult.success()
		}
	}

	suspend fun listInvites(callerId: Long, projectId: ProjectId): SResult<List<ProjectInviteSummary>> {
		val project = ownedProject(callerId, projectId).getOrElse { return SResult.failure(it) }
		return SResult.success(
			projectInviteDao.getInvites(project.id).map { ProjectInviteSummary(it.id, it.email, it.created_at) }
		)
	}

	/** Idempotent. */
	suspend fun revokeInvite(callerId: Long, projectId: ProjectId, inviteId: Long): SResult<Unit> {
		val project = ownedProject(callerId, projectId).getOrElse { return SResult.failure(it) }
		projectInviteDao.deleteInvite(inviteId, project.id)
		return SResult.success()
	}

	suspend fun receivedInvites(userId: Long): List<ReceivedInvite> {
		val account = accountDao.getAccount(userId) ?: return emptyList()
		return projectInviteDao.getInvitesForEmail(account.email).map {
			ReceivedInvite(
				id = it.id,
				projectId = ProjectId(it.project_uuid),
				projectName = it.project_name,
				ownerId = it.owner_id,
				ownerName = it.owner_name,
				createdAt = it.created_at,
			)
		}
	}

	/** Requires a pen name, which is how the owner sees the new co-author. */
	suspend fun acceptInvite(userId: Long, inviteId: Long): SResult<ProjectId> {
		val account = accountDao.getAccount(userId) ?: return SResult.failure(InviteNotFoundException(inviteId))
		if (account.pen_name == null) return SResult.failure(PenNameRequiredException())

		val invite = projectInviteDao.findInvite(inviteId, account.email)
			?: return SResult.failure(InviteNotFoundException(inviteId))
		val project = projectDao.getProjectByRowId(invite.project_id)
		if (project == null || project.user_id == userId) return SResult.failure(InviteNotFoundException(inviteId))

		projectInviteDao.acceptInvite(inviteId, account.email, userId)
			?: return SResult.failure(InviteNotFoundException(inviteId))
		return SResult.success(ProjectId(project.uuid))
	}

	/** Idempotent. */
	suspend fun declineInvite(userId: Long, inviteId: Long) {
		val account = accountDao.getAccount(userId) ?: return
		projectInviteDao.deleteInviteForEmail(inviteId, account.email)
	}

	/**
	 * Removes [userId]'s membership and ends any project session they hold on it, which would
	 * otherwise refuse the owner's delete, rename and move until it expired. False when [userId]
	 * was not a member.
	 */
	suspend fun leave(project: Project, userId: Long): Boolean {
		if (!projectMemberDao.removeMember(project.id, userId)) return false
		val projectId = ProjectId(project.uuid)
		projectSessions.terminateSessions { it.userId == userId && it.projectDef.uuid == projectId }
		return true
	}

	/** [userId] leaving [projectId] themselves. False when they were not a member. */
	suspend fun leaveProject(userId: Long, projectId: ProjectId): Boolean {
		val project = projectDao.getProjectByUuid(projectId) ?: return false
		return leave(project, userId)
	}

	private suspend fun ownedProject(callerId: Long, projectId: ProjectId): Result<Project> {
		val project = projectDao.getProjectByUuid(projectId) ?: return Result.failure(ProjectNotFound(projectId))
		return if (project.user_id == callerId) Result.success(project) else Result.failure(NotOwnerException(projectId))
	}

	private fun refused(reason: InviteRefusal): SResult<Unit> = SResult.failure(InviteRefusedException(reason))

	companion object {
		/** Members plus pending invites. */
		const val MAX_CO_AUTHORS = 5
	}
}
