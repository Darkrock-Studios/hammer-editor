package com.darkrockstudios.apps.hammer.scheduling

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class RecurringTaskRegistryTest {

	private val logger = LoggerFactory.getLogger("test")

	private fun task(taskName: String) = object : RecurringTask(taskName, logger) {
		override suspend fun tick() = Unit
		override suspend fun nextDelay(): Duration = 1.hours
	}

	@Test
	fun `statuses are sorted by name`() {
		val registry = RecurringTaskRegistry()
		registry.register(task("Patreon polling job"))
		registry.register(task("Token maintenance job"))
		registry.register(task("Monitoring maintenance job"))

		assertEquals(
			listOf("Monitoring maintenance job", "Patreon polling job", "Token maintenance job"),
			registry.statuses().map { it.name },
		)
	}

	@Test
	fun `registering the same task twice does not duplicate it`() {
		val registry = RecurringTaskRegistry()
		val job = task("Token maintenance job")

		registry.register(job)
		registry.register(job)

		assertEquals(1, registry.statuses().size)
	}

	@Test
	fun `a never-started task reports not running with no timings`() {
		val registry = RecurringTaskRegistry()
		registry.register(task("Token maintenance job"))

		val status = registry.statuses().single()
		assertEquals(false, status.running)
		assertEquals(null, status.lastRun)
		assertEquals(null, status.nextRun)
	}
}
