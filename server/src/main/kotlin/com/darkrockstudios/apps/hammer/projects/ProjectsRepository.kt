package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.ProjectDatasource
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlinx.datetime.Clock
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject

class ProjectsRepository(
	private val clock: Clock,
	private val projectsDatasource: ProjectsDatasource,
	private val projectDatasource: ProjectDatasource,
) {
	private val syncSessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession> by inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECTS_SYNC_MANAGER)
	)

	suspend fun createUserData(userId: Long) = projectsDatasource.createUserData(userId)

	suspend fun beginProjectsSync(userId: Long): SResult<ProjectsBeginSyncData> {
		return if (syncSessionManager.hasActiveSyncSession(userId)) {
			SResult.failure(
				"User $userId already has a synchronization session",
				Msg.r("api.project.sync.begin.error.session")
			)
		} else {
			val newSyncId = syncSessionManager.createNewSession(userId) { user, sync ->
				ProjectsSynchronizationSession(
					userId = user,
					started = clock.now(),
					syncId = sync
				)
			}

			val projects = projectsDatasource.getProjects(userId)
			val deletedProjects = getDeletedProjects(userId)

			val data = ProjectsBeginSyncData(
				syncId = newSyncId,
				projects = projects,
				deletedProjects = deletedProjects
			)

			SResult.success(data)
		}
	}

	suspend fun endProjectsSync(userId: Long, syncId: String): SResult<Unit> {
		val session = syncSessionManager.findSession(userId)
		return if (session == null) {
			SResult.failure(
				"User $userId does not have a synchronization session",
				Msg.r("api.project.sync.end.noid", userId)
			)
		} else {
			if (session.syncId != syncId) {
				SResult.failure(
					"Invalid sync id",
					Msg.r("api.project.sync.end.invalidid")
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
		if (syncSessionManager.validateSyncId(userId, syncId, true)
				.not()
		) return SResult.failure(InvalidSyncIdException())

		val projectDef = projectsDatasource.getProject(userId, projectId)
		return if (projectDef != null) {
			val result = projectDatasource.deleteProject(userId, projectDef.uuid)
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
		if (syncSessionManager.validateSyncId(userId, syncId, true)
				.not()
		) return SResult.failure(InvalidSyncIdException())

		val existingProject = projectDatasource.findProjectByName(userId, projectName)
		val projectDef = existingProject ?: projectDatasource.createProject(userId, projectName)
		val alreadyExists = (existingProject != null)

		return SResult.success(
			ProjectCreatedResult(
				project = projectDef,
				alreadyExisted = alreadyExists
			)
		)
	}

	data class ProjectCreatedResult(
		val project: ProjectDefinition,
		val alreadyExisted: Boolean,
	)
}