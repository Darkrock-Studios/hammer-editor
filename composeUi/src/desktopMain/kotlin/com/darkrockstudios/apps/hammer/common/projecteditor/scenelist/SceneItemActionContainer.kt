package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@Composable
actual fun SceneItemActionContainer(
	scene: SceneItem,
	onSceneAltClick: (scene: SceneItem) -> Unit,
	itemContent: @Composable (modifier: Modifier) -> Unit
) {
	ContextMenuArea(
		items = {
			listOf(
				ContextMenuItem(
					label = "Delete",
					onClick = { onSceneAltClick(scene) }
				)
			)
		},
	) {
		itemContent(Modifier)
	}
}