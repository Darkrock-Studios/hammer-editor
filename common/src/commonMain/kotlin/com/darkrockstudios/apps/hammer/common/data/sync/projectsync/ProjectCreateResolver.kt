package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import io.github.aakira.napier.Napier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ServerCreateOutcome {
	/**
	 * The project is on the server as [projectId], which is now also its local id. [reminted] when
	 * the original id belonged to another account and a fresh one was used instead.
	 */
	data class Created(
		val projectId: ProjectId,
		val alreadyExisted: Boolean,
		val reminted: Boolean,
	) : ServerCreateOutcome

	/** This account deleted the project; its id is tombstoned and can never be created again. */
	data object DeletedElsewhere : ServerCreateOutcome

	data class Failed(val error: Throwable) : ServerCreateOutcome
}

/**
 * Creates a local project on the server under its own id, shared by account sync and project sync
 * so both resolve the server's answers the same way: a same-name project on the account is adopted,
 * an id held by another account is re-minted once, and a tombstoned id is reported, not retried.
 */
class ProjectCreateResolver(
	private val serverProjectsApi: ServerProjectsApi,
	private val projectsRepository: ProjectsRepository,
) {
	// The server holds one account sync session per install, and a second begin replaces the
	// first, so projects syncing in parallel must not open their sessions concurrently.
	private val sessionLock = Mutex()

	/** Creates [projectDef] within the open account sync session [syncId]. */
	suspend fun create(projectDef: ProjectDef, syncId: String): ServerCreateOutcome {
		var projectId = projectsRepository.getProjectId(projectDef)
		var reminted = false
		while (true) {
			val result = serverProjectsApi.createProject(projectDef.name, projectId, syncId)
			val error = result.exceptionOrNull()
			when {
				error == null -> {
					val response = result.getOrThrow()
					if (response.projectId != projectId) {
						Napier.i("'${projectDef.name}' adopted server project ${response.projectId.id}")
						projectsRepository.setProjectId(projectDef, response.projectId)
					}
					return ServerCreateOutcome.Created(response.projectId, response.alreadyExisted, reminted)
				}

				error.apiErrorCode() == ApiErrorCode.PROJECT_ID_TAKEN && !reminted -> {
					val newId = ProjectId.randomUUID()
					Napier.w("'${projectDef.name}' id ${projectId.id} is taken on the server, re-minted ${newId.id}")
					projectsRepository.setProjectId(projectDef, newId)
					projectId = newId
					reminted = true
				}

				error.apiErrorCode() == ApiErrorCode.PROJECT_DELETED ->
					return ServerCreateOutcome.DeletedElsewhere

				else -> return ServerCreateOutcome.Failed(error)
			}
		}
	}

	/** Creates [projectDef] in an account sync session of its own. */
	suspend fun createInOwnSession(projectDef: ProjectDef): ServerCreateOutcome = sessionLock.withLock {
		val begin = serverProjectsApi.beginProjectsSync()
		val syncId = begin.getOrElse { return ServerCreateOutcome.Failed(it) }.syncId
		try {
			create(projectDef, syncId)
		} finally {
			withContext(NonCancellable) { serverProjectsApi.endProjectsSync(syncId) }
		}
	}
}

private fun Throwable.apiErrorCode(): String? = (this as? HttpFailureException)?.error?.errorCode
