package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.base.validate.validateProjectName
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.*
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Clock

class ProjectsRepository(
	private val clock: Clock,
	private val projectsDatasource: ProjectsDatasource,
	private val projectEntityDatasource: ProjectEntityDatasource,
	private val projectDao: ProjectDao,
) {
	private val syncSessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession> by inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECTS_SYNC_MANAGER)
	)

	suspend fun createUserData(userId: Long) = projectsDatasource.createUserData(userId)

	/** The project's database row ID, which reviews and kudos are keyed by. */
	suspend fun findProjectRowId(userId: Long, projectId: ProjectId): Long? =
		projectDao.getProjectIdOrNull(userId, projectId)

	suspend fun getProjectByRowId(rowId: Long): Project? = projectDao.getProjectByRowId(rowId)

	suspend fun getProjectsWithSyncDate(userId: Long): List<ProjectWithSyncDate> {
		return projectsDatasource.getProjectsWithSyncDate(userId)
	}

	suspend fun getProjectsWithSyncDate(userId: Long, page: Int, pageSize: Int): List<ProjectWithSyncDate> {
		return projectsDatasource.getProjectsWithSyncDate(userId, page, pageSize)
	}

	suspend fun getProjectWithSyncDate(userId: Long, projectId: ProjectId): ProjectWithSyncDate? {
		return projectsDatasource.getProjectsWithSyncDate(userId)
			.find { it.uuid == projectId.id }
	}

	suspend fun getProjectByName(userId: Long, projectName: String): ProjectWithSyncDate? {
		return projectsDatasource.findProjectByNameWithSyncDate(userId, projectName)
	}

	suspend fun getProjectsCount(userId: Long): Long {
		return projectsDatasource.getProjectsCount(userId)
	}

	suspend fun getMostRecentSyncForUser(userId: Long): kotlin.time.Instant? {
		return projectsDatasource.getMostRecentSyncForUser(userId)
	}

	suspend fun beginProjectsSync(
		userId: Long,
		installId: String? = null,
	): SResult<ProjectsBeginSyncData> {
		// Atomically claim the slot, reclaiming only this install's own stale session
		val newSyncId = syncSessionManager.claimSession(
			userId,
			canReplace = { it.installId == installId },
		) { user, sync ->
			ProjectsSynchronizationSession(
				userId = user,
				started = clock.now(),
				syncId = sync,
				installId = installId,
			)
		} ?: return SResult.failure(
			"User $userId already has a synchronization session",
			Msg.r("api_project_sync_begin_error_session", userId)
		)

		val projects = projectsDatasource.getProjects(userId)
		val deletedProjects = getDeletedProjects(userId)

		val data = ProjectsBeginSyncData(
			syncId = newSyncId,
			projects = projects,
			deletedProjects = deletedProjects
		)

		return SResult.success(data)
	}

	suspend fun endProjectsSync(userId: Long, syncId: String): SResult<Unit> {
		val session = syncSessionManager.findSession(userId)
		return if (session == null) {
			SResult.failure(
				"User $userId does not have a synchronization session",
				Msg.r("api_project_sync_end_noid", userId)
			)
		} else {
			if (session.syncId != syncId) {
				SResult.failure(
					"Invalid sync id",
					Msg.r("api_project_sync_end_invalidid")
				)
			} else {
				syncSessionManager.terminateSession(userId)
				SResult.success()
			}
		}
	}

	private suspend fun getDeletedProjects(userId: Long): Set<ProjectId> {
		return projectsDatasource.loadSyncData(userId).deletedProjects
	}

	suspend fun deleteProject(userId: Long, syncId: String, projectId: ProjectId): SResult<Unit> {
		if (syncSessionManager.validateSyncId(userId, syncId).not()) return SResult.failure(
			InvalidSyncIdException()
		)

		val projectDef = projectsDatasource.getProject(userId, projectId)
		return if (projectDef != null) {
			val result = projectEntityDatasource.deleteProject(userId, projectDef.uuid)
			if (result.isSuccess) {
				projectsDatasource.updateSyncData(userId) { data ->
					data.copy(
						deletedProjects = data.deletedProjects + projectDef.uuid
					)
				}
				SResult.success()
			} else {
				SResult.failure(Exception("Server failed to delete project: $projectId"))
			}
		} else {
			projectsDatasource.updateSyncData(userId) { data ->
				data.copy(
					deletedProjects = data.deletedProjects + projectId
				)
			}
			SResult.success()
		}
	}

	/**
	 * Idempotent on [projectId]. When the account already has a project named [projectName] under a
	 * different id, that project is returned so the client adopts its id.
	 */
	suspend fun createProject(
		userId: Long,
		syncId: String,
		projectName: String,
		projectId: ProjectId,
	): SResult<ProjectCreatedResult> {
		if (syncSessionManager.validateSyncId(userId, syncId).not())
			return SResult.failure(InvalidSyncIdException())

		if (validateProjectName(projectName).not())
			return SResult.failure(InvalidProjectName(projectName))

		projectEntityDatasource.getProject(userId, projectId)?.let { existing ->
			return SResult.success(ProjectCreatedResult(project = existing, alreadyExisted = true))
		}

		if (projectId in getDeletedProjects(userId))
			return SResult.failure(ProjectDeletedException(projectId))

		projectEntityDatasource.findProjectByName(userId, projectName)?.let { sameName ->
			return SResult.success(ProjectCreatedResult(project = sameName, alreadyExisted = true))
		}

		projectEntityDatasource.createProject(userId, projectName, projectId)?.let { created ->
			return SResult.success(ProjectCreatedResult(project = created, alreadyExisted = false))
		}

		// A concurrent retry of this same create can win the insert; that is still idempotent.
		return projectEntityDatasource.getProject(userId, projectId)
			?.let { SResult.success(ProjectCreatedResult(project = it, alreadyExisted = true)) }
			?: SResult.failure(ProjectIdTakenException(projectId))
	}

	suspend fun renameProject(
		userId: Long,
		syncId: String,
		projectId: ProjectId,
		newProjectName: String?,
	): SResult<Unit> {
		if (syncSessionManager.validateSyncId(userId, syncId).not())
			return SResult.failure(InvalidSyncIdException())

		if (!validateProjectName(newProjectName))
			return SResult.failure(InvalidProjectName(newProjectName ?: "null"))

		val existingProject = projectEntityDatasource.checkProjectExists(userId, projectId)
		if (existingProject.not()) return SResult.failure(ProjectNotFound(projectId))

		return if (projectEntityDatasource.renameProject(userId, projectId, newProjectName)) {
			SResult.success()
		} else {
			SResult.failure(ProjectNameTaken(newProjectName))
		}
	}

	data class ProjectCreatedResult(
		val project: ProjectDefinition,
		val alreadyExisted: Boolean,
	)
}