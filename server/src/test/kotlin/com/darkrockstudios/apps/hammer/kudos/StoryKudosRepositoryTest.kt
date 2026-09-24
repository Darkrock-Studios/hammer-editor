package com.darkrockstudios.apps.hammer.kudos

import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.StoryKudosDao
import com.darkrockstudios.apps.hammer.database.UserDataPurgeDao
import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.kudos.KudosKind.CHARACTERS
import com.darkrockstudios.apps.hammer.kudos.KudosKind.DIALOGUE
import com.darkrockstudios.apps.hammer.kudos.KudosKind.ENDING
import com.darkrockstudios.apps.hammer.kudos.KudosKind.MADE_ME_LAUGH
import com.darkrockstudios.apps.hammer.kudos.KudosKind.MADE_ME_THINK
import com.darkrockstudios.apps.hammer.kudos.KudosKind.MOVED_ME
import com.darkrockstudios.apps.hammer.kudos.KudosKind.PLOT
import com.darkrockstudios.apps.hammer.kudos.KudosKind.PROSE
import com.darkrockstudios.apps.hammer.utils.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class StoryKudosRepositoryTest : BaseTest() {

	private lateinit var db: SharedPostgresTestDatabase
	private lateinit var accountDao: AccountDao
	private lateinit var repo: StoryKudosRepository

	private var authorId = 0L
	private var projectId = 0L
	private val readers = mutableListOf<Long>()

	@BeforeEach
	override fun setup() {
		super.setup()
		db = SharedPostgresTestDatabase()
		db.initialize()
		setupKoin()

		accountDao = AccountDao(db)
		repo = StoryKudosRepository(StoryKudosDao(db))

		readers.clear()
		authorId = createAccount("author@example.com")
		repeat(4) { i -> readers += createAccount("reader$i@example.com") }
		projectId = createProject(authorId, "Insurgency")
	}

	private fun createAccount(email: String): Long {
		val queries = db.serverDatabase.accountQueries
		queries.createAccount(email, "hash", "secret", false)
		return queries.findAccount(email).executeAsOne().id
	}

	private fun createProject(userId: Long, name: String): Long {
		val queries = db.serverDatabase.projectQueries
		queries.createProject(userId = userId, name = name, uuid = Uuid.random().toString())
		return queries.findProjectByName(userId, name).executeAsOne().id
	}

	private suspend fun pick(readerId: Long, vararg kinds: KudosKind): SetPicksResult =
		repo.setPicks(projectId, authorId, readerId, kinds.toSet())

	@Test
	fun `a new pick set replaces the previous one`() = runTest {
		val reader = readers[0]
		pick(reader, PROSE, PLOT, MOVED_ME)
		pick(reader, PROSE, ENDING)

		assertEquals(setOf(PROSE, ENDING), repo.picksFor(projectId, reader))
	}

	@Test
	fun `craft picks are capped at four`() = runTest {
		val reader = readers[0]

		assertTrue(pick(reader, PROSE, PLOT, ENDING, DIALOGUE) is SetPicksResult.Saved)
		assertEquals(SetPicksResult.OverCap, pick(reader, PROSE, PLOT, ENDING, DIALOGUE, CHARACTERS))
		assertEquals(setOf(PROSE, PLOT, ENDING, DIALOGUE), repo.picksFor(projectId, reader))
	}

	@Test
	fun `picking a second reaction swaps it in for the first`() = runTest {
		val reader = readers[0]
		pick(reader, PROSE, MOVED_ME)

		val result = pick(reader, PROSE, MOVED_ME, MADE_ME_LAUGH)

		assertEquals(SetPicksResult.Saved(setOf(PROSE, MADE_ME_LAUGH)), result)
		assertEquals(setOf(PROSE, MADE_ME_LAUGH), repo.picksFor(projectId, reader))
	}

	@Test
	fun `two brand new reactions at once are rejected`() = runTest {
		assertEquals(SetPicksResult.OverCap, pick(readers[0], MOVED_ME, MADE_ME_LAUGH))
	}

	@Test
	fun `an author cannot give kudos to their own story`() = runTest {
		assertEquals(SetPicksResult.OwnStory, repo.setPicks(projectId, authorId, authorId, setOf(PROSE)))
		assertEquals(0L, repo.tally(projectId).givers)
	}

	@Test
	fun `an opted-out story accepts no kudos until re-enabled`() = runTest {
		repo.setEnabled(projectId, enabled = false)
		assertEquals(SetPicksResult.Disabled, pick(readers[0], PROSE))

		repo.setEnabled(projectId, enabled = true)
		assertTrue(pick(readers[0], PROSE) is SetPicksResult.Saved)
	}

	@Test
	fun `public highlights need three readers and are ranked by count`() = runTest {
		pick(readers[0], PROSE, PLOT, MOVED_ME)
		pick(readers[1], PROSE, PLOT, MOVED_ME)
		pick(readers[2], PROSE, PLOT, MADE_ME_THINK)
		pick(readers[3], PROSE, ENDING)

		val tally = repo.tally(projectId)

		assertEquals(4L, tally.givers)
		assertEquals(listOf(PROSE, PLOT), tally.publicHighlights(KudosGroup.CRAFT))
		assertEquals(emptyList(), tally.publicHighlights(KudosGroup.REACTION))
		assertEquals(
			listOf(MOVED_ME to 2L, MADE_ME_THINK to 1L),
			tally.ranked(KudosGroup.REACTION),
		)
	}

	@Test
	fun `soft-deleted givers drop out of the tally`() = runTest {
		pick(readers[0], PROSE)
		pick(readers[1], PROSE)
		accountDao.markDeleted(readers[1], Clock.System.now())

		val tally = repo.tally(projectId)

		assertEquals(1L, tally.givers)
		assertEquals(mapOf(PROSE to 1L), tally.counts)
	}

	@Test
	fun `stored keys that are no longer chips are ignored`() = runTest {
		db.serverDatabase.storyKudosQueries.insertKudos(projectId, readers[0], "twist")
		pick(readers[1], PROSE)

		val tally = repo.tally(projectId)
		assertEquals(mapOf(PROSE to 1L), tally.counts)
		assertEquals(1L, tally.givers)
		assertEquals(emptySet(), repo.picksFor(projectId, readers[0]))
	}

	@Test
	fun `purging an account removes kudos it gave and kudos on its stories`() = runTest {
		val otherProject = createProject(readers[0], "Reader's Story")
		pick(readers[0], PROSE)
		repo.setPicks(otherProject, readers[0], readers[1], setOf(PLOT))
		repo.setEnabled(otherProject, enabled = false)

		accountDao.markDeleted(readers[0], Clock.System.now())
		UserDataPurgeDao(db).purgeUserData(readers[0])

		val remaining = db.serverDatabase.storyKudosQueries
		assertEquals(emptySet(), remaining.kindsForUser(projectId, readers[0]).executeAsList().toSet())
		assertEquals(emptySet(), remaining.kindsForUser(otherProject, readers[1]).executeAsList().toSet())
		assertEquals(false, remaining.isOptedOut(otherProject).executeAsOne())
	}

	@Test
	fun `every chip has a stable short key and an English label`() {
		val english = Properties().apply {
			File("src/main/resources/i18n/Messages_en.properties").reader(Charsets.UTF_8).use { load(it) }
		}
		val keys = KudosKind.entries.map { it.key }

		assertEquals(keys.size, keys.toSet().size)
		assertTrue(keys.all { it.length in 1..32 })
		assertEquals(emptyList(), KudosKind.entries.filter { english.getProperty(it.messageKey) == null })
	}
}
