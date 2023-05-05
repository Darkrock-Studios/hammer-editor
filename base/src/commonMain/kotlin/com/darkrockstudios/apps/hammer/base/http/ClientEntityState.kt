package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class ClientEntityState(
	val entities: Set<EntityHash>
)

@Serializable
data class EntityHash(
	val id: Int,
	val hash: String
)