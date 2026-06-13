package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.ApiMetricDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class MetricsRepositoryTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SqliteTestDatabase()
		db.initialize()
		setupKoin()
	}

	// Real "now" keeps buckets inside the rollup/retention windows so a stray
	// maintenance tick on the shared metrics table can't purge them.
	private val now = Clock.System.now()

	// Non-/api/* routes so a stray background collector flush can't collide.
	private val routeA = "/test/metrics/a"
	private val routeB = "/test/metrics/b"

	@Test
	fun `endpoint stats merge hour and day buckets and compute percentiles`() = runTest {
		val dao = ApiMetricDao(db)
		val repo = MetricsRepository(dao)

		// Recent hour bucket + an older daily bucket for the same endpoint.
		dao.upsertBucket(now, "HOUR", routeA, "GET", 10, 1, 1000, 9, 1, 0, 0, 0, 0, 0)
		dao.upsertBucket(now - 10.days, "DAY", routeA, "GET", 5, 0, 250, 5, 0, 0, 0, 0, 0, 0)
		// A quieter endpoint, to confirm sorting by volume.
		dao.upsertBucket(now, "HOUR", routeB, "POST", 1, 0, 5, 1, 0, 0, 0, 0, 0, 0)

		val since = now - 30.days
		val stats = repo.getEndpointStats(since).filter { it.route == routeA || it.route == routeB }
		assertEquals(2, stats.size)

		val a = stats.first() // busiest first
		assertEquals(routeA, a.route)
		assertEquals(15L, a.requestCount)
		assertEquals(1L, a.errorCount)
		assertEquals(83L, a.avgMs)          // (1000 + 250) / 15
		assertEquals(50L, a.p50)            // 14 of 15 are <= 50ms
		assertEquals(100L, a.p95)           // the 15th falls in the <=100ms bin

		assertEquals(16L, stats.sumOf { it.requestCount })
		assertEquals(1L, stats.sumOf { it.errorCount })

		// getTotals spans the whole shared table, so just assert ours is included.
		val totals = repo.getTotals(since)
		assertTrue(totals.requestCount >= 16L)
		assertTrue(totals.errorCount >= 1L)
	}
}
