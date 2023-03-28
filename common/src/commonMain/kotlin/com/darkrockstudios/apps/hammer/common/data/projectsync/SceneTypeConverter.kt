package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.common.data.SceneItem

fun SceneItem.Type.toApiType(): ApiSceneType {
	return when (this) {
		SceneItem.Type.Scene -> ApiSceneType.Scene
		SceneItem.Type.Group -> ApiSceneType.Group
		else -> throw IllegalStateException("Unknown type $this")
	}
}


fun ApiSceneType.toSceneType(): SceneItem.Type {
	return when (this) {
		ApiSceneType.Scene -> SceneItem.Type.Scene
		ApiSceneType.Group -> SceneItem.Type.Group
	}
}