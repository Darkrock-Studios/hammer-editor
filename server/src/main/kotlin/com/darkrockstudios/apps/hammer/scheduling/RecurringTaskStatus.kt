package com.darkrockstudios.apps.hammer.scheduling

import kotlin.time.Instant

/**
 * Point-in-time view of a [RecurringTask]'s liveness and schedule, surfaced to
 * the admin monitoring dashboard.
 *
 * @param running whether the task's loop coroutine is currently alive.
 * @param lastRun start time of the most recent tick (null if it hasn't ticked yet).
 * @param nextRun scheduled start time of the next tick (null while stopped).
 * @param lastTickFailed whether the most recent tick threw.
 * @param lastError message from the most recent failure, if any.
 */
data class RecurringTaskStatus(
	val name: String,
	val running: Boolean,
	val lastRun: Instant?,
	val nextRun: Instant?,
	val lastTickFailed: Boolean,
	val lastError: String?,
)
