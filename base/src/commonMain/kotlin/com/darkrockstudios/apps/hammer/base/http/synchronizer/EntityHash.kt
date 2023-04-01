package com.darkrockstudios.apps.hammer.base.http.synchronizer

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
}