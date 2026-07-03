package com.darkrockstudios.apps.hammer.common.storyeditor

import com.darkrockstudios.apps.hammer.common.data.SceneItem

/** Mono meta stamp for a scene item's type, shown in dialog mastheads (`§ RENAME … SCENE`). */
internal fun sceneTypeMeta(item: SceneItem): String = when (item.type) {
	SceneItem.Type.Scene -> "SCENE"
	SceneItem.Type.Group -> "GROUP"
	SceneItem.Type.Root -> "ROOT"
}
