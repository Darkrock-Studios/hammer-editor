package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

sealed interface ApiProjectEntity {
	val type: Type
	val id: Int

	@Serializable
	data class SceneEntity(
		override val type: Type = Type.SCENE,
		override val id: Int,
		val sceneType: ApiSceneType,
		val order: Int,
		val name: String,
		val path: List<Int>,
		val content: String
	) : ApiProjectEntity

	enum class Type {
		SCENE,
	}
}