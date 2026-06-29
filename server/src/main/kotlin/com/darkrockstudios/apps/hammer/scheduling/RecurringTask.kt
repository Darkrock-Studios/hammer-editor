package com.darkrockstudios.apps.hammer.scheduling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Shared scaffolding for the server's recurring background jobs (token cleanup,
 * monitoring maintenance, Patreon polling, ...). A subclass supplies the work
 * ([tick]) and the cadence ([nextDelay]); this base owns the loop, the
 * already-running guard, exception handling, and graceful shutdown.
 *
 * The loop ticks first, then waits [nextDelay] before the next tick. Both [tick]
 * and [nextDelay] run inside the same try/catch, so a failure in either is
 * logged and retried after [errorBackoff] rather than killing the loop —
 * important for the dynamic-interval case where [nextDelay] reads live config.
 */
abstract class RecurringTask(
	private val name: String,
	protected val logger: Logger,
) {
	private var job: Job? = null

	/** One unit of work. Public so tests can drive it deterministically. */
	abstract suspend fun tick()

	/**
	 * How long to wait before the next [tick]. Read live config here for a
	 * dynamic interval; return a constant for a fixed one.
	 */
	protected abstract suspend fun nextDelay(): Duration

	/**
	 * Delay used after a tick (or [nextDelay]) throws, before looping again.
	 * MUST NOT throw — return a constant so a failed config read can't wedge the
	 * loop. Defaults to a fixed fallback.
	 */
	protected open suspend fun errorBackoff(): Duration = DEFAULT_ERROR_BACKOFF

	fun isRunning(): Boolean = job?.isActive == true

	fun start(scope: CoroutineScope) {
		if (job?.isActive == true) {
			logger.info("$name already running")
			return
		}
		job = scope.launch {
			logger.info("Starting $name")
			loop()
		}
	}

	/** Cancels the loop and waits for any in-flight tick to finish, so no tick outlives the caller. */
	suspend fun stop() {
		job?.cancelAndJoin()
		job = null
		logger.info("$name stopped")
	}

	private suspend fun loop() {
		while (currentCoroutineContext().isActive) {
			val wait = try {
				tick()
				nextDelay()
			} catch (e: CancellationException) {
				throw e
				// Background loop must survive any tick failure.
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				logger.error("Error in $name loop", e)
				errorBackoff()
			}
			delay(wait)
		}
	}

	companion object {
		private val DEFAULT_ERROR_BACKOFF = 1.minutes
	}
}
