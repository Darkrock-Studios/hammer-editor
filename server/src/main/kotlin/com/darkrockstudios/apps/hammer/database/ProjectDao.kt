package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.GetProjectsWithSyncDate
import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import com.darkrockstudios.apps.hammer.utilities.toSqliteDateTimeString
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

class ProjectDao(
	database: Database,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.projectQueries

	suspend fun getProjects(userId: Long): Set<ProjectDefinition> = withContext(ioDispatcher) {
		val projects = queries.getProjects(userId).executeAsList()
		return@withContext projects.map { ProjectDefinition.wrap(it.name, it.uuid) }.toSet()
	}

	suspend fun getProjectsWithSyncDate(userId: Long): List<GetProjectsWithSyncDate> =
		withContext(ioDispatcher) {
			queries.getProjectsWithSyncDate(userId).executeAsList()
		}

	suspend fun getProjectsWithSyncDatePaged(userId: Long, limit: Long, offset: Long) =
		withContext(ioDispatcher) {
			queries.getProjectsWithSyncDatePaged(userId, limit, offset).executeAsList()
		}

	suspend fun getProjectsCount(userId: Long): Long =
		withContext(ioDispatcher) {
			queries.getProjectsCount(userId).executeAsOne()
		}

	suspend fun getMostRecentSyncForUser(userId: Long): String? =
		withContext(ioDispatcher) {
			queries.getMostRecentSyncForUser(userId).executeAsOneOrNull()?.last_sync
		}

	suspend fun createProject(
		userId: Long,
		uuid: ProjectId,
		projectName: String
	): Boolean = withContext(ioDispatcher) {
		return@withContext try {
			queries.createProject(
				userId = userId,
				name = projectName,
				uuid = uuid.id,
			)
			true
		} catch (e: Exception) {
			println(e.message)
			false
		}
	}

	suspend fun hasProject(userId: Long, projectId: ProjectId): Boolean =
		withContext(ioDispatcher) {
			queries.hasProjectById(userId, projectId.id).executeAsOne()
		}

	suspend fun updateProjectName(
		userId: Long,
		projectUuid: ProjectId,
		newName: String
	) = withContext(ioDispatcher) {
		queries.updateProjectName(userId = userId, uuid = projectUuid.id, name = newName)
	}

	suspend fun updateSyncData(
		lastId: Long,
		lastSync: Instant,
		userId: Long,
		projectName: String,
	) = withContext(ioDispatcher) {
		queries.updateSyncData(
			lastId = lastId,
			lastSync = lastSync.toSqliteDateTimeString(),
			userId = userId,
			name = projectName,
		)
	}

	suspend fun findProjectData(
		userId: Long,
		projectName: String,
	): Project? = withContext(ioDispatcher) {
		queries.findProjectByName(userId, projectName)
			.executeAsOneOrNull()
	}

	suspend fun getProjectData(
		userId: Long,
		projectUuid: ProjectId,
	): Project? = withContext(ioDispatcher) {
		queries.getProject(userId, projectUuid.id)
			.executeAsOneOrNull()
	}

	suspend fun getProjectId(
		userId: Long,
		projectUuid: ProjectId,
	): Long = withContext(ioDispatcher) {
		queries.getProjectId(userId, projectUuid.id)
			.executeAsOne()
	}

	suspend fun deleteProject(userId: Long, projectId: ProjectId) = withContext(ioDispatcher) {
		queries.deleteProject(userId, projectId.id)
	}
}