package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ProjectHashItem
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.base.validate.validateProjectName
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.*
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Clock

class ProjectsRepository(
	private val clock: Clock,
	private val projectsDatasource: ProjectsDatasource,
	private val projectEntityDatasource: ProjectEntityDatasource,
	private val serverProjectDataRepository: ServerProjectDataRepository,
) {
	private val syncSessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession> by inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECTS_SYNC_MANAGER)
	)

	private val projectSyncSessionManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession> by inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECT_SYNC_MANAGER)
	)

	suspend fun createUserData(userId: Long) = projectsDatasource.createUserData(userId)

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

	/**
	 * Pre-sync change probe: returns the subset of the requested projects whose server-side
	 * project-wide content hash matches the client's. Those are guaranteed in-sync and can skip
	 * their full project sync this session. Read-only — no sync session required.
	 *
	 * A project is omitted from the result (i.e. treated as changed) whenever the server can't be
	 * certain it matches: unknown project, a missing cached entity hash, or an unreadable
	 * project-data blob. Worst case is a redundant full sync, never a skipped-but-divergent project.
	 */
	suspend fun probeProjectChanges(
		userId: Long,
		items: List<ProjectHashItem>,
	): Set<ProjectId> {
		val unchanged = mutableSetOf<ProjectId>()
		for (item in items) {
			val projectDef = projectsDatasource.getProject(userId, item.projectId) ?: continue
			// A project with an in-flight sync (e.g. from another device) may have half-updated
			// stored hashes, so we can't certify it "unchanged". Omit it → the client full-syncs.
			if (projectSyncSessionManager.hasActiveSyncSession(ProjectSyncKey(userId, projectDef))) continue
			val serverHash = computeProjectContentHash(userId, projectDef) ?: continue
			if (serverHash == item.hash) {
				unchanged += item.projectId
			}
		}
		return unchanged
	}

	/**
	 * Recomputes a project's project-wide content hash from the server's stored state (cached entity
	 * hashes + the project-data blob hash), using the same [ProjectContentHasher] the client runs.
	 * Returns null when the hash can't be computed reliably, so the caller treats the project as changed.
	 */
	private suspend fun computeProjectContentHash(
		userId: Long,
		projectDef: ProjectDefinition,
	): String? {
		// One query for all (id, hash) pairs rather than a per-entity lookup.
		val entityHashes = projectEntityDatasource.getEntityHashes(userId, projectDef)

		val dataResult = serverProjectDataRepository.load(userId, projectDef)
		if (!isSuccess(dataResult)) return null
		val projectDataHash = dataResult.data?.hash ?: ProjectDataHasher.hash(ProjectData())

		return ProjectContentHasher.hash(entityHashes, projectDataHash)
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

	suspend fun createProject(
		userId: Long,
		syncId: String,
		projectName: String
	): SResult<ProjectCreatedResult> {
		if (syncSessionManager.validateSyncId(userId, syncId).not())
			return SResult.failure(InvalidSyncIdException())

		if (validateProjectName(projectName).not())
			return SResult.failure(InvalidProjectName(projectName))

		val existingProject = projectEntityDatasource.findProjectByName(userId, projectName)
		val projectDef =
			existingProject ?: projectEntityDatasource.createProject(userId, projectName)
		val alreadyExists = (existingProject != null)

		return SResult.success(
			ProjectCreatedResult(
				project = projectDef,
				alreadyExisted = alreadyExists
			)
		)
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