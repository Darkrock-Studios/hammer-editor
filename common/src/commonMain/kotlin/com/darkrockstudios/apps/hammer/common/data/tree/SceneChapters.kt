package com.darkrockstudios.apps.hammer.common.data.tree

import com.darkrockstudios.apps.hammer.common.data.SceneItem

/** A top-level node of the scene tree and the scenes it contributes to the story, in story order. */
data class ChapterScenes(val chapter: SceneItem, val scenes: List<SceneItem>)

/** A top-level scene is a one-scene chapter; a group is flattened depth-first to its descendant scenes. */
fun ImmutableTree<SceneItem>.collectChapters(): List<ChapterScenes> = root.children.map { node ->
	val scenes = if (node.value.type == SceneItem.Type.Scene) {
		listOf(node.value)
	} else {
		node.filter { it.value.type == SceneItem.Type.Scene }.map { it.value }
	}
	ChapterScenes(chapter = node.value, scenes = scenes)
}
