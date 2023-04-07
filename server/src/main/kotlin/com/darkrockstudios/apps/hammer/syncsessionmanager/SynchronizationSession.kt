package com.darkrockstudios.apps.hammer.syncsessionmanager

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

abstract class SynchronizationSession(
	open val userId: Long,
	open val started: Instant,
	open val syncId: String
) {
	private var lastAccessed: Instant = started

	fun updateLastAccessed(clock: Clock) {
		lastAccessed = clock.now()
	}

	fun isExpired(clock: Clock): Boolean {
		val now = clock.now()
		val timeSinceLastAccess = now - lastAccessed
		return timeSinceLastAccess > EXPIRATION_TIME
	}

	companion object {
		val EXPIRATION_TIME = 2.minutes//30.seconds
	}
}