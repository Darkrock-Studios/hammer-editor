package data.changelog

import com.darkrockstudios.apps.hammer.common.data.changelog.Changelog
import com.darkrockstudios.apps.hammer.common.data.changelog.ChangelogDatasource
import com.darkrockstudios.apps.hammer.common.data.changelog.ChangelogRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangelogRepositoryTest : BaseTest() {

	private lateinit var datasource: ChangelogDatasource
	private lateinit var settingsStore: GlobalSettingsStore

	private var settings = GlobalSettings(
		projectsDirectory = "",
		spellCheckSettings = SpellCheckerSettings(locale = mockk()),
	)

	private val changelog = Changelog(version = "3.7.2", date = "2026-7-27", notes = "- A thing")

	@BeforeEach
	override fun setup() {
		super.setup()

		datasource = mockk()
		settingsStore = mockk(relaxed = true)
		every { settingsStore.globalSettings } answers { settings }
	}

	private fun repository() = ChangelogRepository(datasource, settingsStore)

	@Test
	fun `Changelog is unseen when the stored version differs`() = runTest {
		settings = settings.copy(lastSeenChangelogVersion = "3.6.0")
		coEvery { datasource.loadChangelog() } returns changelog

		assertTrue(repository().hasUnseenChangelog())
	}

	@Test
	fun `Changelog is unseen when nothing has been stored`() = runTest {
		coEvery { datasource.loadChangelog() } returns changelog

		assertNull(settings.lastSeenChangelogVersion)
		assertTrue(repository().hasUnseenChangelog())
	}

	@Test
	fun `Changelog is seen when the stored version matches`() = runTest {
		settings = settings.copy(lastSeenChangelogVersion = "3.7.2")
		coEvery { datasource.loadChangelog() } returns changelog

		assertFalse(repository().hasUnseenChangelog())
	}

	@Test
	fun `Missing resource never shows the dialog`() = runTest {
		coEvery { datasource.loadChangelog() } returns null

		val repository = repository()
		assertFalse(repository.hasUnseenChangelog())
		assertNull(repository.getChangelog())
	}

	@Test
	fun `markSeen stores the changelog version`() = runTest {
		coEvery { datasource.loadChangelog() } returns changelog
		val captured = slot<(GlobalSettings) -> GlobalSettings>()
		coEvery { settingsStore.updateSettings(capture(captured)) } just Runs

		repository().markSeen()

		assertEquals("3.7.2", captured.captured(settings).lastSeenChangelogVersion)
	}

	@Test
	fun `markSeen with no changelog writes nothing`() = runTest {
		coEvery { datasource.loadChangelog() } returns null

		repository().markSeen()

		coVerify(exactly = 0) { settingsStore.updateSettings(any()) }
	}

	@Test
	fun `Resource is read once per session`() = runTest {
		coEvery { datasource.loadChangelog() } returns changelog

		val repository = repository()
		repository.getChangelog()
		repository.getChangelog()
		repository.hasUnseenChangelog()

		coVerify(exactly = 1) { datasource.loadChangelog() }
	}
}
