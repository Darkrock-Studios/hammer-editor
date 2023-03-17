package com.darkrockstudios.apps.hammer.common.data.globalsettings

import kotlinx.serialization.Serializable

@Serializable
data class ServerSettings(
    val ssl: Boolean,
    val url: String,
    val userId: Long,
    val installId: String,
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