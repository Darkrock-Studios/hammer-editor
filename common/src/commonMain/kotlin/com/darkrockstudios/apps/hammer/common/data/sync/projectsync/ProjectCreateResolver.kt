package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSessionLock
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncDatasource
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface ServerCreateOutcome {
	/**
	 * The project is on the server as [projectId], its local id, which may have just been changed to
	 * the server's. [name] and [groupId] are what its account sync baseline records: [groupId] is
	 * the server's placement, which differs from the requested one when an existing project was
	 * matched. [reminted] when the original id belonged to another account and a fresh one was used
	 * instead.
	 */
	data class Created(
		val projectId: ProjectId,
		val name: String,
		val groupId: GroupId?,
		val alreadyExisted: Boolean,
		val reminted: Boolean,
	) : ServerCreateOutcome {
		val baseline: ProjectBaseline get() = ProjectBaseline(name, groupId)
	}

	/**
	 * The server matched the project by name to a server project another local project, [heldBy],
	 * already syncs with: usually a rename of [heldBy] that has not reached the server yet. Nothing
	 * was adopted; the create is retried on a later sync.
	 */
	data class AdoptConflict(val heldBy: ProjectDef) : ServerCreateOutcome

	/**
	 * Only from project sync: the server's answer would change the project's id, which only account
	 * sync may do. Nothing was changed locally.
	 */
	data object NeedsAccountSync : ServerCreateOutcome

	/** The project belongs to another account that shared it with this one; nothing was sent. */
	data object SharedProject : ServerCreateOutcome

	/** This account deleted the project; its id is tombstoned and can never be created again. */
	data object DeletedElsewhere : ServerCreateOutcome

	data class Failed(val error: Throwable) : ServerCreateOutcome
}

/**
 * Creates a local project on the server under its own id, shared by account sync and project sync
 * so both resolve the server's answers the same way: a same-name project on the account is adopted
 * unless another local project already holds its id, an id held by another account is re-minted
 * once, and a tombstoned id is reported, not retried. A project shared with this account is never
 * created, whatever the server would answer: that would fork the owner's book. Within account sync
 * the caller records the created project's baseline; [createInOwnSession] stores it itself.
 */
class ProjectCreateResolver(
	private val serverProjectsApi: ServerProjectsApi,
	private val projectsRepository: ProjectsRepository,
	private val accountSyncDatasource: AccountSyncDatasource,
	private val sessionLock: AccountSessionLock,
) {
	/**
	 * Creates [projectDef] within the open account sync session [syncId], in [groupId] or the top
	 * level. [groupId] must be a group the server holds.
	 */
	suspend fun create(projectDef: ProjectDef, syncId: String, groupId: GroupId? = null): ServerCreateOutcome {
		if (isShared(projectDef)) return ServerCreateOutcome.SharedProject
		return create(projectDef, syncId, groupId, mayChangeId = true)
	}

	private suspend fun create(
		projectDef: ProjectDef,
		syncId: String,
		groupId: GroupId?,
		mayChangeId: Boolean,
	): ServerCreateOutcome {
		var def = projectDef
		var reminted = false
		while (true) {
			val result = serverProjectsApi.createProject(def.name, def.id, syncId, groupId)
			val error = result.exceptionOrNull()
			when {
				error == null -> {
					val response = result.getOrThrow()
					if (response.projectId != def.id) {
						// Adopting would leave two local projects sharing an id.
						val holder = projectsRepository.findProject(response.projectId)
						if (holder != null && holder.name != def.name) {
							Logger.w(
								"'${def.name}' not adopting server project ${response.projectId.id}: " +
									"'${holder.name}' holds it"
							)
							return ServerCreateOutcome.AdoptConflict(holder)
						}
						if (!mayChangeId) {
							Logger.w(
								"'${def.name}' matches server project ${response.projectId.id}: " +
									"needs account sync"
							)
							return ServerCreateOutcome.NeedsAccountSync
						}
						Logger.i("'${def.name}' adopted server project ${response.projectId.id}")
						projectsRepository.setProjectId(def, response.projectId)
					}
					return ServerCreateOutcome.Created(
						projectId = response.projectId,
						name = def.name,
						groupId = response.groupId,
						alreadyExisted = response.alreadyExisted,
						reminted = reminted,
					)
				}

				error.isIdHeldElsewhere() && !reminted -> {
					if (!mayChangeId) {
						Logger.w("'${def.name}' id ${def.id.id} is taken on the server: needs account sync")
						return ServerCreateOutcome.NeedsAccountSync
					}
					val newId = ProjectId.randomUUID()
					Logger.w("'${def.name}' id ${def.id.id} is taken on the server, re-minted ${newId.id}")
					def = projectsRepository.setProjectId(def, newId)
					reminted = true
				}

				error.apiErrorCode() == ApiErrorCode.PROJECT_DELETED ->
					return ServerCreateOutcome.DeletedElsewhere

				else -> return ServerCreateOutcome.Failed(error)
			}
		}
	}

	/** Creates [projectDef] in an account sync session of its own, never changing its id. */
	suspend fun createInOwnSession(projectDef: ProjectDef): ServerCreateOutcome {
		if (isShared(projectDef)) return ServerCreateOutcome.SharedProject
		return createInNewSession(projectDef)
	}

	private suspend fun createInNewSession(projectDef: ProjectDef): ServerCreateOutcome = sessionLock.withSession {
		val begin = serverProjectsApi.beginProjectsSync()
		val syncId = begin.getOrElse { return ServerCreateOutcome.Failed(it) }.syncId
		try {
			create(projectDef, syncId, groupId = null, mayChangeId = false).also { outcome ->
				if (outcome is ServerCreateOutcome.Created) recordBaseline(outcome)
			}
		} finally {
			withContext(NonCancellable) { serverProjectsApi.endProjectsSync(syncId) }
		}
	}

	private fun isShared(projectDef: ProjectDef): Boolean = projectsRepository.getOwner(projectDef) != null

	private suspend fun recordBaseline(created: ServerCreateOutcome.Created) {
		accountSyncDatasource.update {
			it.copy(projectBaselines = it.projectBaselines + (created.projectId to created.baseline))
		}
	}
}

internal fun Throwable.apiErrorCode(): String? = (this as? HttpFailureException)?.error?.errorCode

/**
 * Another account's project holds the id. The server answers `not_member` for it; `project_id_taken`
 * is left only for an insert lost to a concurrent delete.
 */
private fun Throwable.isIdHeldElsewhere(): Boolean =
	apiErrorCode() == ApiErrorCode.PROJECT_ID_TAKEN || apiErrorCode() == ApiErrorCode.NOT_MEMBER
