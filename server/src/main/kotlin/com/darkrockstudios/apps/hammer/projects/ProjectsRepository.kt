package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.project.ProjectsSyncData
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject

class ProjectsRepository(
	private val clock: Clock,
	private val projectsDatasource: ProjectsDatasource
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

	private fun getDeletedProjects(userId: Long): Set<String> {
		return projectsDatasource.loadSyncData(userId).deletedProjects
	}

	suspend fun deleteProject(userId: Long, syncId: String, projectName: String): Result<Unit> {
		if (syncSessionManager.validateSyncId(userId, syncId, true)
				.not()
		) return Result.failure(InvalidSyncIdException())

		val result = projectsDatasource.deleteProject(userId, projectName)
		return result
	}

	suspend fun createProject(userId: Long, syncId: String, projectName: String): Result<Unit> {
		if (syncSessionManager.validateSyncId(userId, syncId, true)
				.not()
		) return Result.failure(InvalidSyncIdException())

		projectsDatasource.createProject(userId, projectName)

		projectsDatasource.updateSyncData(userId) { data ->
			data.copy(
				deletedProjects = data.deletedProjects - projectName
			)
		}

		return Result.success(Unit)
	}

	companion object {
		fun defaultData(userId: Long): ProjectsSyncData {
			return ProjectsSyncData(
				lastSync = Instant.DISTANT_PAST,
				deletedProjects = emptySet()
			)
		}
	}
}