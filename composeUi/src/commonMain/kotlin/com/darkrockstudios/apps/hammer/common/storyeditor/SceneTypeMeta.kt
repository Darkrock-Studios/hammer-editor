package com.darkrockstudios.apps.hammer.common.storyeditor

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.scene_type_meta_chapter
import com.darkrockstudios.apps.hammer.scene_type_meta_group
import com.darkrockstudios.apps.hammer.scene_type_meta_root
import com.darkrockstudios.apps.hammer.scene_type_meta_scene

/** What a scene item is to the author: a top-level scene or group is a chapter. */
enum class SceneKind { Scene, Group, Chapter, Root }

fun sceneKind(item: SceneItem, isChapter: Boolean): SceneKind = when {
	item.type == SceneItem.Type.Root -> SceneKind.Root
	isChapter -> SceneKind.Chapter
	item.type == SceneItem.Type.Group -> SceneKind.Group
	else -> SceneKind.Scene
}

/** Uppercase type stamp for dialog metas. */
@Composable
internal fun sceneTypeMeta(item: SceneItem, isChapter: Boolean = false): String =
	sceneKindMeta(sceneKind(item, isChapter))

@Composable
internal fun sceneKindMeta(kind: SceneKind): String = when (kind) {
	SceneKind.Scene -> Res.string.scene_type_meta_scene.get()
	SceneKind.Group -> Res.string.scene_type_meta_group.get()
	SceneKind.Chapter -> Res.string.scene_type_meta_chapter.get()
	SceneKind.Root -> Res.string.scene_type_meta_root.get()
}.uppercase()
