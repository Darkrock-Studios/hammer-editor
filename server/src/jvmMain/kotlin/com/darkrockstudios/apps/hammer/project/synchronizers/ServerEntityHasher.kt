package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher

fun serverEntityHash(serverEntity: ApiProjectEntity): String {
	return when (serverEntity) {
		is ApiProjectEntity.SceneEntity -> {
			EntityHasher.hashScene(
				id = serverEntity.id,
				name = serverEntity.name,
				order = serverEntity.order,
				path = serverEntity.path,
				type = serverEntity.sceneType,
				content = serverEntity.content,
				outline = serverEntity.outline,
				notes = serverEntity.notes,
			)
		}

		is ApiProjectEntity.NoteEntity ->
			EntityHasher.hashNote(
				id = serverEntity.id,
				created = serverEntity.created,
				content = serverEntity.content
			)

		is ApiProjectEntity.TimelineEventEntity -> EntityHasher.hashTimelineEvent(
			id = serverEntity.id,
			order = serverEntity.order,
			content = serverEntity.content,
			date = serverEntity.date
		)

		is ApiProjectEntity.EncyclopediaEntryEntity -> EntityHasher.hashEncyclopediaEntry(
			id = serverEntity.id,
			name = serverEntity.name,
			entryType = serverEntity.entryType,
			text = serverEntity.text,
			tags = serverEntity.tags,
			image = serverEntity.image,
		)

		is ApiProjectEntity.SceneDraftEntity -> EntityHasher.hashSceneDraft(
			id = serverEntity.id,
			name = serverEntity.name,
			created = serverEntity.created,
			content = serverEntity.content
		)
	}
}