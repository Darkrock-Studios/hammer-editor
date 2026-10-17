package com.darkrockstudios.apps.hammer.frontend.data

import kotlinx.serialization.Serializable

/** A web sign-in that passed the password and still owes a second factor. */
@Serializable
data class PendingMfaSession(
	val userId: Long,
	/** Epoch milliseconds. */
	val expires: Long,
)
