package com.darkrockstudios.apps.hammer.utilities

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SharedTimedCallTest {

	@Test
	fun `concurrent callers share one run of the block`() = runTest {
		var runs = 0
		val release = CompletableDeferred<Unit>()
		val call = SharedTimedCall(StandardTestDispatcher(testScheduler)) {
			runs++
			release.await()
			"done"
		}

		val first = async { call.get(5.seconds) }
		val second = async { call.get(5.seconds) }
		testScheduler.runCurrent()
		release.complete(Unit)

		assertEquals("done", first.await())
		assertEquals("done", second.await())
		assertEquals(1, runs)
	}

	@Test
	fun `a hung block times out callers without starting another run`() = runTest {
		var runs = 0
		val call = SharedTimedCall(StandardTestDispatcher(testScheduler)) {
			runs++
			awaitCancellation()
		}

		assertFailsWith<SharedTimedCall.UnavailableException> { call.get(3.seconds) }
		assertFailsWith<SharedTimedCall.UnavailableException> { call.get(3.seconds) }

		assertEquals(1, runs)
		assertFalse(call.isClosed, "a timeout is not a close")
		call.close()
		assertTrue(call.isClosed)
	}

	@Test
	fun `a failure reaches the caller and the next call runs the block again`() = runTest {
		var runs = 0
		val call = SharedTimedCall(StandardTestDispatcher(testScheduler)) {
			if (++runs == 1) error("database down")
			"recovered"
		}

		assertFailsWith<IllegalStateException> { call.get(3.seconds) }

		assertEquals("recovered", call.get(3.seconds))
		assertEquals(2, runs)
	}

	@Test
	fun `a block cancelling itself is a failure, not a closed call`() = runTest {
		val call = SharedTimedCall(StandardTestDispatcher(testScheduler)) {
			throw kotlinx.coroutines.CancellationException("inner timeout")
		}

		assertFailsWith<IllegalStateException> { call.get(3.seconds) }
	}

	@Test
	fun `after close callers fail fast as unavailable`() = runTest {
		val call = SharedTimedCall(StandardTestDispatcher(testScheduler)) { awaitCancellation() }
		val waiting = async { runCatching { call.get(3.seconds) } }
		testScheduler.runCurrent()

		call.close()

		assertFailsWith<SharedTimedCall.UnavailableException> { call.get(3.seconds) }
		assertEquals(SharedTimedCall.UnavailableException::class, waiting.await().exceptionOrNull()!!::class)
	}
}
