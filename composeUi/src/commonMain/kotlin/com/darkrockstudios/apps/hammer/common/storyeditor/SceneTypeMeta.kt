package com.darkrockstudios.apps.hammer.common.storyeditor

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.scene_type_meta_group
import com.darkrockstudios.apps.hammer.scene_type_meta_root
import com.darkrockstudios.apps.hammer.scene_type_meta_scene

/** Mono meta stamp for a scene item's type, shown in dialog mastheads (`§ RENAME … SCENE`). */
@Composable
internal fun sceneTypeMeta(item: SceneItem): String = when (item.type) {
	SceneItem.Type.Scene -> Res.string.scene_type_meta_scene.get()
	SceneItem.Type.Group -> Res.string.scene_type_meta_group.get()
	SceneItem.Type.Root -> Res.string.scene_type_meta_root.get()
}.uppercase()
