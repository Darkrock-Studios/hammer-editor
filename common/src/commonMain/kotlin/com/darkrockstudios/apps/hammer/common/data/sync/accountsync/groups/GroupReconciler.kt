package com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.Winner
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.nameThreeWay
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.threeWay
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.GroupBaseline

/** A local-only group that takes the id of the server-only group with its name and parent. */
data class AdoptByName(val local: GroupDef, val serverId: GroupId)

/** A group on both sides that differs in at least one attribute. [server]'s parent is normalized. */
data class GroupUpdate(
	val local: GroupDef,
	val server: ApiGroupDefinition,
	val name: Winner,
	val parent: Winner,
	val description: Winner,
) {
	val needsServerPush: Boolean
		get() = name == Winner.PushLocal || parent == Winner.PushLocal || description == Winner.PushLocal

	val mergedName: String get() = if (name == Winner.PushLocal) local.name else server.name
	val mergedParent: GroupId? get() = if (parent == Winner.PushLocal) local.parentId else server.parentId
	val mergedDescription: String?
		get() = if (description == Winner.PushLocal) local.description.orNullIfBlank() else server.description.orNullIfBlank()

	/** What both sides hold once every action of this update succeeds. */
	val baseline: GroupBaseline get() = GroupBaseline(mergedName, mergedParent, mergedDescription)
}

/**
 * What one account sync session must do to bring the local and server group trees together.
 * Server definitions have their parent normalized: a parent the server does not list is the top level.
 */
data class GroupPlan(
	val adoptions: List<AdoptByName>,
	/** Parents first. */
	val createOnServer: List<GroupDef>,
	/** Parents first. */
	val createLocally: List<ApiGroupDefinition>,
	/** By server depth, parents first. */
	val updates: List<GroupUpdate>,
	/** Deleted locally since they last agreed; children first. */
	val deleteOnServer: List<ApiGroupDefinition>,
	/** Deleted on the server since they last agreed; children first. */
	val deleteLocally: List<GroupDef>,
	val agreed: Map<GroupId, GroupBaseline>,
)

/**
 * Pairs local and server groups by id and applies the three-way rule to each attribute. A group on
 * one side only was created there when it has no baseline, and deleted on the other side when it
 * has one. A local-only group without a baseline first looks for a server-only group with the same
 * parent and name to adopt, unless its id is in [noAdoption].
 */
fun reconcileGroups(
	local: List<GroupDef>,
	server: Collection<ApiGroupDefinition>,
	baselines: Map<GroupId, GroupBaseline>,
	noAdoption: Set<GroupId> = emptySet(),
): GroupPlan {
	val serverIds = server.mapTo(mutableSetOf()) { it.uuid }
	val serverById = server
		.map { if (it.parentId in serverIds) it else it.copy(parentId = null) }
		.associateBy { it.uuid }
	val localById = local.associateBy { it.id }
	val serverDepth = depths(serverById.keys) { serverById[it]?.parentId }
	val localDepth = depths(localById.keys) { localById[it]?.parentId }

	val serverOnly = serverById.values.filter { it.uuid !in localById }
	val localOnly = local.filter { it.id !in serverById }

	val adoptions = mutableListOf<AdoptByName>()
	val claimed = mutableSetOf<GroupId>()
	localOnly
		.filter { it.id !in baselines && it.id !in noAdoption }
		.sortedBy { localDepth[it.id] }
		.forEach { group ->
			val match = serverOnly.firstOrNull { candidate ->
				candidate.uuid !in claimed &&
					candidate.parentId == group.parentId &&
					ProjectsRepository.toLocalSafeName(candidate.name) == group.name
			}
			if (match != null) {
				adoptions += AdoptByName(group, match.uuid)
				claimed += match.uuid
			}
		}
	val adopted = adoptions.mapTo(mutableSetOf()) { it.local.id }

	val createOnServer = localOnly
		.filter { it.id !in baselines && it.id !in adopted }
		.sortedBy { localDepth[it.id] }
	val deleteLocally = localOnly
		.filter { it.id in baselines }
		.sortedByDescending { localDepth[it.id] }
	val createLocally = serverOnly
		.filter { it.uuid !in baselines && it.uuid !in claimed }
		.sortedBy { serverDepth[it.uuid] }
	val deleteOnServer = serverOnly
		.filter { it.uuid in baselines && it.uuid !in claimed }
		.sortedByDescending { serverDepth[it.uuid] }

	val updates = mutableListOf<GroupUpdate>()
	val agreed = mutableMapOf<GroupId, GroupBaseline>()
	local.filter { it.id in serverById }.forEach { group ->
		val remote = serverById.getValue(group.id)
		val baseline = baselines[group.id]
		val update = GroupUpdate(
			local = group,
			server = remote,
			name = nameThreeWay(group.name, remote.name, baseline?.name),
			parent = threeWay(group.parentId, remote.parentId, baseline?.parentId, baseline != null),
			description = threeWay(
				group.description.orNullIfBlank(),
				remote.description.orNullIfBlank(),
				baseline?.description.orNullIfBlank(),
				baseline != null,
			),
		)
		if (update.name == Winner.Agree && update.parent == Winner.Agree && update.description == Winner.Agree) {
			agreed[group.id] = update.baseline
		} else {
			updates += update
		}
	}

	return GroupPlan(
		adoptions = adoptions,
		createOnServer = createOnServer,
		createLocally = createLocally,
		updates = updates.sortedBy { serverDepth[it.server.uuid] },
		deleteOnServer = deleteOnServer,
		deleteLocally = deleteLocally,
		agreed = agreed,
	)
}

internal fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

/** Depth of each id, a top-level group at 1. Bounded so a parent cycle cannot loop forever. */
private fun depths(ids: Set<GroupId>, parentOf: (GroupId) -> GroupId?): Map<GroupId, Int> =
	ids.associateWith { id ->
		var depth = 1
		var parent = parentOf(id)
		while (parent != null && depth <= ids.size) {
			depth++
			parent = parentOf(parent)
		}
		depth
	}
