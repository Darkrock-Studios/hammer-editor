package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.ServerConfigDao
import com.darkrockstudios.apps.hammer.database.WhiteListDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WhiteListRepositoryTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var whiteListDao: WhiteListDao
	private lateinit var clock: TestClock

	@BeforeEach
	override fun setup() {
		super.setup()

		db = SharedPostgresTestDatabase()
		db.initialize()
		whiteListDao = WhiteListDao(db)

		clock = TestClock(Clock.System)

		setupKoin()
	}

	private fun createRepo() = WhiteListRepository(whiteListDao, clock)

	@Test
	fun `getWhiteList - all`() = runTest {
		val emails = listOf("a@b.com", "c@d.com")
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }

		val repo = createRepo()
		val result = repo.getWhiteList()

		assertEquals(emails.sorted(), result)
	}

	@Test
	fun `getWhiteList - paginated`() = runTest {
		val emails = (1..25).map { "user$it@example.com" }.sorted()
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }

		val repo = createRepo()
		val result = repo.getWhiteList(page = 1, pageSize = 10)

		assertEquals(emails.drop(10).take(10), result)
	}

	@Test
	fun `getWhiteListCount`() = runTest {
		val emails = listOf("a@b.com", "c@d.com")
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }

		val repo = createRepo()
		val result = repo.getWhiteListCount()

		assertEquals(2L, result)
	}

	@Test
	fun `isOnWhiteList - cleans email`() = runTest {
		whiteListDao.addToWhiteList("test@example.com", Instant.fromEpochSeconds(0), "Test")

		val repo = createRepo()
		val result = repo.isOnWhiteList("  TEST@Example.com  ")

		assertTrue(result)
	}

	@Test
	fun `addToWhiteList - cleans email`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("  TEST@Example.com  ")

		assertTrue(whiteListDao.isWhiteListed("test@example.com", clock.now()))
	}

	@Test
	fun `removeFromWhiteList - cleans email`() = runTest {
		whiteListDao.addToWhiteList("test@example.com", Instant.fromEpochSeconds(0), "Test")

		val repo = createRepo()
		repo.removeFromWhiteList("  TEST@Example.com  ")

		assertFalse(whiteListDao.isWhiteListed("test@example.com", clock.now()))
	}

	@Test
	fun `validateEmail - valid email returns true`() = runTest {
		val repo = createRepo()
		assertTrue(repo.validateEmail("test@example.com"))
	}

	@Test
	fun `validateEmail - invalid email returns false`() = runTest {
		val repo = createRepo()
		assertFalse(repo.validateEmail("not-an-email"))
		assertFalse(repo.validateEmail("missing@"))
		assertFalse(repo.validateEmail("@nodomain.com"))
	}

	@Test
	fun `validateReason - 32 chars or less returns true`() = runTest {
		val repo = createRepo()
		assertTrue(repo.validateReason("Short reason"))
		assertTrue(repo.validateReason("A".repeat(32)))
	}

	@Test
	fun `validateReason - over 32 chars returns false`() = runTest {
		val repo = createRepo()
		assertFalse(repo.validateReason("A".repeat(33)))
	}

	@Test
	fun `isOnWhiteList - entry with no expiry never expires`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("forever@example.com", "Test", expires = null)

		clock.advanceTime(3650.days)

		assertTrue(repo.isOnWhiteList("forever@example.com"))
	}

	@Test
	fun `isOnWhiteList - entry is whitelisted until its expiry passes`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("temp@example.com", "Beta tester", expires = clock.now() + 7.days)

		assertTrue(repo.isOnWhiteList("temp@example.com"), "Should be whitelisted before expiry")

		clock.advanceTime(7.days + 1.minutes)

		// No reaping job has run — enforcement must come from the query itself.
		assertFalse(repo.isOnWhiteList("temp@example.com"), "Should not be whitelisted after expiry")
	}

	@Test
	fun `isOnWhiteList - expired entry still exists until reaped`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("temp@example.com", "Beta tester", expires = clock.now() + 1.days)
		clock.advanceTime(2.days)

		assertEquals(1L, repo.getWhiteListCount(), "Row should survive until the job reaps it")
		assertFalse(repo.isOnWhiteList("temp@example.com"), "But it must not authorize")
	}

	@Test
	fun `addToWhiteList - re-adding an existing email renews its expiry`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("renew@example.com", "Beta tester", expires = clock.now() + 1.days)
		clock.advanceTime(2.days)
		assertFalse(repo.isOnWhiteList("renew@example.com"), "Lapsed before renewal")

		repo.addToWhiteList("renew@example.com", "Beta tester", expires = clock.now() + 30.days)

		assertTrue(repo.isOnWhiteList("renew@example.com"), "Re-adding should renew, not be ignored")
		assertEquals(1L, repo.getWhiteListCount(), "Renewal must not duplicate the row")
	}

	@Test
	fun `addToWhiteList - re-adding can clear an expiry`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("clear@example.com", "Beta tester", expires = clock.now() + 1.days)

		repo.addToWhiteList("clear@example.com", "Friend", expires = null)
		clock.advanceTime(365.days)

		assertTrue(repo.isOnWhiteList("clear@example.com"), "Expiry should have been cleared")
	}

	@Test
	fun `updateExpiry - sets and clears an expiry`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("edit@example.com", "Friend", expires = null)

		repo.updateExpiry("  EDIT@Example.com  ", clock.now() + 1.days)
		clock.advanceTime(2.days)
		assertFalse(repo.isOnWhiteList("edit@example.com"), "Should honour the new expiry")

		repo.updateExpiry("edit@example.com", null)
		assertTrue(repo.isOnWhiteList("edit@example.com"), "Clearing the expiry should restore access")
	}

	@Test
	fun `getExpiredEntries - returns only lapsed entries`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("lapsed@example.com", "Test", expires = clock.now() + 1.days)
		repo.addToWhiteList("current@example.com", "Test", expires = clock.now() + 30.days)
		repo.addToWhiteList("forever@example.com", "Test", expires = null)

		clock.advanceTime(2.days)

		assertEquals(listOf("lapsed@example.com"), repo.getExpiredEntries().map { it.email })
	}

	@Test
	fun `removeExpired - deletes only lapsed entries`() = runTest {
		val repo = createRepo()
		repo.addToWhiteList("lapsed@example.com", "Test", expires = clock.now() + 1.days)
		repo.addToWhiteList("current@example.com", "Test", expires = clock.now() + 30.days)
		repo.addToWhiteList("forever@example.com", "Test", expires = null)

		clock.advanceTime(2.days)
		repo.removeExpired()

		assertEquals(
			listOf("current@example.com", "forever@example.com"),
			repo.getWhiteList(),
		)
	}

	@Test
	fun `getEntry - returns the entry with its expiry, cleaning the email`() = runTest {
		val repo = createRepo()
		val expiry = clock.now() + 30.days
		repo.addToWhiteList("entry@example.com", "Beta tester", expires = expiry)

		val entry = repo.getEntry("  ENTRY@Example.com  ")

		assertEquals("entry@example.com", entry?.email)
		// Postgres stores microseconds, so compare at millisecond granularity.
		assertEquals(expiry.toEpochMilliseconds(), entry?.expires?.toEpochMilliseconds())
	}

	@Test
	fun `getEntry - returns null for an unknown email`() = runTest {
		val repo = createRepo()
		assertEquals(null, repo.getEntry("nobody@example.com"))
	}

	@Test
	fun `validateExpiry - null is valid and past is not`() = runTest {
		val repo = createRepo()

		assertTrue(repo.validateExpiry(null), "Never-expires is valid")
		assertTrue(repo.validateExpiry(clock.now() + 1.days))
		assertFalse(repo.validateExpiry(clock.now() - 1.days), "A past expiry is meaningless")
	}

	@Test
	fun `email search - matches a partial email and excludes the rest`() = runTest {
		seedEmails("alice@example.com", "alicia@example.com", "bob@example.com")

		val repo = createRepo()
		val result = repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "alic")

		assertEquals(
			listOf("alice@example.com", "alicia@example.com"),
			result.map { it.email }.sorted()
		)
	}

	@Test
	fun `email search - matches anywhere in the address, not just the start`() = runTest {
		seedEmails("someone@hammer.ink", "other@example.com")

		val repo = createRepo()
		val result = repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "hammer.ink")

		assertEquals(listOf("someone@hammer.ink"), result.map { it.email })
	}

	@Test
	fun `email search - is case insensitive`() = runTest {
		// Bypasses the repository's cleanEmail so a mixed-case row really lands in the table.
		whiteListDao.addToWhiteList("Alice@Example.com", Instant.fromEpochSeconds(0), "Test")

		val repo = createRepo()

		assertEquals(1, repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "alice").size)
		assertEquals(1, repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "ALICE").size)
		assertEquals(1L, repo.getWhiteListCount("alice"))
	}

	@Test
	fun `email search - honours both sort directions`() = runTest {
		whiteListDao.addToWhiteList("alice.old@example.com", Instant.fromEpochSeconds(1000), "Test")
		whiteListDao.addToWhiteList("alice.new@example.com", Instant.fromEpochSeconds(9000), "Test")
		whiteListDao.addToWhiteList("bob@example.com", Instant.fromEpochSeconds(5000), "Test")

		val repo = createRepo()

		val newestFirst = repo.getWhiteListWithAccountStatus(0, 10, sortOldestFirst = false, emailSearch = "alice")
		val oldestFirst = repo.getWhiteListWithAccountStatus(0, 10, sortOldestFirst = true, emailSearch = "alice")

		assertEquals(listOf("alice.new@example.com", "alice.old@example.com"), newestFirst.map { it.email })
		assertEquals(listOf("alice.old@example.com", "alice.new@example.com"), oldestFirst.map { it.email })
	}

	@Test
	fun `email search - count matches the filter and pages are disjoint`() = runTest {
		seedEmails(*(1..15).map { "alice$it@example.com" }.toTypedArray())
		seedEmails("bob@example.com", "carol@example.com")

		val repo = createRepo()

		assertEquals(15L, repo.getWhiteListCount("alice"))
		assertEquals(17L, repo.getWhiteListCount(), "An absent filter still counts everything")

		val firstPage = repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "alice").map { it.email }
		val secondPage = repo.getWhiteListWithAccountStatus(1, 10, emailSearch = "alice").map { it.email }

		assertEquals(10, firstPage.size)
		assertEquals(5, secondPage.size)
		assertTrue(firstPage.intersect(secondPage.toSet()).isEmpty(), "Pages must not overlap")
	}

	@Test
	fun `email search - underscore is matched literally, not as a wildcard`() = runTest {
		seedEmails("a_b@example.com", "axb@example.com")

		val repo = createRepo()
		val result = repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "a_b")

		assertEquals(listOf("a_b@example.com"), result.map { it.email })
	}

	@Test
	fun `email search - a bare percent matches nothing rather than everything`() = runTest {
		seedEmails("alice@example.com", "bob@example.com")

		val repo = createRepo()

		assertEquals(0L, repo.getWhiteListCount("%"))
		assertTrue(repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "%").isEmpty())
	}

	@Test
	fun `email search - surrounding whitespace is ignored`() = runTest {
		seedEmails("alice@example.com", "bob@example.com")

		val repo = createRepo()
		val result = repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "  alice  ")

		assertEquals(listOf("alice@example.com"), result.map { it.email })
	}

	@Test
	fun `email search - a null or blank filter lists everything`() = runTest {
		seedEmails("alice@example.com", "bob@example.com")

		val repo = createRepo()

		assertEquals(2, repo.getWhiteListWithAccountStatus(0, 10, emailSearch = null).size)
		assertEquals(2, repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "   ").size)
		assertEquals(2L, repo.getWhiteListCount("   "))
	}

	@Test
	fun `paging is stable when every entry shares a date_added`() = runTest {
		// backfillFromAccounts stamps every migrated row with one timestamp, so the sort
		// key alone can't order them and LIMIT/OFFSET needs a unique tiebreaker.
		val emails = (1..15).map { "user%02d@example.com".format(it) }
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }

		val repo = createRepo()
		val firstPage = repo.getWhiteListWithAccountStatus(0, 10).map { it.email }
		val secondPage = repo.getWhiteListWithAccountStatus(1, 10).map { it.email }

		assertEquals(emails.take(10), firstPage, "Ties break on email, so paging is deterministic")
		assertEquals(emails.drop(10), secondPage)
		assertEquals(emails, firstPage + secondPage, "Every entry appears exactly once")
	}

	@Test
	fun `filtered paging is stable when every entry shares a date_added`() = runTest {
		val emails = (1..15).map { "alice%02d@example.com".format(it) }
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }

		val repo = createRepo()
		val firstPage = repo.getWhiteListWithAccountStatus(0, 10, emailSearch = "alice").map { it.email }
		val secondPage = repo.getWhiteListWithAccountStatus(1, 10, emailSearch = "alice").map { it.email }

		assertEquals(emails, firstPage + secondPage, "Every match appears exactly once across pages")
	}

	@Test
	fun `emailSearchPattern - wraps in wildcards and escapes LIKE metacharacters`() {
		assertEquals("%alice%", WhiteListRepository.emailSearchPattern("alice"))
		assertEquals("%alice%", WhiteListRepository.emailSearchPattern("  alice  "))
		assertEquals("%a\\_b%", WhiteListRepository.emailSearchPattern("a_b"))
		assertEquals("%50\\%%", WhiteListRepository.emailSearchPattern("50%"))
		assertEquals("%a\\\\b%", WhiteListRepository.emailSearchPattern("a\\b"))
	}

	private suspend fun seedEmails(vararg emails: String) {
		emails.forEach { whiteListDao.addToWhiteList(it, Instant.fromEpochSeconds(0), "Test") }
	}
}
