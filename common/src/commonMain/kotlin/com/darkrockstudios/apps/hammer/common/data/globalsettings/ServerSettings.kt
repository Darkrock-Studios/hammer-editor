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