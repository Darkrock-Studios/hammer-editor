package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerList
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.leftBorder
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.computeMoveRequest
import com.darkrockstudios.apps.hammer.common.data.movePositionCount
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.data.validMoveDestinations
import com.darkrockstudios.apps.hammer.common.util.containsFolded
import com.darkrockstudios.apps.hammer.scene_move_dialog_destination_label
import com.darkrockstudios.apps.hammer.scene_move_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.scene_move_dialog_invalid_position
import com.darkrockstudios.apps.hammer.scene_move_dialog_move_button
import com.darkrockstudios.apps.hammer.scene_move_dialog_title
import com.darkrockstudios.apps.hammer.scene_move_dialog_no_results
import com.darkrockstudios.apps.hammer.scene_move_dialog_position_label
import com.darkrockstudios.apps.hammer.scene_move_dialog_search_placeholder
import com.darkrockstudios.apps.hammer.scene_move_dialog_chapters
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import androidx.compose.foundation.layout.fillMaxSize
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

private fun moveDestinationTag(id: Int) = "move-dialog-destination-$id"

private const val MOVE_DIALOG_POSITION_FIELD_TAG = "move-dialog-position-field"

private const val MAX_VISIBLE_DESTINATIONS = 6

@Stable
internal class MoveSceneDialogState(
	val item: SceneItem,
	initialTree: ImmutableTree<SceneItem>,
) {
	var tree: ImmutableTree<SceneItem> by mutableStateOf(initialTree)
		private set

	val destinations: List<TreeValue<SceneItem>> by derivedStateOf {
		validMoveDestinations(tree, item)
	}

	private val currentParentId: Int? by derivedStateOf {
		tree.findBy { it.id == item.id }?.let { node -> tree[node.parent].value.id }
	}

	var selectedDestId by mutableStateOf(currentParentId ?: destinations.firstOrNull()?.value?.id)
		private set
	var query by mutableStateOf("")

	val selectedNode: TreeValue<SceneItem>?
		get() = destinations.find { it.value.id == selectedDestId }

	val maxPosition: Int by derivedStateOf {
		selectedDestId?.let { movePositionCount(tree, item, it) } ?: 1
	}

	var positionText by mutableStateOf(defaultPosition().toString())

	val positionValid: Boolean
		get() = positionText.toIntOrNull()?.let { it in 1..maxPosition } == true

	fun select(destId: Int) {
		selectedDestId = destId
		positionText = defaultPosition().toString()
	}

	/** Adopts a changed tree, keeping the user's picks when they are still valid. */
	fun updateTree(newTree: ImmutableTree<SceneItem>) {
		if (newTree === tree) return
		tree = newTree
		if (destinations.none { it.value.id == selectedDestId }) {
			selectedDestId = currentParentId ?: destinations.firstOrNull()?.value?.id
			positionText = defaultPosition().toString()
		}
	}

	fun filteredDestinations(topLevelLabel: String): List<TreeValue<SceneItem>> {
		if (query.isBlank()) return destinations
		return destinations.filter { node ->
			val name = if (node.value.isRootScene) topLevelLabel else node.value.name
			name.containsFolded(query)
		}
	}

	fun buildRequest(): MoveRequest? {
		val destId = selectedDestId ?: return null
		val position = positionText.toIntOrNull() ?: return null
		if (!positionValid) return null
		return computeMoveRequest(tree, item, destId, position - 1)
	}

	// The item's current slot when staying in its parent, so an untouched confirm is a
	// no-op; a new parent defaults to its last slot.
	private fun defaultPosition(): Int {
		val itemNode = tree.findBy { it.id == item.id } ?: return maxPosition
		return if (selectedDestId == currentParentId) {
			tree[itemNode.parent].children.indexOfFirst { it.index == itemNode.index } + 1
		} else {
			maxPosition
		}
	}
}

/**
 * Deterministic alternative to drag & drop: pick a destination group from a searchable,
 * indented list of every group in the story, then a 1-based position among its children.
 */
