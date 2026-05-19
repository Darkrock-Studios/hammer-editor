package com.darkrockstudios.apps.hammer.common.data

import androidx.compose.runtime.Immutable
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class SceneSummary(
	val sceneTree: ImmutableTree<SceneItem>,
	val hasDirtyBuffer: PersistentSet<Int>
)

fun rootSceneNode(projectDef: ProjectDef) = SceneItem(
	projectDef = projectDef,
	type = SceneItem.Type.Root,
	id = SceneItem.ROOT_ID,
	name = "",
	order = 0
)

fun emptySceneSummary(projectDef: ProjectDef) = SceneSummary(
	sceneTree = ImmutableTree(
		root = TreeValue(
			value = rootSceneNode(projectDef),
			index = 0,
			parent = -1,
			children = persistentListOf(),
			depth = 0,
			totalChildren = 0
		),
		totalChildren = 1
	),
	hasDirtyBuffer = persistentSetOf()
)