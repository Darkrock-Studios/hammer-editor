package com.darkrockstudios.apps.hammer.common.data

import kotlinx.serialization.Serializable

@Serializable
data class SceneItem(
	val projectDef: ProjectDef,
	val type: Type,
	val id: Int,
	val name: String,
	val order: Int,
) {
	enum class Type(val isCollection: Boolean) {
		Scene(false), Group(true), Root(true)
	}

	val isRootScene: Boolean
		get() = type == Type.Root

	override fun toString(): String {
		val orderStr = order.toString().padStart(3, '0')
		return "$orderStr - $type - $id - $name"
	}

	companion object {
		const val ROOT_ID = 0
	}
}

@Serializable
data class ScenePathSegments(
	val pathSegments: List<Int>
) {
	val depth: Int
		get() = pathSegments.size
}