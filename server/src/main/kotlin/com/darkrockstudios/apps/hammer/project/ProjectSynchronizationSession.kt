package com.darkrockstudios.apps.hammer.project

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

data class ProjectSynchronizationSession(
	val userId: Long,
	val projectDef: ProjectDefinition,
	val started: Instant,
	val syncId: String
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
		val EXPIRATION_TIME = 30.seconds//5.minutes
	}
}