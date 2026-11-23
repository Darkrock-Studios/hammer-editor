package com.darkrockstudios.apps.hammer.common.preview.storyeditor

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.MoveSceneCard
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.MoveSceneDialogState

/** Moving a top-level scene, over a scrim; the card directly, since the animated dialog can't settle in a preview. */
@Preview(widthDp = 720, heightDp = 720)
@Composable
fun ScreenMoveSceneDialogPreview() {
	KoinApplicationPreview {
		PreviewScrim {
			val tree = remember { previewTree() }
			val item = remember(tree) { tree.findBy { it.id == 8 }!!.value }
			val state = remember(tree, item) { MoveSceneDialogState(item, tree) }
			MoveSceneCard(state = state, onConfirm = {}, onCancel = {})
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
