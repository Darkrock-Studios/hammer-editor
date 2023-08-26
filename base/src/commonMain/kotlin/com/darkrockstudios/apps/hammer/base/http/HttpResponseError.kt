package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class HttpResponseError(
	/** Message meant for logging */
	val error: String,
	/** A translated message to display to the user */
	val displayMessage: String,
) {
	override fun toString() = displayMessage
}