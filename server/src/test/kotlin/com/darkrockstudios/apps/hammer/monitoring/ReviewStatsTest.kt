package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.database.ReviewRequestDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.review.ReviewStatus
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ReviewStatsTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var dao: ReviewRequestDao
	private lateinit var repo: ReviewStatsRepository

	private val now = Instant.parse("2026-01-15T12:00:00Z")
	private var userId = 0L
	private var projectId = 0L
	private var tokenSeq = 0

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		setupKoin()

		dao = ReviewRequestDao(db)
		repo = ReviewStatsRepository(dao)

		val accounts = db.serverDatabase.accountQueries
		accounts.createAccount("author@example.com", "hash", "secret", false)
		userId = accounts.findAccount("author@example.com").executeAsOne().id
		db.serverDatabase.projectQueries.createProject(userId = userId, name = "Story", uuid = Uuid.random().toString())
		projectId = db.serverDatabase.projectQueries.findProjectByName(userId, "Story").executeAsOne().id
	}

	private suspend fun request(created: Instant, expires: Instant? = null): Long {
		val id = dao.createRequest(
			userId = userId,
			projectId = projectId,
			token = "token-${tokenSeq++}",
			reviewerEmail = "editor@example.com",
			label = "Pass",
			note = null,
			status = ReviewStatus.SENT,
			expires = expires,
		).id
		db.executeAsync("UPDATE review_request SET created = '$created' WHERE id = $id")
		return id
	}

	private suspend fun returned(created: Instant, submitted: Instant): Long {
		val id = request(created)
		dao.markOpened(id, ReviewStatus.OPENED, created)
		dao.markSubmitted(id, submitted)
		return id
	}

	@Test
	fun `status counts bucket every request by where it stands`() = runTest {
		request(now - 1.days)
		request(now - 10.days, expires = now - 1.days)
		dao.markOpened(request(now - 2.days), ReviewStatus.OPENED, now - 1.days)
		request(now - 3.days).also {
			dao.markOpened(it, ReviewStatus.OPENED, now - 2.days)
			dao.touchActivity(it, ReviewStatus.IN_PROGRESS, now - 1.days)
		}
		returned(now - 5.days, now - 2.days)
		dao.markResolved(returned(now - 6.days, now - 3.days), now - 1.days)
		request(now - 4.days).also { dao.updateStatus(it, ReviewStatus.CANCELED) }

		val status = repo.overview(now).status

		assertEquals(7, status.total)
		assertEquals(1, status.waiting)
		assertEquals(2, status.editing)
		assertEquals(1, status.expired)
		assertEquals(1, status.awaitingAuthor)
		assertEquals(1, status.resolved)
		assertEquals(1, status.canceled)
		assertEquals(4, status.opened)
		assertEquals(2, status.returned)
	}

	@Test
	fun `window counts place each milestone by when it happened`() = runTest {
		returned(created = now - 20.days, submitted = now - 2.hours)
		request(now - 3.days)
		dao.markResolved(returned(created = now - 40.days, submitted = now - 35.days), now - 6.days)

		val stats = repo.overview(now)

		assertEquals(WindowCounts(h24 = 0, d7 = 1, d30 = 2), stats.requested)
		assertEquals(WindowCounts(h24 = 1, d7 = 1, d30 = 1), stats.returned)
		assertEquals(WindowCounts(h24 = 0, d7 = 1, d30 = 1), stats.resolved)
	}

	@Test
	fun `median turnaround covers reviews returned in the last 30 days`() = runTest {
		returned(created = now - 10.days, submitted = now - 9.days)
		returned(created = now - 10.days, submitted = now - 7.days)
		returned(created = now - 10.days, submitted = now - 2.days)
		returned(created = now - 60.days, submitted = now - 31.days)

		assertEquals(3.days, repo.overview(now).medianTurnaround)
	}

	@Test
	fun `median turnaround is absent until a review comes back`() = runTest {
		request(now - 1.days)

		assertNull(repo.overview(now).medianTurnaround)
	}

	@Test
	fun `daily series is gapless and counts each milestone on its own day`() = runTest {
		returned(created = now - 2.days, submitted = now)

		val daily = repo.overview(now).daily

		assertEquals(31, daily.size)
		assertEquals(Instant.parse("2025-12-16T00:00:00Z"), daily.first().day)
		assertEquals(Instant.parse("2026-01-15T00:00:00Z"), daily.last().day)
		assertEquals(1, daily.single { it.day == Instant.parse("2026-01-13T00:00:00Z") }.requested)
		assertEquals(1, daily.last().returned)
		assertEquals(2, daily.sumOf { it.requested + it.returned + it.resolved })
	}
}
