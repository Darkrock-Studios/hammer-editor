package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
	Surface(
		modifier = draggable
			.fillMaxWidth()
			.wrapContentHeight()
			.padding(start = (Ui.Padding.XL + (Ui.Padding.XL * (depth - 1) * 2)))
			.background(if (isSelected) selectionColor() else MaterialTheme.colorScheme.surfaceVariant)
			.combinedClickable(
				onClick = { onSceneSelected(scene) },
			),
		color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
		tonalElevation = if (isSelected) Ui.Elevation.MEDIUM else 0.dp,
		border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant) else null
	) {
		BoxWithConstraints {
			Row(
				modifier = Modifier
					.wrapContentHeight()
					.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					scene.name,
					style = MaterialTheme.typography.bodyLarge,
					modifier = Modifier.weight(1f).padding(start = Ui.Padding.L)
				)
				IconButton(onClick = { onSceneAltClick(scene) }, modifier = Modifier) {
					Icon(
						imageVector = Icons.Filled.Delete,
						contentDescription = "Delete",
						modifier = Modifier.size(18.dp),
					)
				}
			}

			Unsaved(hasDirtyBuffer)
		}
	}
}