package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef

internal fun sceneItem(id: Int, type: SceneItem.Type, name: String) =
	SceneItem(projectDef = fakeProjectDef(), type = type, id = id, name = name, order = id)

internal fun sceneNode(item: SceneItem, vararg children: TreeNode<SceneItem>): TreeNode<SceneItem> {
	val treeNode = TreeNode(item)
	children.forEach { treeNode.addChild(it) }
	return treeNode
}
