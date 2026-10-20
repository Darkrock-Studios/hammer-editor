package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ProjectGroupDao
import com.darkrockstudios.apps.hammer.database.ProjectsDao
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.projects.ProjectsDatasource.Companion.defaultUserData

class ProjectsDatabaseDatasource(
	private val projectDao: ProjectDao,
	private val projectsDao: ProjectsDao,
	private val projectGroupDao: ProjectGroupDao,
) : ProjectsDatasource {

	override suspend fun saveSyncData(userId: Long, data: ProjectsSyncData) {
		projectsDao.updateProjectSyncData(
			userId,
			data.lastSync,
			data.deletedProjects
		)
	}

	override suspend fun getProjects(userId: Long): Set<ProjectDefinition> {
		return projectDao.getProjects(userId)
	}

	override suspend fun getProjectsWithSyncDate(userId: Long): List<ProjectWithSyncDate> {
		return projectDao.getProjectsWithSyncDate(userId).map {
			ProjectWithSyncDate(name = it.name, uuid = it.uuid, lastSync = it.last_sync)
		}
	}

	override suspend fun getProjectsWithSyncDate(
		userId: Long,
		page: Int,
		pageSize: Int
	): List<ProjectWithSyncDate> {
		val limit = pageSize.toLong()
		val offset = (page * pageSize).toLong()
		return projectDao.getProjectsWithSyncDatePaged(userId, limit, offset).map {
			ProjectWithSyncDate(name = it.name, uuid = it.uuid, lastSync = it.last_sync)
		}
	}

	override suspend fun getProjectsCount(userId: Long): Long {
		return projectDao.getProjectsCount(userId)
	}

	override suspend fun getMostRecentSyncForUser(userId: Long): kotlin.time.Instant? {
		return projectDao.getMostRecentSyncForUser(userId)
	}

	override suspend fun findProjectByName(userId: Long, projectName: String): ProjectDefinition? {
		val data = projectDao.findProjectData(userId, projectName)
		return if (data != null) {
			ProjectDefinition(name = data.name, uuid = ProjectId(data.uuid))
		} else {
			null
		}
	}

	override suspend fun findProjectByNameWithSyncDate(userId: Long, projectName: String): ProjectWithSyncDate? {
		val data = projectDao.findProjectData(userId, projectName)
		return if (data != null) {
			ProjectWithSyncDate(name = data.name, uuid = data.uuid, lastSync = data.last_sync)
		} else {
			null
		}
	}

	override suspend fun getProject(userId: Long, projectId: ProjectId): ProjectDefinition? {
		val project = projectDao.getProjectData(userId, projectId) ?: return null
		return ProjectDefinition(name = project.name, uuid = ProjectId(project.uuid))
	}

	override suspend fun loadSyncData(userId: Long): ProjectsSyncData {
		return projectsDao.getProjectSyncData(userId)
	}

	override suspend fun createUserData(userId: Long) {
		val data: ProjectsSyncData = defaultUserData(userId)
		projectsDao.updateProjectSyncData(
			userId,
			data.lastSync,
			data.deletedProjects
		)
	}

	override suspend fun getGroups(userId: Long): List<ProjectGroup> = projectGroupDao.getGroups(userId)

	override suspend fun getGroup(userId: Long, groupId: GroupId): ProjectGroup? =
		projectGroupDao.getGroup(userId, groupId)

	override suspend fun findSiblingGroupByName(userId: Long, parentRowId: Long?, name: String): ProjectGroup? =
		projectGroupDao.findSiblingByName(userId, parentRowId, name)

	override suspend fun createGroup(
		userId: Long,
		groupId: GroupId,
		name: String,
		description: String?,
		parentRowId: Long?,
	): Boolean = projectGroupDao.createGroup(userId, groupId, name, description, parentRowId)

	override suspend fun updateGroup(
		userId: Long,
		rowId: Long,
		name: String,
		description: String?,
		parentRowId: Long?,
	): Boolean = projectGroupDao.updateGroup(userId, rowId, name, description, parentRowId)

	override suspend fun deleteGroup(userId: Long, group: ProjectGroup, newParentRowId: Long?): Boolean =
		projectGroupDao.deleteGroup(userId, group, newParentRowId)

	override suspend fun getProjectPlacements(userId: Long): Map<ProjectId, GroupId> =
		projectDao.getProjectPlacements(userId)

	override suspend fun getProjectGroupId(userId: Long, projectId: ProjectId): GroupId? =
		projectDao.getProjectGroupUuid(userId, projectId)

	override suspend fun setProjectGroup(userId: Long, projectId: ProjectId, groupRowId: Long?): Boolean =
		projectDao.setProjectGroup(userId, projectId, groupRowId)
}
