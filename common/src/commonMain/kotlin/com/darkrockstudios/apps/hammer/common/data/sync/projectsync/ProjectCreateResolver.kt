package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.AccountSyncDatasource
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import io.github.aakira.napier.Napier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ServerCreateOutcome {
	/**
	 * The project is on the server under its local id, which may have just been changed to the
	 * server's. [reminted] when the original id belonged to another account and a fresh one was
	 * used instead.
	 */
	data class Created(
		val alreadyExisted: Boolean,
		val reminted: Boolean,
	) : ServerCreateOutcome

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

	/** This account deleted the project; its id is tombstoned and can never be created again. */
	data object DeletedElsewhere : ServerCreateOutcome

	data class Failed(val error: Throwable) : ServerCreateOutcome
}

/**
 * Creates a local project on the server under its own id, shared by account sync and project sync
 * so both resolve the server's answers the same way: a same-name project on the account is adopted
 * unless another local project already holds its id, an id held by another account is re-minted
 * once, and a tombstoned id is reported, not retried. A created project's account sync baseline
 * records the placement the server answers with, which differs from the requested one when an
 * existing project was matched.
 */
class ProjectCreateResolver(
	private val serverProjectsApi: ServerProjectsApi,
	private val projectsRepository: ProjectsRepository,
	private val accountSyncDatasource: AccountSyncDatasource,
) {
	// The server holds one account sync session per install, and a second begin replaces the
	// first, so projects syncing in parallel must not open their sessions concurrently.
	private val sessionLock = Mutex()

	/**
	 * Creates [projectDef] within the open account sync session [syncId], in [groupId] or the top
	 * level. [groupId] must be a group the server holds.
	 */
	suspend fun create(projectDef: ProjectDef, syncId: String, groupId: GroupId? = null): ServerCreateOutcome =
		create(projectDef, syncId, groupId, mayChangeId = true)

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
							Napier.w(
								"'${def.name}' not adopting server project ${response.projectId.id}: " +
									"'${holder.name}' holds it"
							)
							return ServerCreateOutcome.AdoptConflict(holder)
						}
						if (!mayChangeId) {
							Napier.w(
								"'${def.name}' matches server project ${response.projectId.id}: " +
									"needs account sync"
							)
							return ServerCreateOutcome.NeedsAccountSync
						}
						Napier.i("'${def.name}' adopted server project ${response.projectId.id}")
						projectsRepository.setProjectId(def, response.projectId)
					}
					recordBaseline(response.projectId, ProjectBaseline(def.name, response.groupId))
					return ServerCreateOutcome.Created(response.alreadyExisted, reminted)
				}

				error.apiErrorCode() == ApiErrorCode.PROJECT_ID_TAKEN && !reminted -> {
					if (!mayChangeId) {
						Napier.w("'${def.name}' id ${def.id.id} is taken on the server: needs account sync")
						return ServerCreateOutcome.NeedsAccountSync
					}
					val newId = ProjectId.randomUUID()
					Napier.w("'${def.name}' id ${def.id.id} is taken on the server, re-minted ${newId.id}")
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
	suspend fun createInOwnSession(projectDef: ProjectDef): ServerCreateOutcome = sessionLock.withLock {
		val begin = serverProjectsApi.beginProjectsSync()
		val syncId = begin.getOrElse { return ServerCreateOutcome.Failed(it) }.syncId
		try {
			create(projectDef, syncId, groupId = null, mayChangeId = false)
		} finally {
			withContext(NonCancellable) { serverProjectsApi.endProjectsSync(syncId) }
		}
	}

	private suspend fun recordBaseline(projectId: ProjectId, baseline: ProjectBaseline) {
		accountSyncDatasource.update { it.copy(projectBaselines = it.projectBaselines + (projectId to baseline)) }
	}
}

internal fun Throwable.apiErrorCode(): String? = (this as? HttpFailureException)?.error?.errorCode
