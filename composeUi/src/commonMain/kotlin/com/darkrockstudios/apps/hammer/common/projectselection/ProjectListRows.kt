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
	fun groupOf(project: ProjectData): GroupId? = placement[project.definition.id]?.takeIf { tree.group(it) != null }
	var number = 0

	if (filtered) {
		return sorted.map { project ->
			ProjectListRow.Project(project, depth = 0, number = ++number, groupPath = tree.pathNames(groupOf(project)))
		}
	}

	// Each project counts once toward every group above it.
	val projectCounts = projects.mapNotNull(::groupOf).flatMap(tree::ancestry).groupingBy { it.id }.eachCount()
	val dataById = groups.associateBy { it.definition.id }
	val projectsByParent = sorted.groupBy(::groupOf)
	val rows = mutableListOf<ProjectListRow>()

	fun addProjects(parent: GroupId?, depth: Int) {
		projectsByParent[parent].orEmpty().forEach { project ->
			rows += ProjectListRow.Project(project, depth, number = ++number, groupPath = emptyList())
		}
	}

	// The expanded groups enclosing the current one; a group's projects follow its last subgroup.
	val open = ArrayDeque<GroupId>()
	fun closeTo(depth: Int) {
		while (open.size > depth) addProjects(open.removeLast(), depth = open.size + 1)
	}

	var hiddenBelow = Int.MAX_VALUE
	tree.groupsInTreeOrder().forEach { (group, depth) ->
		if (depth > hiddenBelow) return@forEach
		closeTo(depth - 1)
		val data = dataById.getValue(group.id)
		rows += ProjectListRow.Group(data, depth, projectCount = projectCounts[group.id] ?: 0)
		if (data.collapsed) {
			hiddenBelow = depth
		} else {
			hiddenBelow = Int.MAX_VALUE
			open += group.id
		}
	}
	closeTo(0)
	addProjects(parent = null, depth = 0)
	return rows
}
