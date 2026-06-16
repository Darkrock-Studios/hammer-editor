package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import com.darkrockstudios.apps.hammer.database.ErrorLogDao
import com.darkrockstudios.apps.hammer.database.LoginAttemptDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Exercises the monitoring DAOs against the (embedded Postgres) test database,
 * validating the Postgres-specific SQL: histogram upsert-accumulation, the
 * hour->day rollup with date_trunc, fingerprint dedupe, and retention purges.
 */
class MonitoringDaoTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SqliteTestDatabase()
		db.initialize()
		setupKoin()
	}

	private val base = Instant.parse("2026-01-15T12:00:00Z")

	@Test
	fun `upsert accumulates into an existing bucket`() = runTest {
		val dao = ApiMetricDao(db)
		dao.upsertBucket(base, "HOUR", "/api/x", "POST", 1, 0, 50, 1, 0, 0, 0, 0, 0, 0)
		dao.upsertBucket(base, "HOUR", "/api/x", "POST", 4, 2, 200, 0, 2, 1, 1, 0, 0, 0)

		val rows = dao.getBucketsSince("HOUR", base)
		assertEquals(1, rows.size)
		assertEquals(5L, rows.first().request_count)
		assertEquals(2L, rows.first().error_count)
		assertEquals(250L, rows.first().total_duration_ms)
		assertEquals(1L, rows.first().le_50)
		assertEquals(2L, rows.first().le_100)
	}

	@Test
	fun `hourly buckets roll up into a single daily bucket`() = runTest {
		val dao = ApiMetricDao(db)
		val h0 = Instant.parse("2026-01-10T00:00:00Z")
		val h1 = Instant.parse("2026-01-10T01:00:00Z")
		dao.upsertBucket(h0, "HOUR", "/api/test", "GET", 3, 1, 300, 1, 1, 1, 0, 0, 0, 0)
		dao.upsertBucket(h1, "HOUR", "/api/test", "GET", 2, 0, 100, 2, 0, 0, 0, 0, 0, 0)

		dao.rollupHourToDay(cutoff = base)

		assertTrue(dao.getBucketsSince("HOUR", h0).isEmpty(), "hour buckets removed after rollup")

		val days = dao.getBucketsSince("DAY", h0)
		assertEquals(1, days.size)
		val day = days.first()
		assertEquals(5L, day.request_count)
		assertEquals(1L, day.error_count)
		assertEquals(400L, day.total_duration_ms)
		assertEquals(3L, day.le_50)
	}

	@Test
	fun `purge removes day buckets older than the cutoff`() = runTest {
		val dao = ApiMetricDao(db)
		val old = Instant.parse("2025-01-01T00:00:00Z")
		dao.upsertBucket(old, "DAY", "/api/old", "GET", 1, 0, 10, 1, 0, 0, 0, 0, 0, 0)
		dao.upsertBucket(base, "DAY", "/api/new", "GET", 1, 0, 10, 1, 0, 0, 0, 0, 0, 0)

		dao.deleteDayBucketsBefore(base - 1.days)

		val remaining = dao.getBucketsSince("DAY", Instant.parse("2024-01-01T00:00:00Z"))
		assertEquals(1, remaining.size)
		assertEquals("/api/new", remaining.first().route)
	}

	@Test
	fun `errors dedupe by fingerprint and bump occurrence count`() = runTest {
		val dao = ErrorLogDao(db)
		dao.recordError("fp1", "RuntimeException", "/api/sync", 7, "boom", "stack", 500, base)
		dao.recordError(
			"fp1",
			"RuntimeException",
			"/api/sync",
			7,
			"boom again",
			"stack2",
			500,
			base + 1.hours
		)

		val rows = dao.getRecentErrors(10, 0)
		assertEquals(1, rows.size)
		assertEquals(2L, rows.first().occurrence_count)
		assertEquals("boom again", rows.first().message)
		assertEquals(500, rows.first().status)
		assertEquals(1L, dao.getErrorCount())
	}

	@Test
	fun `errors purge by last seen`() = runTest {
		val dao = ErrorLogDao(db)
		dao.recordError("old", "E", null, null, null, null, 500, base - 10.days)
		dao.recordError("new", "E", null, null, null, null, 500, base)

		dao.deleteErrorsBefore(base - 1.days)

		assertEquals(1L, dao.getErrorCount())
	}

	@Test
	fun `login attempts count only recent failures for an email`() = runTest {
		val dao = LoginAttemptDao(db)
		dao.recordAttempt("user@x.com", "1.2.3.4", false, base)
		dao.recordAttempt("user@x.com", "1.2.3.4", false, base + 1.hours)
		dao.recordAttempt("user@x.com", "1.2.3.4", true, base + 2.hours)   // success: not counted
		dao.recordAttempt("other@x.com", null, false, base)               // different email

		assertEquals(2L, dao.countRecentFailuresByEmail("user@x.com", base - 1.days))
	}

	@Test
	fun `errors to alert respects threshold, recency and notified flag`() = runTest {
		val dao = ErrorLogDao(db)
		repeat(3) {
			dao.recordError(
				"fp1",
				"E",
				"/r",
				null,
				"m",
				"s",
				500,
				base
			)
		}   // 3 occurrences, recent
		dao.recordError(
			"fp2",
			"E",
			"/r2",
			null,
			"m",
			"s",
			500,
			base
		)               // below threshold
		repeat(5) {
			dao.recordError(
				"fpOld",
				"E",
				"/old",
				null,
				"m",
				"s",
				500,
				base - 10.days
			)
		} // outside window

		val toAlert = dao.getErrorsToAlert(minOccurrences = 3, since = base - 1.days)
		assertEquals(1, toAlert.size)
		assertEquals("fp1", toAlert.first().fingerprint)

		dao.markNotified(base, toAlert.first().id)
		assertTrue(dao.getErrorsToAlert(3, base - 1.days).isEmpty(), "notified errors are excluded")
	}

	@Test
	fun `top failing emails ranks failed logins in the window`() = runTest {
		val dao = LoginAttemptDao(db)
		repeat(3) { dao.recordAttempt("a@x.com", "1.1.1.1", false, base) }
		dao.recordAttempt("a@x.com", "1.1.1.1", true, base)          // success: not counted
		dao.recordAttempt("b@x.com", "2.2.2.2", false, base)
		dao.recordAttempt("old@x.com", null, false, base - 10.days)  // outside window

		val top = dao.getTopFailingEmails(since = base - 1.days, limit = 10L)
		assertEquals(2, top.size)
		assertEquals("a@x.com", top.first().email)
		assertEquals(3L, top.first().failures)
	}

	@Test
	fun `login attempts purge by time`() = runTest {
		val dao = LoginAttemptDao(db)
		dao.recordAttempt("a@x.com", null, false, base - 10.days)
		dao.recordAttempt("a@x.com", null, false, base)

		dao.deleteAttemptsBefore(base - 1.days)

		assertEquals(1, dao.getRecentAttempts(10, 0).size)
	}

	@Test
	fun `brute-force emails returns only accounts at or above the failure threshold`() = runTest {
		val dao = LoginAttemptDao(db)
		repeat(10) { dao.recordAttempt("hit@x.com", "1.1.1.1", false, base) }     // == threshold
		repeat(9) { dao.recordAttempt("near@x.com", "1.1.1.1", false, base) }     // below
		dao.recordAttempt("old@x.com", "1.1.1.1", false, base - 10.days)          // outside window

		val breaching = dao.getBruteForceEmails(since = base - 1.hours, failThreshold = 10L)
		assertEquals(1, breaching.size)
		assertEquals("hit@x.com", breaching.first().email)
		assertEquals(10L, breaching.first().failures)
	}

	@Test
	fun `brute-force ips flags a low-volume spray via the distinct-accounts lens`() = runTest {
		val dao = LoginAttemptDao(db)
		// Spray: 6 distinct accounts, 1 failure each — only 6 total, well under the volume
		// threshold, but must still be flagged (this is the case a top-N LIMIT could hide).
		(1..6).forEach { dao.recordAttempt("a$it@x.com", "203.0.113.9", false, base) }
		// Volume: one account hammered 20x from another IP.
		repeat(20) { dao.recordAttempt("victim@x.com", "203.0.113.7", false, base) }
		// Neither lens: 3 failures across 2 accounts from a quiet IP.
		repeat(2) { dao.recordAttempt("q1@x.com", "198.51.100.1", false, base) }
		dao.recordAttempt("q2@x.com", "198.51.100.1", false, base)

		val breaching = dao.getBruteForceIps(since = base - 1.hours, failThreshold = 20L, accountThreshold = 6L)
			.associateBy { it.ip_address }
		assertEquals(2, breaching.size)
		assertEquals(6L, breaching["203.0.113.9"]?.accounts)
		assertEquals(6L, breaching["203.0.113.9"]?.failures)
		assertEquals(20L, breaching["203.0.113.7"]?.failures)
		assertEquals(null, breaching["198.51.100.1"])
	}
}
