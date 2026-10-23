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
 * to the group that holds it, null at the top level. [collapsed] is local display state.
 */
data class ProjectTree(
	val groups: List<GroupDef>,
	val projects: List<ProjectDef>,
	val placement: Map<ProjectId, GroupId?>,
	val collapsed: Set<GroupId> = emptySet(),
) {
	private val groupsById: Map<GroupId, GroupDef> by lazy { groups.associateBy { it.id } }

	fun group(id: GroupId): GroupDef? = groupsById[id]

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

	/** Names from the top level down to [id], empty for the top level itself. */
	fun pathNames(id: GroupId?): List<String> = id?.let { ancestry(it).map(GroupDef::name) } ?: emptyList()

	/**
	 * Every group with its depth, each followed by its subgroups: top-level groups are at depth 1 and
	 * siblings sort by name, ignoring case. A group whose parent is unknown is top level. Each id is
	 * listed once, so a hand-built cycle cannot loop.
	 */
	fun groupsInTreeOrder(): List<Pair<GroupDef, Int>> {
		val byParent = groups.groupBy { it.parentId?.takeIf { id -> group(id) != null } }
		val listed = mutableSetOf<GroupId>()
		val ordered = mutableListOf<Pair<GroupDef, Int>>()

		fun visit(parent: GroupId?, depth: Int) {
			byParent[parent].orEmpty()
				.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
				.forEach { group ->
					if (!listed.add(group.id)) return@forEach
					ordered += group to depth
					visit(group.id, depth + 1)
				}
		}
		visit(parent = null, depth = 1)
		return ordered
	}

	companion object {
		val EMPTY = ProjectTree(emptyList(), emptyList(), emptyMap())
	}
}
