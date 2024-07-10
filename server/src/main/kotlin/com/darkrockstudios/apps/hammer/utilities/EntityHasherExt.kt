package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher

fun EntityHasher.hashEntity(entity: ApiProjectEntity): String {
	return when (entity) {
		is ApiProjectEntity.SceneEntity -> hashScene(
			id = entity.id,
			name = entity.name,
			order = entity.order,
			path = entity.path,
			type = entity.sceneType,
			content = entity.content,
			outline = entity.outline,
			notes = entity.notes,
		)

		is ApiProjectEntity.SceneDraftEntity -> hashSceneDraft(
			id = entity.id,
			created = entity.created,
			name = entity.name,
			content = entity.content,
		)

		is ApiProjectEntity.NoteEntity -> hashNote(
			id = entity.id,
			created = entity.created,
			content = entity.content,
		)

		is ApiProjectEntity.TimelineEventEntity -> hashTimelineEvent(
			id = entity.id,
			order = entity.order,
			content = entity.content,
			date = entity.date,
		)

		is ApiProjectEntity.EncyclopediaEntryEntity -> hashEncyclopediaEntry(
			id = entity.id,
			name = entity.name,
			entryType = entity.entryType,
			text = entity.text,
			tags = entity.tags,
			image = entity.image,
		)
	}
}