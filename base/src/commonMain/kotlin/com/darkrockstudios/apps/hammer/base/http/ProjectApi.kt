package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class HasProjectResponse(
    val exists: Boolean
)

@Serializable
data class SaveSceneResponse(
    val saved: Boolean
)