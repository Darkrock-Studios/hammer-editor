package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineCheckbox
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerList
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.project_home_export_scenes_clear_all
import com.darkrockstudios.apps.hammer.project_home_export_scenes_label
import com.darkrockstudios.apps.hammer.project_home_export_scenes_select_all
import com.darkrockstudios.apps.hammer.project_home_export_scenes_selected

internal fun sceneSelectorSceneTag(id: Int) = "scene-selector-scene-$id"
internal fun sceneSelectorGroupTag(id: Int) = "scene-selector-group-$id"
internal const val SCENE_SELECTOR_MASTER_TOGGLE_TAG = "scene-selector-master-toggle"

/**
 * The project's scenes as a tree to pick from. With [multiple], a group row selects or clears every
 * scene beneath it; without, picking a scene replaces the pick, and picking it again clears it.
 */
@Composable
internal fun SceneSelector(
	entries: List<ExportableScene>,
	selected: Set<Int>,
	multiple: Boolean,
	onSelectionChanged: (Set<Int>) -> Unit,
) {
	val allIds = remember(entries) { allSceneIds(entries) }
	val allSelected = allIds.isNotEmpty() && selected.containsAll(allIds)

	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		if (multiple) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				HdMonoLabel(text = Res.string.project_home_export_scenes_label.get())
				Text(
					text = Res.string.project_home_export_scenes_selected.get(selected.size, allIds.size),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.weight(1f),
				)
				HdHairlineButton(
					label = if (allSelected) {
						Res.string.project_home_export_scenes_clear_all.get()
					} else {
						Res.string.project_home_export_scenes_select_all.get()
					},
					onClick = { onSelectionChanged(if (allSelected) emptySet() else allIds) },
					modifier = Modifier.testTag(SCENE_SELECTOR_MASTER_TOGGLE_TAG),
				)
			}
		}

		HdPickerList {
			items(items = entries, key = { it.id }) { entry ->
				if (entry.isGroup) {
					GroupRow(
						entry = entry,
						fullySelected = isGroupFullySelected(entries, selected, entry),
						selectable = multiple && descendantSceneIds(entries, entry).isNotEmpty(),
						onToggle = { onSelectionChanged(toggleGroup(entries, selected, entry)) },
					)
				} else {
					SceneRow(
						entry = entry,
						isSelected = entry.id in selected,
						multiple = multiple,
						onToggle = {
							onSelectionChanged(
								when {
									multiple -> toggleScene(selected, entry.id)
									entry.id in selected -> emptySet()
									else -> setOf(entry.id)
								}
							)
						},
					)
				}
			}
		}
	}
}

@Composable
private fun GroupRow(
	entry: ExportableScene,
	fullySelected: Boolean,
	selectable: Boolean,
	onToggle: () -> Unit,
) {
	val interaction = if (selectable) {
		Modifier.toggleable(
			value = fullySelected,
			role = Role.Checkbox,
			onValueChange = { onToggle() },
		)
	} else {
		// Nothing beneath it to select, or only one scene may be picked; an inert checkbox would mislead.
		Modifier
	}
	HdPickerRow(
		label = entry.name,
		depth = entry.depth,
		icon = Icons.Filled.Folder,
		modifier = interaction.testTag(sceneSelectorGroupTag(entry.id)),
		trailing = { if (selectable) HdHairlineCheckbox(checked = fullySelected) },
	)
}

@Composable
private fun SceneRow(
	entry: ExportableScene,
	isSelected: Boolean,
	multiple: Boolean,
	onToggle: () -> Unit,
) {
	HdPickerRow(
		label = entry.name,
		depth = entry.depth,
		modifier = Modifier
			.toggleable(
				value = isSelected,
				role = if (multiple) Role.Checkbox else Role.RadioButton,
				onValueChange = { onToggle() },
			)
			.testTag(sceneSelectorSceneTag(entry.id)),
		trailing = { HdHairlineCheckbox(checked = isSelected) },
	)
}
