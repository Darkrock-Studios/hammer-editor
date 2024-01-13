package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@Composable
expect fun SceneGroupActionContainer(
	scene: SceneItem,
	onSceneAltClick: (scene: SceneItem) -> Unit,
	onCreateSceneClick: (scene: SceneItem) -> Unit,
	onCreateGroupClick: (scene: SceneItem) -> Unit,
	itemContent: @Composable (modifier: Modifier) -> Unit
)