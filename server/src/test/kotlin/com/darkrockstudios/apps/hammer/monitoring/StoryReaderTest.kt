package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.PublishedStoryReaderDao
import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Exercises the best-effort unique-reader metric: the collector's cookieless
 * visitor-hash dedup and daily-salt rotation, plus the DAO/repository distinct
 * counts, windowing, and retention purge against the test database.
 */
class StoryReaderTest : BaseTest() {

	private lateinit var db: SqliteTestDatabase

	private val baseNow = Instant.parse("2026-01-15T12:00:00Z")
	private var clockNow = baseNow
	private val clock = object : Clock { override fun now() = clockNow }

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SqliteTestDatabase()
		db.initialize()
		clockNow = baseNow
		setupKoin()
	}

	@Test
	fun `distinct visitors collapse per day and split on ip, user-agent, or story`() {
		val collector = StoryReaderCollector(clock)

		// Same visitor of the same story in the same day collapses to one key.
		collector.record(projectId = 1L, clientIp = "1.1.1.1", userAgent = "UA-A")
		collector.record(projectId = 1L, clientIp = "1.1.1.1", userAgent = "UA-A")
		// A different IP, user-agent, or story is a distinct reader.
		collector.record(projectId = 1L, clientIp = "2.2.2.2", userAgent = "UA-A")
		collector.record(projectId = 1L, clientIp = "1.1.1.1", userAgent = "UA-B")
		collector.record(projectId = 2L, clientIp = "1.1.1.1", userAgent = "UA-A")

		assertEquals(4, collector.drainToKeys().size)
	}

	@Test
	fun `the same visitor hashes differently across days so they can't be linked`() {
		val collector = StoryReaderCollector(clock)

		collector.record(projectId = 1L, clientIp = "1.1.1.1", userAgent = "UA-A")
		val day1 = collector.drainToKeys().single()

		clockNow = baseNow + 1.days
		collector.record(projectId = 1L, clientIp = "1.1.1.1", userAgent = "UA-A")
		val day2 = collector.drainToKeys().single()

		assertNotEquals(day1.visitorHash, day2.visitorHash)
		assertNotEquals(day1.dayBucket, day2.dayBucket)
	}

	@Test
	fun `a disabled collector records nothing`() {
		val collector = StoryReaderCollector(clock)
		collector.setCollecting(false)

		collector.record(projectId = 1L, clientIp = "1.1.1.1", userAgent = "UA-A")

		assertEquals(0, collector.drainToKeys().size)
	}

	@Test
	fun `all-time total counts per story and aggregate windows span all stories`() = runTest {
		val repo = StoryReaderRepository(PublishedStoryReaderDao(db))
		val today = truncateToDay(baseNow)

		repo.recordKeys(
			listOf(
				ReaderKey(projectId = 1L, dayBucket = today, visitorHash = "p1v1"),
				ReaderKey(projectId = 1L, dayBucket = today, visitorHash = "p1v1"), // idempotent
				ReaderKey(projectId = 1L, dayBucket = today, visitorHash = "p1v2"),
				ReaderKey(projectId = 2L, dayBucket = today, visitorHash = "p2v1"),
			)
		)

		assertEquals(2L, repo.totalReadersForProject(1L))
		assertEquals(1L, repo.totalReadersForProject(2L))
		assertEquals(3L, repo.readerCounts(baseNow).h24)
	}

	@Test
	fun `purge rolls purged days into the all-time total`() = runTest {
		val dao = PublishedStoryReaderDao(db)
		val repo = StoryReaderRepository(dao)
		val recent = truncateToDay(baseNow)
		val old = truncateToDay(baseNow - 40.days)

		dao.recordReader(1L, recent, "recent")
		dao.recordReader(1L, old, "old1")
		dao.recordReader(1L, old, "old2")
		assertEquals(3L, repo.totalReadersForProject(1L))

		repo.purgeBefore(baseNow - 7.days)

		// The two old days were deleted from detail but folded into the lifetime total.
		assertEquals(1L, dao.countReadersForProjectSince(1L, baseNow - 60.days))
		assertEquals(3L, repo.totalReadersForProject(1L))
	}

	@Test
	fun `daily readers returns one bucket per day`() = runTest {
		val repo = StoryReaderRepository(PublishedStoryReaderDao(db))
		val today = truncateToDay(baseNow)
		val threeDaysAgo = truncateToDay(baseNow - 3.days)

		repo.recordKeys(
			listOf(
				ReaderKey(1L, today, "a"),
				ReaderKey(1L, today, "b"),
				ReaderKey(1L, threeDaysAgo, "c"),
			)
		)

		val daily = repo.dailyReadersForProject(1L, baseNow - 30.days)

		assertEquals(2, daily.size)
		assertEquals(3L, daily.sumOf { it.count })
	}

	@Test
	fun `windows exclude rows older than the cutoff and purge deletes them`() = runTest {
		val dao = PublishedStoryReaderDao(db)
		val recent = truncateToDay(baseNow)
		val old = truncateToDay(baseNow - 40.days)

		dao.recordReader(1L, recent, "recent")
		dao.recordReader(1L, old, "old")

		assertEquals(1L, dao.countReadersForProjectSince(1L, baseNow - 24.hours))
		assertEquals(2L, dao.countReadersForProjectSince(1L, baseNow - 60.days))

		dao.deleteReadersBefore(baseNow - 7.days)
		assertEquals(1L, dao.countReadersForProjectSince(1L, baseNow - 60.days))
	}

	private fun truncateToDay(instant: Instant): Instant {
		val dayMillis = 24L * 60 * 60 * 1000
		return Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() / dayMillis * dayMillis)
	}
}
