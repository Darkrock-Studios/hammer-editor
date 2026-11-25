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

internal fun ImmutableTree<SceneItem>.sceneAfter(sceneId: Int): SceneItem? {
	val scenes = scenesInOrder()
	val at = scenes.indexOfFirst { it.value.id == sceneId }
	if (at < 0) return null
	return scenes.getOrNull(at + 1)?.value
}

internal fun ImmutableTree<SceneItem>.sceneBefore(sceneId: Int): SceneItem? {
	val scenes = scenesInOrder()
	val at = scenes.indexOfFirst { it.value.id == sceneId }
	if (at < 0) return null
	return scenes.getOrNull(at - 1)?.value
}

internal fun ImmutableTree<SceneItem>.firstSceneOfNextGroup(sceneId: Int): SceneItem? {
	val scenes = scenesInOrder()
	val at = scenes.indexOfFirst { it.value.id == sceneId }
	if (at < 0) return null
	val parent = scenes[at].parent
	return scenes.drop(at + 1).firstOrNull { it.parent != parent }?.value
}

internal fun ImmutableTree<SceneItem>.firstSceneOfPreviousGroup(sceneId: Int): SceneItem? {
	val scenes = scenesInOrder()
	val at = scenes.indexOfFirst { it.value.id == sceneId }
	if (at < 0) return null
	var i = at - 1
	while (i >= 0 && scenes[i].parent == scenes[at].parent) i--
	if (i < 0) return null
	val previousParent = scenes[i].parent
	while (i > 0 && scenes[i - 1].parent == previousParent) i--
	return scenes[i].value
}
