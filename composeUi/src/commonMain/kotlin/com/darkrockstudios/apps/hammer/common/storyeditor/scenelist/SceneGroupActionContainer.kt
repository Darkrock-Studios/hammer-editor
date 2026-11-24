package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@Composable
expect fun SceneGroupActionContainer(
	scene: SceneItem,
	demonstrateSwipe: Boolean,
	onSceneAltClick: (scene: SceneItem) -> Unit,
	onSceneRenameClick: (scene: SceneItem) -> Unit,
	onSceneMoveClick: (scene: SceneItem) -> Unit,
	onCreateSceneClick: (scene: SceneItem) -> Unit,
	onCreateGroupClick: (scene: SceneItem) -> Unit,
	/** Applied to the container itself, which holds still while the row slides. */
	modifier: Modifier = Modifier,
	itemContent: @Composable (modifier: Modifier) -> Unit
)
