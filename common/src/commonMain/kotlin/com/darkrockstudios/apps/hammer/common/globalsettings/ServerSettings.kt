package com.darkrockstudios.apps.hammer.common.globalsettings

import kotlinx.serialization.Serializable

@Serializable
data class ServerSettings(
    val email: String,
    val url: String,
    val deviceId: String,
    val bearerToken: String?,
    val refreshToken: String?,
)

@Serializable
data class State(
    val nextId: Int,
    val dirty: List<EntityState>
)

@Serializable
data class EntityState(
    val id: Int,
    val originalHash: String
)