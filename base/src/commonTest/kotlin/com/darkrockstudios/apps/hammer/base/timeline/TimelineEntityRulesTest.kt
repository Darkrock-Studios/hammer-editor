package com.darkrockstudios.apps.hammer.base.timeline

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineEntityRulesTest {
	private fun event(timeline: String?) = ApiProjectEntity.TimelineEventEntity(
		id = 1,
		order = 0,
		label = null,
		content = "",
		timeline = timeline,
	)

	@Test
	fun `no timeline is main and passes`() {
		assertNull(TimelineEntityRules.problem(event(timeline = null)))
	}

	@Test
	fun `a well formed unknown id passes`() {
		assertNull(TimelineEntityRules.problem(event(timeline = "a3f09c1e")))
		assertNull(TimelineEntityRules.problem(event(timeline = "Before_the-evening9")))
		assertNull(TimelineEntityRules.problem(event(timeline = "x".repeat(TimelineLimits.MAX_TIMELINE_ID))))
	}

	@Test
	fun `main spelled out is rejected`() {
		assertEquals(TimelineRuleProblem.MainSpelledOut, TimelineEntityRules.problem(event(timeline = TimelineDef.MAIN_ID)))
	}

	@Test
	fun `malformed ids are rejected`() {
		listOf(
			"",
			"   ",
			"x".repeat(TimelineLimits.MAX_TIMELINE_ID + 1),
			"a b",
			"a.b",
			"a/b",
			"café",
		).forEach { id ->
			assertEquals(TimelineRuleProblem.TimelineIdInvalid, TimelineEntityRules.problem(event(timeline = id)), id)
		}
	}

	@Test
	fun `isValidTimelineId accepts main and random ids`() {
		assertTrue(isValidTimelineId(TimelineDef.MAIN_ID))
		repeat(20) { assertTrue(isValidTimelineId(TimelineDef.randomId())) }
		assertFalse(isValidTimelineId(""))
		assertFalse(isValidTimelineId("Main\n"))
	}
}
