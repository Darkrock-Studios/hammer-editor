package com.darkrockstudios.apps.hammer.common.projectselection

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree

/** One row of the projects list. */
sealed interface ProjectListRow {
	val key: String

	/** [depth] 1 is a top-level group; [projectCount] counts every project beneath it. */
	data class Group(
		val data: GroupData,
		val depth: Int,
		val projectCount: Int,
	) : ProjectListRow {
		override val key: String = "g:${data.definition.id.id}"
	}

	/**
	 * [depth] is the number of groups around the project, 0 at the top level. [number] is 1-based
	 * across the visible projects. [groupPath] names the enclosing groups, and is only filled when
	 * the list is flattened by a filter.
	 */
	data class Project(
		val data: ProjectData,
		val depth: Int,
		val number: Int,
		val groupPath: List<String>,
	) : ProjectListRow {
		// Two copied project folders can share an id until it is healed; the path keeps keys unique.
		override val key: String = "p:${data.definition.id.id}:${data.definition.path.hashCode()}"
	}
}

/**
 * Lays out [projects] (already filtered) under [groups]. At each level groups come first,
 * alphabetically, then projects in [sortMode]. A collapsed group shows only its own row. While a
 * filter is active ([filtered]) there are no group rows: matches list flat, each with the names of
 * the groups it sits in. A project placed in an unknown group shows at the top level.
 */
internal fun buildProjectListRows(
	groups: List<GroupData>,
	projects: List<ProjectData>,
	placement: Map<ProjectId, GroupId?>,
	sortMode: ProjectsSortMode,
	filtered: Boolean,
): List<ProjectListRow> {
	val sorted = applySort(projects, sortMode)
	val tree = ProjectTree(
		groups = groups.map { it.definition },
		projects = projects.map { it.definition },
		placement = placement,
	)
	var number = 0

	if (filtered) {
		return sorted.map { project ->
			val groupId = placement[project.definition.id]?.takeIf { tree.group(it) != null }
			ProjectListRow.Project(project, depth = 0, number = ++number, groupPath = tree.pathNames(groupId))
		}
	}

	val collapsed = groups.filter { it.collapsed }.mapTo(mutableSetOf()) { it.definition.id }
	val groupsByParent = groups.groupBy { it.definition.parentId?.takeIf { id -> tree.group(id) != null } }
	val projectsByParent = sorted.groupBy { project ->
		placement[project.definition.id]?.takeIf { tree.group(it) != null }
	}
	val rows = mutableListOf<ProjectListRow>()

	fun emit(parent: GroupId?, depth: Int, visited: Set<GroupId>) {
		groupsByParent[parent].orEmpty()
			.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.definition.name })
			.forEach { group ->
				val id = group.definition.id
				// A hand-built cycle must not recurse forever.
				if (id in visited) return@forEach
				rows += ProjectListRow.Group(group, depth + 1, projectCount = tree.projectsUnder(id).size)
				if (id !in collapsed) emit(id, depth + 1, visited + id)
			}
		projectsByParent[parent].orEmpty().forEach { project ->
			rows += ProjectListRow.Project(project, depth, number = ++number, groupPath = emptyList())
		}
	}
	emit(parent = null, depth = 0, visited = emptySet())
	return rows
}
