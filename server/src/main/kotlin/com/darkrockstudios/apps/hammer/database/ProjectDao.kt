package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.GetProjectsWithSyncDate
import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.sql.SQLException
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

	suspend fun getMostRecentSyncForUser(userId: Long): Instant? =
		withContext(ioDispatcher) {
			queries.getMostRecentSyncForUser(userId).executeAsOneOrNull()?.last_sync
		}

	/** False when nothing was inserted because some account, possibly this one, already holds [uuid]. */
	suspend fun createProject(
		userId: Long,
		uuid: ProjectId,
		projectName: String
	): Boolean = withContext(ioDispatcher) {
		queries.createProject(
			userId = userId,
			name = projectName,
			uuid = uuid.id,
		).value > 0
	}

	suspend fun hasProject(userId: Long, projectId: ProjectId): Boolean =
		withContext(ioDispatcher) {
			queries.hasProjectById(userId, projectId.id).executeAsOne()
		}

	/** False when the account already has a project under [newName]; `project(name, user_id)` is unique. */
	suspend fun updateProjectName(
		userId: Long,
		projectUuid: ProjectId,
		newName: String
	): Boolean = withContext(ioDispatcher) {
		return@withContext try {
			queries.updateProjectName(userId = userId, uuid = projectUuid.id, name = newName)
			true
		} catch (e: SQLException) {
			if (e.isUniqueViolation()) false else throw e
		}
	}

	suspend fun updateSyncData(
		lastId: Long,
		lastSync: Instant,
		userId: Long,
		projectName: String,
	) = withContext(ioDispatcher) {
		queries.updateSyncData(
			lastId = lastId,
			lastSync = lastSync,
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

	suspend fun getProjectIdOrNull(
		userId: Long,
		projectUuid: ProjectId,
	): Long? = withContext(ioDispatcher) {
		queries.getProjectId(userId, projectUuid.id)
			.executeAsOneOrNull()
	}

	suspend fun getProjectByRowId(id: Long): Project? = withContext(ioDispatcher) {
		queries.getProjectByRowId(id).executeAsOneOrNull()
	}

	/** Only projects inside a group appear. */
	suspend fun getProjectPlacements(userId: Long): Map<ProjectId, GroupId> = withContext(ioDispatcher) {
		queries.getProjectPlacements(userId).executeAsList()
			.associate { ProjectId(it.uuid) to GroupId(it.group_uuid) }
	}

	suspend fun getProjectGroupUuid(userId: Long, projectId: ProjectId): GroupId? = withContext(ioDispatcher) {
		queries.getProjectGroupUuid(userId, projectId.id).executeAsOneOrNull()?.let(::GroupId)
	}

	/** False when the account has no project [projectId]. A null [groupRowId] places it at the top level. */
	suspend fun setProjectGroup(
		userId: Long,
		projectId: ProjectId,
		groupRowId: Long?,
	): Boolean = withContext(ioDispatcher) {
		queries.setProjectGroup(groupId = groupRowId, userId = userId, uuid = projectId.id).value > 0
	}

	suspend fun deleteProject(userId: Long, projectId: ProjectId) = withContext(ioDispatcher) {
		queries.deleteProject(userId, projectId.id)
	}
}