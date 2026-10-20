package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.serialization.Serializable

/** A project group: a directory holding projects and other groups. A null [parentId] is the top level. */
@Serializable
data class GroupDef(
	val id: GroupId,
	val name: String,
	val path: HPath,
	val parentId: GroupId?,
	val description: String?,
)

/**
 * The projects directory as a tree. Groups are listed parents first. [placement] maps each project
 * to the group that holds it, null at the top level.
 */
data class ProjectTree(
	val groups: List<GroupDef>,
	val projects: List<ProjectDef>,
	val placement: Map<ProjectId, GroupId?>,
) {
	private val groupsById: Map<GroupId, GroupDef> by lazy { groups.associateBy { it.id } }

	fun group(id: GroupId): GroupDef? = groupsById[id]

	fun parentOf(projectId: ProjectId): GroupDef? = placement[projectId]?.let(::group)

	fun parentOf(group: GroupDef): GroupDef? = group.parentId?.let(::group)

	fun childGroups(parent: GroupId?): List<GroupDef> = groups.filter { it.parentId == parent }

	fun projectsIn(parent: GroupId?): List<ProjectDef> = projects.filter { placement[it.id] == parent }

	/** The group's chain from the top level down to it, empty for an unknown group. */
	fun ancestry(id: GroupId): List<GroupDef> {
		val chain = mutableListOf<GroupDef>()
		var current = group(id)
		// Bounded so a hand-built tree with a cycle cannot loop forever.
		while (current != null && chain.size <= groups.size) {
			chain += current
			current = current.parentId?.let(::group)
		}
		return chain.asReversed()
	}

	/** A top-level group is at depth 1; an unknown group is at 0. */
	fun depthOf(id: GroupId): Int = ancestry(id).size

	fun isSameOrDescendant(id: GroupId, ancestor: GroupId): Boolean = ancestry(id).any { it.id == ancestor }

	/** Levels of groups from [id] down to its deepest descendant group: 1 for a group with no subgroups. */
	fun subtreeHeight(id: GroupId): Int = if (group(id) == null) 0 else heightBelow(id, budget = groups.size)

	private fun heightBelow(id: GroupId, budget: Int): Int =
		1 + (childGroups(id).takeIf { budget > 0 }?.maxOfOrNull { heightBelow(it.id, budget - 1) } ?: 0)

	/** Every project in [id] or any of its descendant groups. */
	fun projectsUnder(id: GroupId): List<ProjectDef> =
		projects.filter { project -> placement[project.id]?.let { isSameOrDescendant(it, id) } == true }

	/** Names from the top level down to [id], empty for the top level itself. */
	fun pathNames(id: GroupId?): List<String> = id?.let { ancestry(it).map(GroupDef::name) } ?: emptyList()

	companion object {
		val EMPTY = ProjectTree(emptyList(), emptyList(), emptyMap())
	}
}
