package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.soywiz.krypto.sha256

object EntityHash {
	fun hashScene(id: Int, order: Int, name: String, type: ApiSceneType, content: String): String {
		return "$id:$order:$name:${type.name}:$content".encodeToByteArray().sha256().base64Url
	}
}