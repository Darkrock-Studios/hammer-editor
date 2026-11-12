package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.GetInvitesForEmail
import com.darkrockstudios.apps.hammer.Project_invite
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class ProjectInviteDao(database: Database) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.projectInviteQueries
	private val memberQueries = database.serverDatabase.projectMemberQueries
	private val accessQueries = database.serverDatabase.projectAccessQueries

	enum class InsertResult { INSERTED, ALREADY_INVITED, FULL }

	/**
	 * Refuses once members plus pending invites reach [maxPeople]. The project row is locked for the
	 * transaction so two concurrent invites cannot both pass the count.
	 */
	suspend fun insertInvite(
		projectRowId: Long,
		email: String,
		invitedBy: Long,
		maxPeople: Int,
	): InsertResult = withContext(ioDispatcher) {
		queries.transactionWithResult {
			accessQueries.lockProjectRow(projectRowId).executeAsOneOrNull()
			if (queries.isInvited(projectRowId, email).executeAsOne()) return@transactionWithResult InsertResult.ALREADY_INVITED

			val people = memberQueries.countMembers(projectRowId).executeAsOne() +
				queries.countInvites(projectRowId).executeAsOne()
			if (people >= maxPeople) return@transactionWithResult InsertResult.FULL

			queries.insertInvite(projectRowId, email, invitedBy)
			InsertResult.INSERTED
		}
	}

	suspend fun getInvites(projectRowId: Long): List<Project_invite> = withContext(ioDispatcher) {
		queries.getInvitesForProject(projectRowId).executeAsList()
	}

	/** False when the project has no invite [inviteId]. */
	suspend fun deleteInvite(inviteId: Long, projectRowId: Long): Boolean = withContext(ioDispatcher) {
		queries.deleteInvite(inviteId, projectRowId).value > 0
	}

	suspend fun findInvite(inviteId: Long, email: String): Project_invite? = withContext(ioDispatcher) {
		queries.getInviteForEmail(inviteId, email).executeAsOneOrNull()
	}

	suspend fun getInvitesForEmail(email: String): List<GetInvitesForEmail> = withContext(ioDispatcher) {
		queries.getInvitesForEmail(email).executeAsList()
	}

	/** False when no invite [inviteId] is addressed to [email]. */
	suspend fun deleteInviteForEmail(inviteId: Long, email: String): Boolean = withContext(ioDispatcher) {
		queries.deleteInviteForEmail(inviteId, email).value > 0
	}

	/** Turns the invite into a membership; null when no invite [inviteId] is addressed to [email]. */
	suspend fun acceptInvite(inviteId: Long, email: String, userId: Long): Project_invite? =
		withContext(ioDispatcher) {
			queries.transactionWithResult {
				val invite = queries.getInviteForEmail(inviteId, email).executeAsOneOrNull()
					?: return@transactionWithResult null
				memberQueries.insertMember(invite.project_id, userId)
				queries.deleteInvite(invite.id, invite.project_id)
				invite
			}
		}
}
