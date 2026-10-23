package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_DESCRIPTION_LENGTH
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
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
			deletedProjects = deletedProjects,
			groups = projectsDatasource.getGroups(userId).toSet(),
			projectPlacements = projectsDatasource.getProjectPlacements(userId),
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
	 *
	 * [groupId] places a newly created project; an unknown group places it at the top level. An
	 * existing project is never moved, and the result reports where it actually lives.
	 */
	suspend fun createProject(
		userId: Long,
		syncId: String,
		projectName: String,
		projectId: ProjectId,
		groupId: GroupId? = null,
	): SResult<ProjectCreatedResult> {
		if (syncSessionManager.validateSyncId(userId, syncId).not())
			return SResult.failure(InvalidSyncIdException())

		if (validateProjectName(projectName).not())
			return SResult.failure(InvalidProjectName(projectName))

		projectEntityDatasource.getProject(userId, projectId)?.let { existing ->
			return SResult.success(existingProjectResult(userId, existing))
		}

		if (projectId in getDeletedProjects(userId))
			return SResult.failure(ProjectDeletedException(projectId))

		projectEntityDatasource.findProjectByName(userId, projectName)?.let { sameName ->
			return SResult.success(existingProjectResult(userId, sameName))
		}

		projectEntityDatasource.createProject(userId, projectName, projectId)?.let { created ->
			val group = groupId?.let { projectsDatasource.getGroup(userId, it) }
			if (group != null) projectsDatasource.setProjectGroup(userId, created.uuid, group.rowId)
			return SResult.success(
				ProjectCreatedResult(project = created, alreadyExisted = false, groupId = group?.uuid)
			)
		}

		// A concurrent retry of this same create can win the insert; that is still idempotent.
		return projectEntityDatasource.getProject(userId, projectId)
			?.let { SResult.success(existingProjectResult(userId, it)) }
			?: SResult.failure(ProjectIdTakenException(projectId))
	}

	private suspend fun existingProjectResult(userId: Long, project: ProjectDefinition) = ProjectCreatedResult(
		project = project,
		alreadyExisted = true,
		groupId = projectsDatasource.getProjectGroupId(userId, project.uuid),
	)

	/** An unknown [groupId] places the project at the top level. */
	suspend fun moveProject(
		userId: Long,
		syncId: String,
		projectId: ProjectId,
		groupId: GroupId?,
	): SResult<Unit> {
		if (syncSessionManager.validateSyncId(userId, syncId).not())
			return SResult.failure(InvalidSyncIdException())

		val group = groupId?.let { projectsDatasource.getGroup(userId, it) }
		return if (projectsDatasource.setProjectGroup(userId, projectId, group?.rowId)) {
			SResult.success()
		} else {
			SResult.failure(ProjectNotFound(projectId))
		}
	}

	/**
	 * Creates or updates [group] under its client-minted id. An unknown parent places it at the top
	 * level. A new group whose name a sibling already holds is not created: the sibling's id is
	 * returned so the client adopts it, as [createProject] does for project names.
	 */
	suspend fun upsertGroup(
		userId: Long,
		syncId: String,
		group: ApiGroupDefinition,
	): SResult<GroupUpsertResult> {
		if (syncSessionManager.validateSyncId(userId, syncId).not())
			return SResult.failure(InvalidSyncIdException())

		if (validateProjectName(group.name).not())
			return SResult.failure(InvalidGroupName(group.name))

		val description = group.description?.takeIf { it.isNotBlank() }
		if (description != null && description.length > MAX_GROUP_DESCRIPTION_LENGTH)
			return SResult.failure(InvalidGroupDescription(description.length))

		val groups = projectsDatasource.getGroups(userId)
		val byRowId = groups.associateBy { it.rowId }
		val existing = groups.find { it.uuid == group.uuid }
		val parent = group.parentId?.let { parentId -> groups.find { it.uuid == parentId } }

		if (existing != null && parent != null && ancestorsOf(parent, byRowId).any { it.rowId == existing.rowId })
			return SResult.failure(GroupCycleException(group.uuid))

		val parentDepth = parent?.let { ancestorsOf(it, byRowId).count() } ?: 0
		val subtreeHeight = existing?.let { subtreeHeight(it, groups) } ?: 0
		if (parentDepth + 1 + subtreeHeight > MAX_GROUP_NESTING_DEPTH)
			return SResult.failure(GroupTooDeepException(group.uuid))

		val sibling = projectsDatasource.findSiblingGroupByName(userId, parent?.rowId, group.name)
			?.takeIf { it.rowId != existing?.rowId }

		if (existing != null) {
			if (sibling != null) return SResult.failure(GroupNameTaken(group.name))
			val updated = projectsDatasource.updateGroup(userId, existing.rowId, group.name, description, parent?.rowId)
			return if (updated) {
				SResult.success(GroupUpsertResult(groupId = existing.uuid, alreadyExisted = true))
			} else {
				SResult.failure(GroupNameTaken(group.name))
			}
		}

		if (sibling != null)
			return SResult.success(GroupUpsertResult(groupId = sibling.uuid, alreadyExisted = true))

		if (projectsDatasource.createGroup(userId, group.uuid, group.name, description, parent?.rowId))
			return SResult.success(GroupUpsertResult(groupId = group.uuid, alreadyExisted = false))

		// Lost the insert to a concurrent retry of this create, or to a same-name sibling.
		val winner = projectsDatasource.getGroup(userId, group.uuid)
			?: projectsDatasource.findSiblingGroupByName(userId, parent?.rowId, group.name)
		return if (winner != null) {
			SResult.success(GroupUpsertResult(groupId = winner.uuid, alreadyExisted = true))
		} else {
			SResult.failure(GroupIdTakenException(group.uuid))
		}
	}

	/** Idempotent. The group's child groups and projects move up to its parent. */
	suspend fun deleteGroup(userId: Long, syncId: String, groupId: GroupId): SResult<Unit> {
		if (syncSessionManager.validateSyncId(userId, syncId).not())
			return SResult.failure(InvalidSyncIdException())

		val group = projectsDatasource.getGroup(userId, groupId) ?: return SResult.success()

		val groups = projectsDatasource.getGroups(userId)
		val siblings = groups.filter { it.parentRowId == group.parentRowId && it.rowId != group.rowId }
		groups.filter { it.parentRowId == group.rowId }
			.find { child -> siblings.any { it.name.equals(child.name, ignoreCase = true) } }
			?.let { return SResult.failure(GroupNameTaken(it.name)) }

		// The database stays authoritative on name equality and catches a concurrent rename.
		return if (projectsDatasource.deleteGroup(userId, group, group.parentRowId)) {
			SResult.success()
		} else {
			SResult.failure(GroupNameTaken(group.name))
		}
	}

	/** [group] first, then up to the top level. */
	private fun ancestorsOf(group: ProjectGroup, byRowId: Map<Long, ProjectGroup>): Sequence<ProjectGroup> {
		val seen = mutableSetOf<Long>()
		return generateSequence(group) { current -> current.parentRowId?.let(byRowId::get) }
			.takeWhile { seen.add(it.rowId) }
	}

	/** Levels below [group]: zero for a group with no child groups. */
	private fun subtreeHeight(group: ProjectGroup, groups: List<ProjectGroup>): Int {
		val children = groups.groupBy { it.parentRowId }
		val seen = mutableSetOf(group.rowId)
		var level = listOf(group)
		var height = 0
		while (true) {
			level = level.flatMap { children[it.rowId].orEmpty() }.filter { seen.add(it.rowId) }
			if (level.isEmpty()) return height
			height++
		}
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
		val groupId: GroupId? = null,
	)

	data class GroupUpsertResult(
		val groupId: GroupId,
		val alreadyExisted: Boolean,
	)
}