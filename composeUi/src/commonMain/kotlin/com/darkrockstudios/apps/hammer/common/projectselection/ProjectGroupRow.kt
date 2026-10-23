package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdIndentStep
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdIndentFor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdIndentRails
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.projects_list_group_collapsed
import com.darkrockstudios.apps.hammer.projects_list_group_content_description
import com.darkrockstudios.apps.hammer.projects_list_group_expanded
import com.darkrockstudios.apps.hammer.projects_list_group_menu_delete
import com.darkrockstudios.apps.hammer.projects_list_group_menu_edit_description
import com.darkrockstudios.apps.hammer.projects_list_group_menu_move
import com.darkrockstudios.apps.hammer.projects_list_group_menu_new_project
import com.darkrockstudios.apps.hammer.projects_list_group_menu_new_subgroup
import com.darkrockstudios.apps.hammer.projects_list_group_menu_rename
import com.darkrockstudios.apps.hammer.projects_list_group_project_count_none
import com.darkrockstudios.apps.hammer.projects_list_group_project_count_one
import com.darkrockstudios.apps.hammer.projects_list_group_project_count_other
import org.jetbrains.compose.resources.StringResource

fun projectGroupRowTag(id: GroupId) = "project-group-${id.id}"

/** Lines a group's chevron up with the number column of the project rows. */
internal val ProjectTreeGutter: Dp = 4.dp + Ui.Padding.XL

/** Everything a group row can do to its group. */
@Immutable
class ProjectGroupActions(
	val onToggle: (GroupDef) -> Unit = {},
	val onNewProject: (GroupDef) -> Unit = {},
	val onNewSubgroup: (GroupDef) -> Unit = {},
	val onRename: (GroupDef) -> Unit = {},
	val onMove: (GroupDef) -> Unit = {},
	val onEditDescription: (GroupDef) -> Unit = {},
	val onDelete: (GroupDef) -> Unit = {},
)

/** One overflow or context menu entry of a group row. */
class ProjectGroupMenuItem(val label: StringResource, val onClick: () -> Unit)

private fun ProjectGroupActions.menuItems(group: GroupDef, depth: Int): List<ProjectGroupMenuItem> =
	listOfNotNull(
		ProjectGroupMenuItem(Res.string.projects_list_group_menu_new_project) { onNewProject(group) },
		ProjectGroupMenuItem(Res.string.projects_list_group_menu_new_subgroup) { onNewSubgroup(group) }
			.takeIf { depth < MAX_GROUP_NESTING_DEPTH },
		ProjectGroupMenuItem(Res.string.projects_list_group_menu_rename) { onRename(group) },
		ProjectGroupMenuItem(Res.string.projects_list_group_menu_move) { onMove(group) },
		ProjectGroupMenuItem(Res.string.projects_list_group_menu_edit_description) { onEditDescription(group) },
		ProjectGroupMenuItem(Res.string.projects_list_group_menu_delete) { onDelete(group) },
	)

/**
 * A group in the projects list, from the scene list's group row: chevron, name, transitive
 * project count, optional italic description, and the group's overflow menu.
 *
 *     ⌄ The Salt Trilogy                  3 PROJECTS  ⋮
 *       Three books about the drowned coast
 *     ┆ ⌄ Abandoned Drafts                1 PROJECT   ⋮
 */
@Composable
fun ProjectGroupRow(
	row: ProjectListRow.Group,
	actions: ProjectGroupActions,
	modifier: Modifier = Modifier,
) {
	val group = row.data.definition
	val collapsed = row.data.collapsed
	val indent = hdIndentFor(row.depth, gutter = ProjectTreeGutter)
	val menuItems = actions.menuItems(group, row.depth)
	val rowDescription = Res.string.projects_list_group_content_description.get(group.name)
	val stateDescription = if (collapsed) {
		Res.string.projects_list_group_collapsed.get()
	} else {
		Res.string.projects_list_group_expanded.get()
	}

	ProjectGroupActionContainer(items = menuItems) {
		Column(
			modifier = modifier
				.fillMaxWidth()
				.hdIndentRails(
					levels = row.depth - 1,
					color = MaterialTheme.colorScheme.outlineVariant,
					gutter = ProjectTreeGutter,
				),
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clickable { actions.onToggle(group) }
					.semantics { contentDescription = rowDescription }
					.testTag(projectGroupRowTag(group.id))
					.padding(start = indent),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(
					imageVector = if (collapsed) {
						Icons.AutoMirrored.Filled.KeyboardArrowRight
					} else {
						Icons.Filled.KeyboardArrowDown
					},
					contentDescription = stateDescription,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(HdIndentStep),
				)

				Column(
					modifier = Modifier
						.weight(1f)
						.padding(start = Ui.Padding.M, top = Ui.Padding.L, bottom = Ui.Padding.L),
					verticalArrangement = Arrangement.spacedBy(2.dp),
				) {
					Text(
						text = group.name,
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.Medium,
						color = MaterialTheme.colorScheme.onSurface,
					)
					group.description?.takeIf { it.isNotBlank() }?.let { description ->
						Text(
							text = description,
							style = MaterialTheme.typography.bodySmall,
							fontStyle = FontStyle.Italic,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}

				HdMonoLabel(
					text = projectCountLabel(row.projectCount),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = Ui.Padding.M),
				)

				ProjectOptionsMenu(items = menuItems.map { it.label }) { clicked ->
					menuItems.first { it.label == clicked }.onClick()
				}
			}

			// A top-level group cuts the full width; a nested one hangs at its own indent.
			HorizontalDivider(
				modifier = Modifier.padding(start = if (row.depth <= 1) 0.dp else indent),
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}
	}
}

@Composable
private fun projectCountLabel(count: Int): String = when (count) {
	0 -> Res.string.projects_list_group_project_count_none.get()
	1 -> Res.string.projects_list_group_project_count_one.get()
	else -> Res.string.projects_list_group_project_count_other.get(count)
}
