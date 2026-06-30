package com.darkrockstudios.apps.hammer.scheduling

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RecurringTaskTest {

	private val logger = LoggerFactory.getLogger("test")

	/** While a task is already running, a second start() must not launch a second loop. */
	@Test
	fun `start is idempotent while running`() = runBlocking {
		val entered = AtomicInteger(0)
		val firstEntered = CompletableDeferred<Unit>()
		val gate = CompletableDeferred<Unit>()
		val task = object : RecurringTask("test", logger) {
			override suspend fun tick() {
				entered.incrementAndGet()
				if (!firstEntered.isCompleted) firstEntered.complete(Unit)
				gate.await() // hold the tick in flight so the task stays "running"
			}

			override suspend fun nextDelay(): Duration = 10.milliseconds
		}
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

		task.start(scope)
		withTimeout(5.seconds) { firstEntered.await() }
		task.start(scope) // should no-op: the first loop is still active
		delay(200)

		assertEquals(1, entered.get(), "a second start() launched another loop")
		gate.complete(Unit)
		scope.cancel()
	}

	/** A throwing tick is logged and retried after errorBackoff, not allowed to kill the loop. */
	@Test
	fun `loop keeps running after a tick throws`() = runBlocking {
		val calls = AtomicInteger(0)
		val reachedThree = CompletableDeferred<Unit>()
		val task = object : RecurringTask("test", logger) {
			override suspend fun tick() {
				val n = calls.incrementAndGet()
				if (n == 1) error("boom") // first tick fails
				if (n >= 3 && !reachedThree.isCompleted) reachedThree.complete(Unit)
			}

			override suspend fun nextDelay(): Duration = 5.milliseconds
			override suspend fun errorBackoff(): Duration = 5.milliseconds
		}
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

		task.start(scope)
		withTimeout(5.seconds) { reachedThree.await() }

		assertTrue(calls.get() >= 3, "loop stopped after the failing tick")
		task.stop()
		assertFalse(task.isRunning())
		scope.cancel()
	}

	/** status() reports liveness plus the last and next run times derived from the clock and nextDelay. */
	@Test
	fun `status reports last run and next run`() = runBlocking {
		val now = Instant.parse("2026-01-15T12:00:00Z")
		val fixedClock = object : Clock {
			override fun now(): Instant = now
		}
		val ticked = CompletableDeferred<Unit>()
		val task = object : RecurringTask("test", logger, fixedClock) {
			override suspend fun tick() {
				if (!ticked.isCompleted) ticked.complete(Unit)
			}

			override suspend fun nextDelay(): Duration = 30.minutes
		}
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

		task.start(scope)
		withTimeout(5.seconds) { ticked.await() }

		// After the first tick the loop sets nextRun, then parks in delay(30m).
		var status = task.status()
		withTimeout(5.seconds) {
			while (status.nextRun == null) {
				delay(10)
				status = task.status()
			}
		}

		assertTrue(status.running)
		assertFalse(status.lastTickFailed)
		assertNull(status.lastError)
		assertEquals(now, status.lastRun)
		assertEquals(now + 30.minutes, status.nextRun)

		task.stop()
		assertNull(task.status().nextRun, "nextRun should clear once stopped")
		scope.cancel()
	}

	/** stop() must not return until an in-flight tick has finished. */
	@Test
	fun `stop waits for an in-flight tick`() = runBlocking {
		val inTick = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val task = object : RecurringTask("test", logger) {
			override suspend fun tick() {
				if (!inTick.isCompleted) {
					inTick.complete(Unit)
					release.await()
				}
			}

			override suspend fun nextDelay(): Duration = 10.milliseconds
		}
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

		task.start(scope)
		withTimeout(5.seconds) { inTick.await() }

		val stopper = launch(Dispatchers.Default) { task.stop() }
		delay(200)
		assertTrue(stopper.isActive, "stop() returned while a tick was still running")

		release.complete(Unit)
		stopper.join()
		assertFalse(task.isRunning(), "task still running after stop()")
		scope.cancel()
	}
}
