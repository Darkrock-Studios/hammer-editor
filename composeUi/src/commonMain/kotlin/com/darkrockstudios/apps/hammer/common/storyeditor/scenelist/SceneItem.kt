package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.leftBorder
import com.darkrockstudios.apps.hammer.common.data.SceneItem

fun sceneItemTag(id: Int) = "scene-item-$id"

@ExperimentalFoundationApi
@Composable
internal fun SceneItem(
	scene: SceneItem,
	draggable: Modifier,
	depth: Int,
	hasDirtyBuffer: Boolean,
	isSelected: Boolean,
	shouldNux: Boolean,
	onSceneSelected: (SceneItem) -> Unit,
	onSceneDeleteRequest: (SceneItem) -> Unit,
	onSceneRenameRequest: (SceneItem) -> Unit,
	onSceneArchiveRequest: (SceneItem) -> Unit,
	onSceneMoveRequest: (SceneItem) -> Unit,
) {
	val bg = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
	val accent = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

	SceneItemActionContainer(
		scene,
		onSceneDeleteRequest,
		onSceneRenameRequest,
		onSceneArchiveRequest,
		onSceneMoveRequest,
		shouldNux,
	) {
		Box(
			modifier = draggable
				.fillMaxWidth()
				.testTag(sceneItemTag(scene.id))
				.background(bg)
				.leftBorder(2.dp, accent)
				.combinedClickable(onClick = { onSceneSelected(scene) })
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = sceneRowStartPadding(depth),
						end = Ui.Padding.XL,
						top = Ui.Padding.M,
						bottom = Ui.Padding.M,
					),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = scene.name,
					style = MaterialTheme.typography.bodyMedium,
					color = if (isSelected) {
						MaterialTheme.colorScheme.onSurface
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					},
					fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
					modifier = Modifier.weight(1f),
				)
			}

			Unsaved(hasDirtyBuffer)
		}
	}
}

private fun sceneRowStartPadding(depth: Int): Dp =
	Ui.Padding.XXL + (Ui.Padding.XL * (depth - 1).coerceAtLeast(0))
