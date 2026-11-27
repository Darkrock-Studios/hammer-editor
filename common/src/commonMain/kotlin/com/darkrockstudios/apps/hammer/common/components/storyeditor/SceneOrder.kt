package com.darkrockstudios.apps.hammer.common.components.storyeditor

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue

/*
 * Scenes in reading order are the tree's scenes depth first. A "group" here is a run of
 * consecutive scenes sharing a parent, so loose scenes between two groups form a group of their own.
 */

private fun ImmutableTree<SceneItem>.scenesInOrder(): List<TreeValue<SceneItem>> =
	filter { it.value.type == SceneItem.Type.Scene }

/** The scenes in reading order and where [sceneId] falls among them, or null when it is no scene here. */
private fun ImmutableTree<SceneItem>.locate(sceneId: Int): Pair<List<TreeValue<SceneItem>>, Int>? {
	val scenes = scenesInOrder()
	val at = scenes.indexOfFirst { it.value.id == sceneId }
	return if (at < 0) null else scenes to at
}

/** The scene [offset] places from [sceneId] in reading order. */
internal fun ImmutableTree<SceneItem>.sceneAt(sceneId: Int, offset: Int): SceneItem? {
	val (scenes, at) = locate(sceneId) ?: return null
	return scenes.getOrNull(at + offset)?.value
}

internal fun ImmutableTree<SceneItem>.sceneAfter(sceneId: Int): SceneItem? = sceneAt(sceneId, 1)

internal fun ImmutableTree<SceneItem>.sceneBefore(sceneId: Int): SceneItem? = sceneAt(sceneId, -1)

internal fun ImmutableTree<SceneItem>.firstSceneOfNextGroup(sceneId: Int): SceneItem? {
	val (scenes, at) = locate(sceneId) ?: return null
	val parent = scenes[at].parent
	return scenes.drop(at + 1).firstOrNull { it.parent != parent }?.value
}

internal fun ImmutableTree<SceneItem>.firstSceneOfPreviousGroup(sceneId: Int): SceneItem? {
	val (scenes, at) = locate(sceneId) ?: return null
	var i = at - 1
	while (i >= 0 && scenes[i].parent == scenes[at].parent) i--
	if (i < 0) return null
	val previousParent = scenes[i].parent
	while (i > 0 && scenes[i - 1].parent == previousParent) i--
	return scenes[i].value
}
