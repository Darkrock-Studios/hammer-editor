package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class HttpResponseError(
	val error: String,
	val message: String,
)