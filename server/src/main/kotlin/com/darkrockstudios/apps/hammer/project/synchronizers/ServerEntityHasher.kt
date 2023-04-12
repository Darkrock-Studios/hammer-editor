package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash

fun serverEntityHash(serverEntity: ApiProjectEntity): String {
	return when (serverEntity) {
		is ApiProjectEntity.SceneEntity -> {
			EntityHash.hashScene(
				id = serverEntity.id,
				name = serverEntity.name,
				order = serverEntity.order,
				type = serverEntity.sceneType,
				content = serverEntity.content
			)
		}

		is ApiProjectEntity.NoteEntity ->
			EntityHash.hashNote(
				id = serverEntity.id,
				created = serverEntity.created,
				content = serverEntity.content
			)

		is ApiProjectEntity.TimelineEventEntity -> EntityHash.hashTimelineEvent(
			id = serverEntity.id,
			order = serverEntity.order,
			content = serverEntity.content,
			date = serverEntity.date
		)

		is ApiProjectEntity.EncyclopediaEntryEntity -> EntityHash.hashEncyclopediaEntry(
			id = serverEntity.id,
			name = serverEntity.name,
			entryType = serverEntity.entryType,
			text = serverEntity.text,
			tags = serverEntity.tags,
			image = serverEntity.image,
		)

		is ApiProjectEntity.SceneDraftEntity -> EntityHash.hashSceneDraft(
			id = serverEntity.id,
			name = serverEntity.name,
			created = serverEntity.created,
			content = serverEntity.content
		)
	}
}