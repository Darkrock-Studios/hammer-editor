package com.darkrockstudios.apps.hammer.common.data.tree

import com.darkrockstudios.apps.hammer.base.storyChapters
import com.darkrockstudios.apps.hammer.common.data.SceneItem

/** A top-level node of the scene tree and the scenes it contributes to the story, in story order. */
data class ChapterScenes(val chapter: SceneItem, val scenes: List<SceneItem>)

/** The story's chapters by the rules in base's [storyChapters], which the web reader shares. */
fun ImmutableTree<SceneItem>.collectChapters(): List<ChapterScenes> =
	storyChapters(root.children, { it.children }, { it.value.type == SceneItem.Type.Scene })
		.map { chapter -> ChapterScenes(chapter = chapter.top.value, scenes = chapter.scenes.map { it.value }) }
