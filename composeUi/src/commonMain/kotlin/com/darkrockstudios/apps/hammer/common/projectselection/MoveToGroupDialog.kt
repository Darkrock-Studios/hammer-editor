package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.components.projectselection.MoveTarget
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.compose.FormDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerList
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.leftBorder
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.move_to_group_cancel_button
import com.darkrockstudios.apps.hammer.move_to_group_destination_label
import com.darkrockstudios.apps.hammer.move_to_group_marker
import com.darkrockstudios.apps.hammer.move_to_group_meta_group
import com.darkrockstudios.apps.hammer.move_to_group_meta_project
import com.darkrockstudios.apps.hammer.move_to_group_move_button
import com.darkrockstudios.apps.hammer.move_to_group_no_results
import com.darkrockstudios.apps.hammer.move_to_group_search_placeholder
import com.darkrockstudios.apps.hammer.move_to_group_top_level

fun moveToGroupDestinationTag(id: GroupId?) = "move-to-group-destination-${id?.id ?: "top"}"

private const val MAX_VISIBLE_DESTINATIONS = 8

/** Past this many groups the picker gets a search field. */
private const val SEARCH_THRESHOLD = 8

/** A row of the picker: [group] null is the top level, at [depth] 0. */
internal data class MoveDestination(val group: GroupDef?, val depth: Int)

/**
 * The top level, then every group in tree order, alphabetical among siblings. [excluding] and its
 * descendants are left out, since a group cannot move into itself. A group whose parent is unknown
 * hangs from the top level.
 */
internal fun moveDestinations(groups: List<GroupDef>, excluding: GroupId?): List<MoveDestination> {
	val ids = groups.mapTo(mutableSetOf()) { it.id }
	val byParent = groups.groupBy { it.parentId?.takeIf { id -> id in ids } }
	val destinations = mutableListOf(MoveDestination(group = null, depth = 0))

	fun visit(parent: GroupId?, depth: Int, visited: Set<GroupId>) {
		byParent[parent].orEmpty()
			.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
			.forEach { group ->
				if (group.id == excluding || group.id in visited) return@forEach
				destinations += MoveDestination(group, depth)
				visit(group.id, depth + 1, visited + group.id)
			}
	}
	visit(parent = null, depth = 1, visited = emptySet())
	return destinations
}

/**
 * The target as it is now: a reload or sync may have renamed or moved it since the dialog opened.
 * Falls back to the stored copy when it is no longer listed.
 */
internal fun MoveTarget.current(projects: List<ProjectData>, groups: List<GroupData>): MoveTarget = when (this) {
	is MoveTarget.Project ->
		MoveTarget.Project(projects.firstOrNull { it.definition.id == projectDef.id }?.definition ?: projectDef)

	is MoveTarget.Group ->
		MoveTarget.Group(groups.firstOrNull { it.definition.id == groupDef.id }?.definition ?: groupDef)
}

/** Picks the group, or the top level, that a project or group moves into. */
@Composable
fun MoveToGroupDialog(
	target: MoveTarget,
	groups: List<GroupDef>,
	placement: Map<ProjectId, GroupId?>,
	onMove: (destination: GroupDef?) -> Unit,
	onDismiss: () -> Unit,
) {
	val (name, currentParent, excluding) = when (target) {
		is MoveTarget.Project -> Triple(target.projectDef.name, placement[target.projectDef.id], null)
		is MoveTarget.Group -> Triple(target.groupDef.name, target.groupDef.parentId, target.groupDef.id)
	}
	val destinations = remember(groups, excluding) { moveDestinations(groups, excluding) }
	// Held as the raw id string so it saves without a custom saver.
	var selectedId by rememberSaveable {
		mutableStateOf(currentParent?.takeIf { id -> destinations.any { it.group?.id == id } }?.id)
	}
	val selected = selectedId?.let(::GroupId)
	var query by rememberSaveable { mutableStateOf("") }
	val topLevelLabel = Res.string.move_to_group_top_level.get()
	val visible = remember(destinations, query, topLevelLabel) {
		if (query.isBlank()) {
			destinations
		} else {
			destinations.filter { (it.group?.name ?: topLevelLabel).contains(query.trim(), ignoreCase = true) }
		}
	}
	val canMove = selected != currentParent

	fun submit() {
		if (canMove) onMove(destinations.firstOrNull { it.group?.id == selected }?.group)
	}

	FormDialog(
		visible = true,
		marker = "§ ${Res.string.move_to_group_marker.get().uppercase()}",
		meta = when (target) {
			is MoveTarget.Project -> Res.string.move_to_group_meta_project.get()
			is MoveTarget.Group -> Res.string.move_to_group_meta_group.get()
		}.uppercase(),
		title = name,
		confirmLabel = Res.string.move_to_group_move_button.get(),
		cancelLabel = Res.string.move_to_group_cancel_button.get(),
		onConfirm = ::submit,
		onCancel = onDismiss,
		onDismiss = onDismiss,
		confirmEnabled = canMove,
	) {
		Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
			HdMonoLabel(text = Res.string.move_to_group_destination_label.get())
			if (destinations.size - 1 > SEARCH_THRESHOLD) {
				HdSearchField(
					value = query,
					onValueChange = { query = it },
					placeholder = Res.string.move_to_group_search_placeholder.get(),
					onClear = { query = "" },
					modifier = Modifier.fillMaxWidth(),
				)
			}
			HdPickerList(
				maxVisibleRows = MAX_VISIBLE_DESTINATIONS,
				emptyText = if (visible.isEmpty()) Res.string.move_to_group_no_results.get() else null,
			) {
				items(items = visible, key = { moveToGroupDestinationTag(it.group?.id) }) { destination ->
					DestinationRow(
						destination = destination,
						topLevelLabel = topLevelLabel,
						isSelected = destination.group?.id == selected,
						onSelect = { selectedId = destination.group?.id?.id },
					)
				}
			}
		}
	}
}

@Composable
private fun DestinationRow(
	destination: MoveDestination,
	topLevelLabel: String,
	isSelected: Boolean,
	onSelect: () -> Unit,
) {
	val bg = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
	val accent = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

	HdPickerRow(
		label = destination.group?.name ?: topLevelLabel,
		depth = destination.depth,
		icon = if (destination.group == null) Icons.Outlined.Home else Icons.Filled.Folder,
		modifier = Modifier
			.background(bg)
			.leftBorder(2.dp, accent)
			.clickable(onClick = onSelect)
			.testTag(moveToGroupDestinationTag(destination.group?.id)),
	)
}
