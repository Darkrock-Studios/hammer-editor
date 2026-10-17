package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import okio.Path.Companion.toPath

internal val sceneTreeTestProjectDef =
	ProjectDef(ProjectId("6f0b8e52-2a4c-4d1e-9b3a-7c5d1e2f3a42"), "Test", "/test".toPath().toHPath())

internal fun sceneItem(id: Int, type: SceneItem.Type, name: String) =
	SceneItem(projectDef = sceneTreeTestProjectDef, type = type, id = id, name = name, order = id)

internal fun sceneNode(item: SceneItem, vararg children: TreeNode<SceneItem>): TreeNode<SceneItem> {
	val treeNode = TreeNode(item)
	children.forEach { treeNode.addChild(it) }
	return treeNode
}
