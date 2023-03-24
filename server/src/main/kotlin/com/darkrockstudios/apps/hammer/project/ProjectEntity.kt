package com.darkrockstudios.apps.hammer.project

import kotlinx.serialization.Serializable

sealed interface ProjectEntity {
	val type: Type
	val id: Int

	@Serializable
	data class SceneEntity(
		override val type: Type = Type.SCENE,
		override val id: Int,
		val order: Int,
		val name: String,
		val path: List<String>,
		val content: String
	) : ProjectEntity

	enum class Type {
		SCENE
	}
}