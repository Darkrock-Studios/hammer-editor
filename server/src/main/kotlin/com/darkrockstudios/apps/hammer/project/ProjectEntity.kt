package com.darkrockstudios.apps.hammer.project

import kotlinx.serialization.Serializable

sealed interface ProjectEntity {
	val id: Long

	@Serializable
	data class SceneEntity(
		override val id: Long,
		val order: Int,
		val name: String,
		val path: List<String>,
		val content: String
	) : ProjectEntity
}