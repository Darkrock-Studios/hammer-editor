package com.darkrockstudios.apps.hammer.monitoring

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogRingBufferTest {

	private fun line(level: String, logger: String, msg: String) = LogLine(
		timestampMillis = 0,
		levelRank = LogRingBuffer.rankOf(level),
		level = level,
		logger = logger,
		message = msg,
	)

	@Test
	fun `filters by level and query, newest last`() {
		// Tag lines so concurrent app logging into the shared buffer can't pollute the assertions.
		val tag = "RBTEST_${System.nanoTime()}"
		LogRingBuffer.add(line("INFO", "L", "$tag info"))
		LogRingBuffer.add(line("WARN", "L", "$tag warn"))
		LogRingBuffer.add(line("ERROR", "L", "$tag err"))

		assertEquals(3, LogRingBuffer.recent(query = tag).size)

		val warnPlus = LogRingBuffer.recent(minLevel = "WARN", query = tag)
		assertEquals(2, warnPlus.size)
		assertEquals("WARN", warnPlus.first().level)
		assertEquals("ERROR", warnPlus.last().level)

		assertEquals(1, LogRingBuffer.recent(minLevel = "ERROR", query = tag).size)
	}

	@Test
	fun `is bounded to capacity`() {
		LogRingBuffer.clear()
		repeat(LogRingBuffer.CAPACITY + 50) { LogRingBuffer.add(line("INFO", "L", "m$it")) }
		assertEquals(LogRingBuffer.CAPACITY, LogRingBuffer.recent(limit = Int.MAX_VALUE).size)
	}

	@Test
	fun `redacts secrets`() {
		assertEquals(
			"Authorization: Bearer ***",
			RingBufferLogAppender.redactSecrets("Authorization: Bearer abc123.def-456"),
		)
		assertTrue(RingBufferLogAppender.redactSecrets("password=hunter2").contains("***"))
	}
}
