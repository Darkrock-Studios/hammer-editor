package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.soywiz.krypto.sha256
import kotlinx.datetime.Instant

object EntityHash {
	fun hashScene(id: Int, order: Int, name: String, type: ApiSceneType, content: String): String {
		return "$id:$order:$name:${type.name}:$content".encodeToByteArray().sha256().base64Url
	}

	fun hashNote(id: Int, created: Instant, content: String): String {
		return "$id:$content:$created".encodeToByteArray().sha256().base64Url
	}

	fun hashTimelineEvent(id: Int, order: Int, content: String, date: String?): String {
		return "$id:$order:$content:${date ?: "null"}".encodeToByteArray().sha256().base64Url
	}

	fun hashEncyclopediaEntry(
		id: Int,
		name: String,
		entityType: String,
		text: String,
		tags: List<String>,
		image: ApiProjectEntity.EncyclopediaEntryEntity.Image?
	): String {
		val tagsStr = tags.joinToString(",")
		val imagePart = if (image != null) {
			"${image.base64}:${image.fileExtension}"
		} else {
			"null"
		}

		return "$id:$name:$entityType:$text:$tagsStr:$imagePart".encodeToByteArray().sha256().base64Url
	}

	fun hashSceneDraft(id: Int, created: Instant, name: String, content: String): String {
		return "$id:$created:$name:$content".encodeToByteArray().sha256().base64Url
	}
}