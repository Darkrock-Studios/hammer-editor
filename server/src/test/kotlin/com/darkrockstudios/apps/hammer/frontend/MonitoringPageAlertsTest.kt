package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.monitoring.EndpointStat
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonitoringPageAlertsTest {

	private fun stat(route: String, requests: Long, errors: Long) = EndpointStat(
		route = route, method = "GET", requestCount = requests, errorCount = errors,
		avgMs = 100L, p50 = 80L, p95 = 200L, p99 = 300L,
	)

	@Test
	fun `below request floor is never alerted even at 100 percent error rate`() {
		val alerts = deriveAlerts(listOf(stat("/api/fragile", 19L, 19L)))
		assertTrue(alerts.isEmpty())
	}

	@Test
	fun `at floor with error rate exactly at threshold boundary is not alerted`() {
		// 5/20 = exactly 25% — threshold is strict >, so this must NOT fire
		val alerts = deriveAlerts(listOf(stat("/api/route", 20L, 5L)))
		assertTrue(alerts.isEmpty())
	}

	@Test
	fun `above threshold triggers alert with correct fields`() {
		// 6/20 = 30% > 25%
		val alerts = deriveAlerts(listOf(stat("/api/route", 20L, 6L)))
		assertEquals(1, alerts.size)
		val alert = alerts.first()
		assertEquals("warning", alert["severity"])
		assertEquals("/api/route", alert["route"])
		// Rate formatting is locale-dependent ("30.0%" vs "30,0%"); assert the stable part.
		val detail = alert["detail"] as String
		assertTrue(detail.contains("of 20 requests failed"), detail)
		assertEquals("/admin/monitoring/errors?route=%2Fapi%2Froute", alert["href"])
	}

	@Test
	fun `higher error rate sorts before lower error rate`() {
		val stats = listOf(
			stat("/api/bad", 100L, 30L),   // 30%
			stat("/api/worse", 100L, 50L), // 50%
		)
		val alerts = deriveAlerts(stats)
		assertEquals(2, alerts.size)
		assertEquals("/api/worse", alerts[0]["route"])
		assertEquals("/api/bad", alerts[1]["route"])
	}

	@Test
	fun `endpoint below floor is excluded even when surrounded by alerting endpoints`() {
		val stats = listOf(
			stat("/api/active", 100L, 40L), // 40% → should alert
			stat("/api/quiet", 5L, 5L),     // 100% but only 5 req → must NOT alert
		)
		val alerts = deriveAlerts(stats)
		assertEquals(1, alerts.size)
		assertEquals("/api/active", alerts.first()["route"])
	}

	@Test
	fun `href contains URL-encoded route`() {
		val alerts = deriveAlerts(listOf(stat("/api/user/{id}/sync", 20L, 6L)))
		val href = alerts.first()["href"] as String
		assertEquals("/admin/monitoring/errors?route=%2Fapi%2Fuser%2F%7Bid%7D%2Fsync", href)
	}
}
