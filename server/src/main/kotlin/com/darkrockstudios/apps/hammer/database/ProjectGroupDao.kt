package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.projects.ProjectGroup
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.sql.SQLException

class ProjectGroupDao(
	database: Database,
) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.projectGroupQueries
	private val projectQueries = database.serverDatabase.projectQueries

	suspend fun getGroups(userId: Long): List<ProjectGroup> = withContext(ioDispatcher) {
		queries.getGroups(userId, ::toProjectGroup).executeAsList()
	}

	suspend fun getGroup(userId: Long, groupId: GroupId): ProjectGroup? = withContext(ioDispatcher) {
		queries.getGroup(userId, groupId.id, ::toProjectGroup).executeAsOneOrNull()
	}

	/** Case-insensitive; a null [parentRowId] searches the top level. */
	suspend fun findSiblingByName(
		userId: Long,
		parentRowId: Long?,
		name: String,
	): ProjectGroup? = withContext(ioDispatcher) {
		if (parentRowId == null) {
			queries.findRootGroupByName(userId, name, ::toProjectGroup).executeAsOneOrNull()
		} else {
			queries.findChildGroupByName(userId, parentRowId, name, ::toProjectGroup).executeAsOneOrNull()
		}
	}

	/**
	 * False when nothing was inserted: some account, possibly this one, already holds [groupId],
	 * or a sibling took [name] first.
	 */
	suspend fun createGroup(
		userId: Long,
		groupId: GroupId,
		name: String,
		description: String?,
		parentRowId: Long?,
	): Boolean = withContext(ioDispatcher) {
		try {
			queries.createGroup(
				userId = userId,
				uuid = groupId.id,
				name = name,
				description = description,
				parentId = parentRowId,
			).value > 0
		} catch (e: SQLException) {
			if (e.isUniqueViolation()) false else throw e
		}
	}

	/** False when a sibling under the target parent already has [name]. */
	suspend fun updateGroup(
		userId: Long,
		rowId: Long,
		name: String,
		description: String?,
		parentRowId: Long?,
	): Boolean = withContext(ioDispatcher) {
		try {
			queries.updateGroup(
				name = name,
				description = description,
				parentId = parentRowId,
				userId = userId,
				id = rowId,
			)
			true
		} catch (e: SQLException) {
			if (e.isUniqueViolation()) false else throw e
		}
	}

	/**
	 * Moves [group]'s child groups and projects under [newParentRowId], then deletes it.
	 * Reparents explicitly rather than leaning on the FKs, which test databases do not enforce.
	 * False, with nothing changed, when a child group's name collides at the new parent.
	 */
	suspend fun deleteGroup(
		userId: Long,
		group: ProjectGroup,
		newParentRowId: Long?,
	): Boolean = withContext(ioDispatcher) {
		try {
			queries.transaction {
				queries.reparentChildren(newParentId = newParentRowId, userId = userId, oldParentId = group.rowId)
				projectQueries.reparentProjects(newGroupId = newParentRowId, userId = userId, oldGroupId = group.rowId)
				queries.deleteGroup(userId = userId, id = group.rowId)
			}
			true
		} catch (e: SQLException) {
			if (e.isUniqueViolation()) false else throw e
		}
	}

	suspend fun deleteAllForUser(userId: Long) = withContext(ioDispatcher) {
		queries.deleteAllForUser(userId)
	}
}

private fun toProjectGroup(
	id: Long,
	uuid: String,
	name: String,
	description: String?,
	parentId: Long?,
	parentUuid: String?,
) = ProjectGroup(
	rowId = id,
	uuid = GroupId(uuid),
	name = name,
	description = description,
	parentRowId = parentId,
	parentUuid = parentUuid?.let(::GroupId),
)
