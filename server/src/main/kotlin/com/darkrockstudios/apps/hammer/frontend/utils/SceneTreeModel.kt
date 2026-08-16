package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.story.SceneHierarchyItem

/** Mustache model rows for the scene-select-tree partial, indent pre-computed. */
fun sceneTreeModel(scenes: List<SceneHierarchyItem>): List<Map<String, Any>> =
	scenes.map { item ->
		mapOf(
			"id" to item.id,
			"name" to item.name,
			"isGroup" to item.isGroup,
			"isScene" to item.isScene,
			"depth" to item.depth,
			"indentPx" to (item.depth * 18),
		)
	}
