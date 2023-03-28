package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.soywiz.krypto.sha256

object SceneHash {
	fun hashScene(id: Int, order: Int, title: String, type: ApiSceneType): String {
		return "$id:$order:$title:${type.name}".encodeToByteArray().sha256().base64Url
	}
}