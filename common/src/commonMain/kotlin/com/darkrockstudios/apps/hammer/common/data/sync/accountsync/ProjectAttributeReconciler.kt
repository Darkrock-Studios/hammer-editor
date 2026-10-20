package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.RenamedProject

sealed interface ProjectAttributeAction {
	/** [name] is already safe to store on this device. */
	data class RenameLocally(val name: String) : ProjectAttributeAction
	data class RenameOnServer(val name: String) : ProjectAttributeAction

	/** Into the server's group [groupId], or the top level when null. */
	data class MoveLocally(val groupId: GroupId?) : ProjectAttributeAction
	data class MoveOnServer(val groupId: GroupId?) : ProjectAttributeAction
}

/**
 * A project both sides hold. [baseline] is recorded once every action succeeds; it is null when this
 * session cannot settle the project.
 */
data class ProjectReconciliation(
	val local: ProjectDef,
	val server: ApiProjectDefinition,
	val actions: List<ProjectAttributeAction>,
	val baseline: ProjectBaseline?,
)

/**
 * Turns renames queued by older versions into baselines. Each seeds its project's baseline with the
 * name the server holds now, so the three-way rule pushes the local name. A queued project the server
 * does not list is dropped: creation or deletion already covers it.
 */
fun seedFromLegacyRenames(
	queued: Set<RenamedProject>,
	serverProjects: Collection<ApiProjectDefinition>,
	baselines: Map<ProjectId, ProjectBaseline>,
): Map<ProjectId, ProjectBaseline> {
	val serverById = serverProjects.associateBy { it.uuid }
	return queued.mapNotNull { rename ->
		val server = serverById[rename.projectId] ?: return@mapNotNull null
		val groupId = baselines[rename.projectId]?.groupId ?: server.groupId
		rename.projectId to ProjectBaseline(server.name, groupId)
	}.toMap()
}

/**
 * Applies the three-way rule to the name and placement of every project on both sides, except the
 * [excluded] ones. A local placement is only pushed into a group in [confirmedGroupIds]: the server
 * would put a project in any other group at the top level.
 */
fun reconcileProjectAttributes(
	local: List<ProjectDef>,
	placement: Map<ProjectId, GroupId?>,
	server: Collection<ApiProjectDefinition>,
	baselines: Map<ProjectId, ProjectBaseline>,
	excluded: Set<ProjectId>,
	confirmedGroupIds: Set<GroupId>,
): List<ProjectReconciliation> {
	val localById = local.associateBy { it.id }
	return server
		.filter { it.uuid !in excluded }
		.mapNotNull { remote ->
			val project = localById[remote.uuid] ?: return@mapNotNull null
			val baseline = baselines[remote.uuid]
			val localGroup = placement[project.id]
			val actions = mutableListOf<ProjectAttributeAction>()

			val name = nameThreeWay(project.name, remote.name, baseline?.name)
			when (name) {
				Winner.TakeServer ->
					actions += ProjectAttributeAction.RenameLocally(ProjectsRepository.toLocalSafeName(remote.name))

				Winner.PushLocal -> actions += ProjectAttributeAction.RenameOnServer(project.name)
				Winner.Agree -> Unit
			}

			val place = threeWay(localGroup, remote.groupId, baseline?.groupId, baseline != null)
			var settles = true
			when (place) {
				Winner.TakeServer -> actions += ProjectAttributeAction.MoveLocally(remote.groupId)
				Winner.PushLocal ->
					if (localGroup == null || localGroup in confirmedGroupIds) {
						actions += ProjectAttributeAction.MoveOnServer(localGroup)
					} else {
						settles = false
					}

				Winner.Agree -> Unit
			}

			ProjectReconciliation(
				local = project,
				server = remote,
				actions = actions,
				baseline = if (settles) {
					ProjectBaseline(
						name = if (name == Winner.PushLocal) project.name else remote.name,
						groupId = if (place == Winner.PushLocal) localGroup else remote.groupId,
					)
				} else {
					null
				},
			)
		}
}

/**
 * The local group a project placed in the server group [serverGroupId] belongs in: that group, or its
 * nearest ancestor this device has, walking the server's parents. Null is the top level.
 */
fun ProjectTree.nearestLocalGroup(
	serverGroupId: GroupId?,
	serverGroups: Map<GroupId, ApiGroupDefinition>,
): GroupDef? {
	var id = serverGroupId
	var steps = 0
	while (id != null && steps++ <= MAX_GROUP_NESTING_DEPTH) {
		group(id)?.let { return it }
		id = serverGroups[id]?.parentId
	}
	return null
}
