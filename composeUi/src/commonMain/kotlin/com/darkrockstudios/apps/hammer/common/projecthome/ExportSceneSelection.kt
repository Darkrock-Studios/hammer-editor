package com.darkrockstudios.apps.hammer.common.projecthome

import com.darkrockstudios.apps.hammer.common.data.ExportableScene

/** Every selectable (leaf scene) id in [entries]. */
internal fun allSceneIds(entries: List<ExportableScene>): Set<Int> =
	entries.filterNot { it.isGroup }.map { it.id }.toSet()

/**
 * Scene ids of every descendant of [group]: the contiguous entries following it
 * while their depth stays greater than the group's.
 */
internal fun descendantSceneIds(entries: List<ExportableScene>, group: ExportableScene): List<Int> {
	val start = entries.indexOf(group) + 1
	if (start <= 0) return emptyList()
	return entries.asSequence()
		.drop(start)
		.takeWhile { it.depth > group.depth }
		.filterNot { it.isGroup }
		.map { it.id }
		.toList()
}

internal fun isGroupFullySelected(
	entries: List<ExportableScene>,
	selected: Set<Int>,
	group: ExportableScene,
): Boolean {
	val ids = descendantSceneIds(entries, group)
	return ids.isNotEmpty() && selected.containsAll(ids)
}

internal fun toggleScene(selected: Set<Int>, id: Int): Set<Int> =
	if (id in selected) selected - id else selected + id

/**
 * Group rows act as select-all/clear toggles for their subtree; a partially
 * selected group toggles to fully selected. An empty group is inert.
 */
internal fun toggleGroup(
	entries: List<ExportableScene>,
	selected: Set<Int>,
	group: ExportableScene,
): Set<Int> {
	val ids = descendantSceneIds(entries, group)
	if (ids.isEmpty()) return selected
	return if (selected.containsAll(ids)) selected - ids.toSet() else selected + ids
}
