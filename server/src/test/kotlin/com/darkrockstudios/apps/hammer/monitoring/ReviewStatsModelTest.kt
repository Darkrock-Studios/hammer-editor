package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.ReviewStatusCounts
import com.darkrockstudios.apps.hammer.frontend.formatTurnaround
import com.darkrockstudios.apps.hammer.frontend.reviewStatsModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ReviewStatsModelTest {

	private val day = Instant.parse("2026-01-15T00:00:00Z")

	private fun counts(
		waiting: Long = 0,
		editing: Long = 0,
		expired: Long = 0,
		awaitingAuthor: Long = 0,
		resolved: Long = 0,
		canceled: Long = 0,
		opened: Long = 0,
		returned: Long = 0,
	) = ReviewStatusCounts(
		total = waiting + editing + expired + awaitingAuthor + resolved + canceled,
		waiting = waiting,
		editing = editing,
		expired = expired,
		awaitingAuthor = awaitingAuthor,
		resolved = resolved,
		canceled = canceled,
		opened = opened,
		returned = returned,
	)

	private fun stats(status: ReviewStatusCounts) = ReviewStats(
		requested = WindowCounts(h24 = 1, d7 = 3, d30 = 8),
		returned = WindowCounts(h24 = 0, d7 = 2, d30 = 5),
		resolved = WindowCounts(h24 = 0, d7 = 1, d30 = 4),
		status = status,
		medianTurnaround = 5.hours,
		daily = listOf(
			ReviewDay(day - 1.days, requested = 2, returned = 0, resolved = 1),
			ReviewDay(day, requested = 1, returned = 3, resolved = 0),
		),
	)

	@Test
	fun `status shares split the bar and empty statuses are hidden`() {
		val model = reviewStatsModel(stats(counts(waiting = 1, editing = 3, resolved = 4, opened = 7, returned = 4)))

		assertEquals("12.50", model["reviewWaitingShare"])
		assertEquals("37.50", model["reviewEditingShare"])
		assertEquals("50.00", model["reviewResolvedShare"])
		assertEquals("4", model["reviewResolvedCount"])
		assertEquals(true, model["reviewEditingShown"])
		assertEquals(false, model["reviewCanceledShown"])
		assertEquals("0", model["reviewCanceledCount"])
	}

	@Test
	fun `rates are a share of every request sent`() {
		val model = reviewStatsModel(stats(counts(waiting = 1, editing = 3, resolved = 4, opened = 6, returned = 4)))

		assertEquals(true, model["hasReviews"])
		assertEquals("8", model["reviewsTotal"])
		assertEquals("%.1f%%".format(75.0), model["reviewsOpenedRate"])
		assertEquals("%.1f%%".format(50.0), model["reviewsReturnedRate"])
		assertEquals("5h", model["reviewsTurnaround"])
		assertEquals("3", model["reviewsRequested7d"])
		assertEquals("5", model["reviewsReturned30d"])
		assertEquals("4", model["reviewsResolved30d"])
	}

	@Test
	fun `no requests yet shows the empty state without dividing by zero`() {
		val model = reviewStatsModel(stats(counts()).copy(medianTurnaround = null))

		assertFalse(model["hasReviews"] as Boolean)
		assertEquals("—", model["reviewsOpenedRate"])
		assertEquals("—", model["reviewsTurnaround"])
		assertEquals("0.00", model["reviewWaitingShare"])
	}

	@Test
	fun `chart carries one point per day for each milestone`() {
		val model = reviewStatsModel(stats(counts(waiting = 1)))

		val chart = Json.parseToJsonElement(model["reviewsChartJson"] as String).jsonObject
		assertEquals(listOf("Jan 14", "Jan 15"), chart["labels"]!!.jsonArray.map { it.jsonPrimitive.content })
		assertEquals(listOf(2L, 1L), chart["requested"]!!.jsonArray.map { it.jsonPrimitive.long })
		assertEquals(listOf(0L, 3L), chart["returned"]!!.jsonArray.map { it.jsonPrimitive.long })
		assertEquals(listOf(1L, 0L), chart["resolved"]!!.jsonArray.map { it.jsonPrimitive.long })
	}

	@Test
	fun `turnaround reads in minutes, then hours, then days`() {
		assertEquals("45m", formatTurnaround(45.minutes))
		assertEquals("30h", formatTurnaround(30.hours))
		assertEquals("%.1fd".format(2.5), formatTurnaround(2.days + 12.hours))
		assertTrue(formatTurnaround(10.days).startsWith("10"))
	}
}
