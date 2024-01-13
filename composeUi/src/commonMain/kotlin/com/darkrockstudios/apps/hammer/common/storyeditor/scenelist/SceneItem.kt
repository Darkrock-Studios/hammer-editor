package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.leftBorder
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalFoundationApi
@Composable
internal fun SceneItem(
	scene: SceneItem,
	draggable: Modifier,
	depth: Int,
	hasDirtyBuffer: Boolean,
	isSelected: Boolean,
	onSceneSelected: (SceneItem) -> Unit,
	onSceneAltClick: (SceneItem) -> Unit,
) {
	var modifier = draggable
		.fillMaxWidth()
		.wrapContentHeight()
		.padding(start = (Ui.Padding.L * (depth - 1) * 2))
		.background(if (isSelected) selectionColor() else MaterialTheme.colorScheme.surfaceVariant)
		.combinedClickable(
			onClick = { onSceneSelected(scene) },
		)

	if (depth > 1) {
		modifier = modifier.leftBorder(1.dp, MaterialTheme.colorScheme.outline)
	}

	SceneItemActionContainer(scene, onSceneAltClick) {
		Surface(
			modifier = modifier,
			color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
			tonalElevation = if (isSelected) Ui.Elevation.MEDIUM else 0.dp,
			border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant) else null
		) {
			BoxWithConstraints {
				Row(
					modifier = Modifier
						.padding(Ui.Padding.L)
						.wrapContentHeight()
						.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						scene.name,
						style = MaterialTheme.typography.bodyLarge,
						modifier = Modifier.weight(1f).padding(start = Ui.Padding.L)
					)
				}

				Unsaved(hasDirtyBuffer)
			}
		}
	}
}