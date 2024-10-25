package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.EntityType
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

fun ApiProjectEntity.Type.toEntityType(): EntityType {
	return when (this) {
		ApiProjectEntity.Type.SCENE -> EntityType.Scene
		ApiProjectEntity.Type.NOTE -> EntityType.Note
		ApiProjectEntity.Type.TIMELINE_EVENT -> EntityType.TimelineEvent
		ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> EntityType.EncyclopediaEntry
		ApiProjectEntity.Type.SCENE_DRAFT -> EntityType.SceneDraft
	}
}