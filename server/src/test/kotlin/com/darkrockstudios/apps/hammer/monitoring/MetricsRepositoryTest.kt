package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.test.assertEquals

class MetricsRepositoryTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SqliteTestDatabase()
		db.initialize()
		setupKoin()
	}

	private val t = Instant.parse("2026-01-15T00:00:00Z")

	@Test
	fun `endpoint stats merge hour and day buckets and compute percentiles`() = runTest {
		val dao = ApiMetricDao(db)
		val repo = MetricsRepository(dao)

		// Recent hour bucket + an older daily bucket for the same endpoint.
		dao.upsertBucket(t, "HOUR", "/api/a", "GET", 10, 1, 1000, 9, 1, 0, 0, 0, 0, 0)
		dao.upsertBucket(t - 10.days, "DAY", "/api/a", "GET", 5, 0, 250, 5, 0, 0, 0, 0, 0, 0)
		// A different, quieter endpoint to confirm sorting by volume.
		dao.upsertBucket(t, "HOUR", "/api/b", "POST", 1, 0, 5, 1, 0, 0, 0, 0, 0, 0)

		val stats = repo.getEndpointStats(t - 30.days)
		assertEquals(2, stats.size)

		val a = stats.first() // busiest first
		assertEquals("/api/a", a.route)
		assertEquals(15L, a.requestCount)
		assertEquals(1L, a.errorCount)
		assertEquals(83L, a.avgMs)          // (1000 + 250) / 15
		assertEquals(50L, a.p50)            // 14 of 15 are <= 50ms
		assertEquals(100L, a.p95)           // the 15th falls in the <=100ms bin

		val totals = repo.getTotals(t - 30.days)
		assertEquals(16L, totals.requestCount)
		assertEquals(1L, totals.errorCount)
	}
}