@Composable
internal fun MoveSceneDialog(
	item: SceneItem,
	tree: ImmutableTree<SceneItem>,
	onDismiss: () -> Unit,
	onMove: (MoveRequest) -> Unit,
) {
	val state = remember(item.id) { MoveSceneDialogState(item, tree) }
	LaunchedEffect(state, tree) { state.updateTree(tree) }

	fun submit() {
		val request = state.buildRequest() ?: return
		onMove(request)
		onDismiss()
	}

	AnimatedDialog(
		visible = true,
		onCloseRequest = onDismiss,
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		MoveSceneCard(
			state = state,
			onConfirm = ::submit,
			onCancel = onDismiss,
		)
	}
}

@Composable
internal fun MoveSceneCard(
	state: MoveSceneDialogState,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	FormDialogScaffold(
		title = Res.string.scene_move_dialog_title.get(state.item.name),
		confirmLabel = Res.string.scene_move_dialog_move_button.get(),
		cancelLabel = Res.string.scene_move_dialog_dismiss_button.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
		confirmEnabled = state.selectedNode != null && state.positionValid,
	) {
		MoveSceneDialogBody(state = state, onSubmit = onConfirm)
	}
}

@Composable
internal fun ColumnScope.MoveSceneDialogBody(
	state: MoveSceneDialogState,
	onSubmit: () -> Unit,
) {
	val topLevelLabel = Res.string.scene_move_dialog_chapters.get()

	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		HdMonoLabel(text = Res.string.scene_move_dialog_destination_label.get())
		HdSearchField(
			value = state.query,
			onValueChange = { state.query = it },
			placeholder = Res.string.scene_move_dialog_search_placeholder.get(),
			onClear = { state.query = "" },
			modifier = Modifier.fillMaxWidth(),
		)
		DestinationList(
			destinations = state.filteredDestinations(topLevelLabel),
			topLevelLabel = topLevelLabel,
			selectedDestId = state.selectedDestId,
			onSelect = state::select,
		)
	}

	HdHairlineField(
		value = state.positionText,
		onValueChange = { text -> state.positionText = text.filter { it.isDigit() } },
		label = Res.string.scene_move_dialog_position_label.get(state.maxPosition),
		error = if (state.positionValid) null else Res.string.scene_move_dialog_invalid_position.get(
			state.maxPosition
		),
		onImeAction = onSubmit,
		testTag = MOVE_DIALOG_POSITION_FIELD_TAG,
		imeAction = ImeAction.Done,
		capitalization = KeyboardCapitalization.None,
	)
}

@Composable
private fun DestinationList(
	destinations: List<TreeValue<SceneItem>>,
	topLevelLabel: String,
	selectedDestId: Int?,
	onSelect: (Int) -> Unit,
) {
	HdPickerList(
		maxVisibleRows = MAX_VISIBLE_DESTINATIONS,
		emptyText = if (destinations.isEmpty()) Res.string.scene_move_dialog_no_results.get() else null,
	) {
		items(items = destinations, key = { it.value.id }) { node ->
			DestinationRow(
				node = node,
				topLevelLabel = topLevelLabel,
				isSelected = node.value.id == selectedDestId,
				onSelect = onSelect,
			)
		}
	}
}

@Composable
private fun DestinationRow(
	node: TreeValue<SceneItem>,
	topLevelLabel: String,
	isSelected: Boolean,
	onSelect: (Int) -> Unit,
) {
	val isTopLevel = node.value.isRootScene
	val bg = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
	val accent = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

	HdPickerRow(
		label = if (isTopLevel) topLevelLabel else node.value.name,
		depth = node.depth,
		// Only groups directly under the top level are chapters.
		icon = when (node.depth) {
			0 -> null
			1 -> Icons.AutoMirrored.Outlined.MenuBook
			else -> Icons.Filled.Folder
		},
		modifier = Modifier
			.background(bg)
			.leftBorder(2.dp, accent)
			.clickable { onSelect(node.value.id) }
			.testTag(moveDestinationTag(node.value.id)),
		trailing = { HdMonoLabel(text = node.children.size.toString()) },
	)
}
