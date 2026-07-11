package com.darkrockstudios.apps.hammer.common.preview.storyeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneTypeMeta
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.MoveSceneDialogBody
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.MoveSceneDialogState
import com.darkrockstudios.apps.hammer.scene_move_dialog_dismiss_button
import com.darkrockstudios.apps.hammer.scene_move_dialog_move_button

/**
 * The move dialog renders inside an animated [androidx.compose.ui.window.Dialog], which the
 * Desktop preview renderer can't settle to a static frame. Preview its [FormDialogScaffold]
 * chrome directly.
 */
@Preview(widthDp = 720, heightDp = 720)
@Composable
fun ScreenMoveSceneDialogPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				val tree = remember { previewTree() }
				val item = remember(tree) { tree.findBy { it.id == 8 }!!.value }
				val state = remember(tree, item) { MoveSceneDialogState(item, tree) }

				FormDialogScaffold(
					marker = "§ MOVE",
					meta = sceneTypeMeta(item),
					title = item.name,
					confirmLabel = Res.string.scene_move_dialog_move_button.get(),
					cancelLabel = Res.string.scene_move_dialog_dismiss_button.get(),
					onConfirm = {},
					onCancel = {},
					confirmEnabled = true,
					body = {
						MoveSceneDialogBody(state = state, onSubmit = {})
					},
				)
			}
		}
	}
}

private fun previewTree(): ImmutableTree<SceneItem> {
	val projectDef = fakeProjectDef()

	fun item(id: Int, type: SceneItem.Type, name: String) =
		SceneItem(projectDef = projectDef, type = type, id = id, name = name, order = id)

	fun node(item: SceneItem, vararg children: TreeNode<SceneItem>): TreeNode<SceneItem> {
		val treeNode = TreeNode(item)
		children.forEach { treeNode.addChild(it) }
		return treeNode
	}

	val tree = Tree<SceneItem>()
	tree.setRoot(
		node(
			item(0, SceneItem.Type.Root, ""),
			node(
				item(1, SceneItem.Type.Group, "Chapter 1 · Down the Rabbit-Hole"),
				node(item(2, SceneItem.Type.Scene, "The Riverbank")),
				node(item(3, SceneItem.Type.Scene, "The Fall")),
			),
			node(
				item(4, SceneItem.Type.Group, "Chapter 2 · The Pool of Tears"),
				node(item(5, SceneItem.Type.Scene, "Growing Pains")),
				node(
					item(6, SceneItem.Type.Group, "Interlude"),
					node(item(7, SceneItem.Type.Scene, "The Mouse's Tale")),
				),
			),
			node(
				item(9, SceneItem.Type.Group, "Chapter 3 · A Caucus-Race"),
			),
			node(item(8, SceneItem.Type.Scene, "A Mad Tea-Party")),
		)
	)
	return tree.toImmutableTree()
}
