package com.darkrockstudios.apps.hammer.base

/** A top-level node of a story's scene tree and the scenes it contributes, in story order. */
data class TreeChapter<N>(val top: N, val scenes: List<N>)

/**
 * How a story splits into chapters, shared by the app's reader mode and the web reader: each
 * top-level node is one chapter. A top-level scene stands alone; a group contributes its
 * descendant scenes depth first, so nested groups are structure rather than chapters.
 */
fun <N> storyChapters(
	topLevel: List<N>,
	children: (N) -> List<N>,
	isScene: (N) -> Boolean,
): List<TreeChapter<N>> = topLevel.map { TreeChapter(it, chapterScenes(it, children, isScene)) }

/** [node] itself when it is a scene, otherwise the scenes below it, depth first. */
fun <N> chapterScenes(node: N, children: (N) -> List<N>, isScene: (N) -> Boolean): List<N> =
	if (isScene(node)) listOf(node) else children(node).flatMap { chapterScenes(it, children, isScene) }
