package com.darkrockstudios.apps.hammer.frontend.data

import kotlinx.serialization.Serializable

/**
 * The private shares this browser has unlocked with their passwords. It never holds a password:
 * each entry is checked against the live share on every request, so a changed password, an
 * expired share or a removed one stops working at once.
 */
@Serializable
data class ShareAccessSession(
	val shares: List<UnlockedShare> = emptyList(),
)

@Serializable
data class UnlockedShare(
	/** The `project_access` row this entry opens. */
	val accessId: Long,
	/** The share password's fingerprint when it was unlocked; see `sharePasswordFingerprint`. */
	val fingerprint: String,
	/** Epoch milliseconds. */
	val expires: Long,
)
