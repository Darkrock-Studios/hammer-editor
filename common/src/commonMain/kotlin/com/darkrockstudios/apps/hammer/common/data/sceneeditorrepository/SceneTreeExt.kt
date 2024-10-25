package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode

inline fun Tree<SceneItem>.findById(scene: SceneItem): TreeNode<SceneItem> = findById(scene.id)
inline fun Tree<SceneItem>.findById(id: Int): TreeNode<SceneItem> = find { it.id == id }