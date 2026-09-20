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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Shared scaffolding for the server's recurring background jobs (token cleanup,
 * monitoring maintenance, plugin sync jobs, ...). A subclass supplies the work
 * ([tick]) and the cadence ([nextDelay]); this base owns the loop, the
 * already-running guard, exception handling, graceful shutdown, and the
 * liveness/timing bookkeeping surfaced as a [RecurringTaskStatus].
 *
 * The loop ticks first, then waits [nextDelay] before the next tick. Both [tick]
 * and [nextDelay] run inside the same try/catch, so a failure in either is
 * logged and retried after [errorBackoff] rather than killing the loop —
 * important for the dynamic-interval case where [nextDelay] reads live config.
 */
abstract class RecurringTask(
	val name: String,
	protected val logger: Logger,
	private val clock: Clock = Clock.System,
) {
	private var job: Job? = null

	// Liveness/timing snapshot. Written only from the single loop coroutine, read
	// from request threads via status(); @Volatile gives those reads visibility.
	@Volatile
	private var lastRun: Instant? = null

	@Volatile
	private var nextRun: Instant? = null

	@Volatile
	private var lastTickFailed: Boolean = false

	@Volatile
	private var lastError: String? = null

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

	/** A point-in-time snapshot of this task's liveness and schedule, for the admin dashboard. */
	fun status(): RecurringTaskStatus = RecurringTaskStatus(
		name = name,
		running = isRunning(),
		lastRun = lastRun,
		nextRun = nextRun,
		lastTickFailed = lastTickFailed,
		lastError = lastError,
	)

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
		nextRun = null
		logger.info("$name stopped")
	}

	private suspend fun loop() {
		while (currentCoroutineContext().isActive) {
			lastRun = clock.now()
			val wait = try {
				tick()
				lastTickFailed = false
				lastError = null
				nextDelay()
			} catch (e: CancellationException) {
				throw e
				// Background loop must survive any tick failure.
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				logger.error("Error in $name loop", e)
				lastTickFailed = true
				lastError = e.message ?: e::class.simpleName
				errorBackoff()
			}
			nextRun = clock.now() + wait
			delay(wait)
		}
	}

	companion object {
		private val DEFAULT_ERROR_BACKOFF = 1.minutes
	}
}
