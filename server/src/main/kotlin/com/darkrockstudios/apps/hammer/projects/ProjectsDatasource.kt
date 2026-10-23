package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import kotlin.time.Instant

interface ProjectsDatasource {
	suspend fun saveSyncData(userId: Long, data: ProjectsSyncData)
	suspend fun getProjects(userId: Long): Set<ProjectDefinition>
	suspend fun getProjectsWithSyncDate(userId: Long): List<ProjectWithSyncDate>
	suspend fun getProjectsWithSyncDate(userId: Long, page: Int, pageSize: Int): List<ProjectWithSyncDate>
	suspend fun getProjectsCount(userId: Long): Long
	suspend fun getMostRecentSyncForUser(userId: Long): Instant?
	suspend fun findProjectByName(userId: Long, projectName: String): ProjectDefinition?
	suspend fun findProjectByNameWithSyncDate(userId: Long, projectName: String): ProjectWithSyncDate?
	suspend fun getProject(userId: Long, projectId: ProjectId): ProjectDefinition?
	suspend fun loadSyncData(userId: Long): ProjectsSyncData
	suspend fun createUserData(userId: Long)

	suspend fun getGroups(userId: Long): List<ProjectGroup>
	suspend fun getGroup(userId: Long, groupId: GroupId): ProjectGroup?
	suspend fun findSiblingGroupByName(userId: Long, parentRowId: Long?, name: String): ProjectGroup?

	/** False when nothing was inserted: [groupId] is held by some account, or a sibling took [name]. */
	suspend fun createGroup(
		userId: Long,
		groupId: GroupId,
		name: String,
		description: String?,
		parentRowId: Long?,
	): Boolean

	/** False when a sibling under [parentRowId] already has [name]. */
	suspend fun updateGroup(
		userId: Long,
		rowId: Long,
		name: String,
		description: String?,
		parentRowId: Long?,
	): Boolean

	/** Moves the group's contents under [newParentRowId] first; false, with nothing changed, on a name collision. */
	suspend fun deleteGroup(userId: Long, group: ProjectGroup, newParentRowId: Long?): Boolean

	/** Only projects inside a group appear. */
	suspend fun getProjectPlacements(userId: Long): Map<ProjectId, GroupId>
	suspend fun getProjectGroupId(userId: Long, projectId: ProjectId): GroupId?

	/** False when the account has no project [projectId]. */
	suspend fun setProjectGroup(userId: Long, projectId: ProjectId, groupRowId: Long?): Boolean

	suspend fun updateSyncData(
		userId: Long,
		action: (ProjectsSyncData) -> ProjectsSyncData
	): ProjectsSyncData {
		val syncData = loadSyncData(userId)
		val updated = action(syncData)
		saveSyncData(userId, updated)
		return updated
	}

	companion object {
		fun defaultUserData(userId: Long): ProjectsSyncData {
			return ProjectsSyncData(
				lastSync = Instant.DISTANT_PAST,
				deletedProjects = emptySet()
			)
		}
	}
}