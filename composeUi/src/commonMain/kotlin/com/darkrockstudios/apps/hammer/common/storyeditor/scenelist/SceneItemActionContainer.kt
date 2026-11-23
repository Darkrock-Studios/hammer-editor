package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.data.SceneItem

/** Whether a row opens its action menu by swiping it sideways, which [SceneItemActionContainer] decides per platform. */
expect val sceneRowSwipeOpensMenu: Boolean

@Composable
expect fun SceneItemActionContainer(
	scene: SceneItem,
	onSceneDeleteClick: (scene: SceneItem) -> Unit,
	onSceneRenameClick: (scene: SceneItem) -> Unit,
	onSceneArchiveClick: (scene: SceneItem) -> Unit,
	onSceneMoveClick: (scene: SceneItem) -> Unit,
	demonstrateSwipe: Boolean = false,
	/** Applied to the container itself, which holds still while the row slides. */
	modifier: Modifier = Modifier,
	itemContent: @Composable (modifier: Modifier) -> Unit,
)