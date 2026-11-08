package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.GetProjectsWithSyncDate
import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.OwnerId
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
	private val memberQueries = database.serverDatabase.projectMemberQueries
	private val inviteQueries = database.serverDatabase.projectInviteQueries

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
		owner: OwnerId,
		uuid: ProjectId,
		projectName: String
	): Boolean = withContext(ioDispatcher) {
		queries.createProject(
			userId = owner.id,
			name = projectName,
			uuid = uuid.id,
		).value > 0
	}

	suspend fun hasProject(owner: OwnerId, projectId: ProjectId): Boolean =
		withContext(ioDispatcher) {
			queries.hasProjectById(owner.id, projectId.id).executeAsOne()
		}

	/** False when the account already has a project under [newName]; `project(name, user_id)` is unique. */
	suspend fun updateProjectName(
		owner: OwnerId,
		projectUuid: ProjectId,
		newName: String
	): Boolean = withContext(ioDispatcher) {
		return@withContext try {
			queries.updateProjectName(userId = owner.id, uuid = projectUuid.id, name = newName)
			true
		} catch (e: SQLException) {
			if (e.isUniqueViolation()) false else throw e
		}
	}

	suspend fun updateSyncData(
		lastId: Long,
		lastSync: Instant,
		owner: OwnerId,
		projectUuid: ProjectId,
	) = withContext(ioDispatcher) {
		queries.updateSyncData(
			lastId = lastId,
			lastSync = lastSync,
			userId = owner.id,
			uuid = projectUuid.id,
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
		owner: OwnerId,
		projectUuid: ProjectId,
	): Project? = withContext(ioDispatcher) {
		queries.getProject(owner.id, projectUuid.id)
			.executeAsOneOrNull()
	}

	suspend fun getProjectByUuid(projectUuid: ProjectId): Project? = withContext(ioDispatcher) {
		queries.getProjectByUuid(projectUuid.id).executeAsOneOrNull()
	}

	suspend fun getProjectId(
		owner: OwnerId,
		projectUuid: ProjectId,
	): Long = withContext(ioDispatcher) {
		queries.getProjectId(owner.id, projectUuid.id)
			.executeAsOne()
	}

	suspend fun getProjectIdOrNull(
		owner: OwnerId,
		projectUuid: ProjectId,
	): Long? = withContext(ioDispatcher) {
		queries.getProjectId(owner.id, projectUuid.id)
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

	suspend fun deleteProject(owner: OwnerId, projectId: ProjectId) = withContext(ioDispatcher) {
		queries.transaction {
			queries.getProjectId(owner.id, projectId.id).executeAsOneOrNull()?.let { rowId ->
				memberQueries.deleteAllForProject(rowId)
				inviteQueries.deleteAllForProject(rowId)
			}
			queries.deleteProject(owner.id, projectId.id)
		}
	}
}