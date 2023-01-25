package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.tree.TreeValue

data class SceneSummary(
	val sceneTree: ImmutableTree<SceneItem>,
	val hasDirtyBuffer: Set<Int>
)

fun emptySceneSummary(projectDef: ProjectDef) = SceneSummary(
	sceneTree = ImmutableTree(
		root = TreeValue(
			value = SceneItem(
				projectDef = projectDef,
				type = SceneItem.Type.Root,
				id = SceneItem.ROOT_ID,
				name = "",
				order = 0
			),
			index = 0,
			parent = -1,
			children = emptyList(),
			depth = 0,
			totalChildren = 0
		),
		totalChildren = 1
	),
	hasDirtyBuffer = emptySet()
)