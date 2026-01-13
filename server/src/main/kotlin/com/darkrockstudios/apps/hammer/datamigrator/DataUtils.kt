package com.darkrockstudios.apps.hammer.datamigrator

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import kotlinx.serialization.KSerializer

fun getSerializerForType(type: ApiProjectEntity.Type): KSerializer<ApiProjectEntity> {
	return when (type) {
		ApiProjectEntity.Type.NOTE -> ApiProjectEntity.NoteEntity.serializer() as KSerializer<ApiProjectEntity>
		ApiProjectEntity.Type.SCENE -> ApiProjectEntity.SceneEntity.serializer() as KSerializer<ApiProjectEntity>
		ApiProjectEntity.Type.TIMELINE_EVENT -> ApiProjectEntity.TimelineEventEntity.serializer() as KSerializer<ApiProjectEntity>
		ApiProjectEntity.Type.ENCYCLOPEDIA_ENTRY -> ApiProjectEntity.EncyclopediaEntryEntity.serializer() as KSerializer<ApiProjectEntity>
		ApiProjectEntity.Type.SCENE_DRAFT -> ApiProjectEntity.SceneDraftEntity.serializer() as KSerializer<ApiProjectEntity>
	}
}