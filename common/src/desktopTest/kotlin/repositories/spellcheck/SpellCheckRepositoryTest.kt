package repositories.spellcheck

import app.cash.turbine.test
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.apps.hammer.common.spellcheck.toSpLocale
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import com.darkrockstudios.libs.platformspellchecker.SpLocale
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpellCheckRepositoryTest : BaseTest() {

	private lateinit var globalSettingsDatasource: GlobalSettingsDatasource
	private lateinit var serverSettingsDatasource: ServerSettingsDatasource
	private lateinit var factory: PlatformSpellCheckerFactory

	@BeforeEach
	override fun setup() {
		super.setup()
		globalSettingsDatasource = mockk()
		serverSettingsDatasource = mockk()
		factory = mockk()
		setupKoin()
	}

	private fun settingsStore(locale: Locale, enabled: Boolean = true): GlobalSettingsStore {
		coEvery { globalSettingsDatasource.loadSettings() } returns GlobalSettings(
			projectsDirectory = "/projects",
			spellCheckSettings = SpellCheckerSettings(enabled = enabled, locale = locale),
		)
		coEvery { serverSettingsDatasource.loadServerSettings(any()) } returns null
		return GlobalSettingsStore(globalSettingsDatasource, serverSettingsDatasource)
	}

	@Test
	fun `loads a spell checker for the configured locale on init`() = scope.runTest {
		val checker = mockk<PlatformSpellChecker>()
		every { factory.hasLanguage(any()) } returns true
		// Locale-exact stub: loading any dictionary other than the configured "en" fails the test.
		coEvery { factory.createSpellChecker(SpLocale("en")) } returns checker

		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en")), factory)

		repo.dictionaryFlow.test {
			assertSame(checker, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `warns and emits nothing for an unsupported locale`() = scope.runTest {
		every { factory.hasLanguage(any()) } returns false

		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en")), factory)
		advanceUntilIdle()

		assertTrue(repo.dictionaryFlow.replayCache.isEmpty())
		coVerify(exactly = 0) { factory.createSpellChecker(any()) }
	}

	@Test
	fun `reloads the checker when the settings locale changes`() = scope.runTest {
		val first = mockk<PlatformSpellChecker>()
		val second = mockk<PlatformSpellChecker>()
		every { factory.hasLanguage(any()) } returns true
		// Locale-exact stubs: the reload must request the NEW locale, not re-load the old one.
		coEvery { factory.createSpellChecker(SpLocale("en")) } returns first
		coEvery { factory.createSpellChecker(SpLocale("fr")) } returns second
		coEvery { globalSettingsDatasource.storeSettings(any()) } just Runs

		val store = settingsStore(Locale.forLanguageTag("en"))
		val repo = SpellCheckRepository(store, factory)

		repo.dictionaryFlow.test {
			assertSame(first, awaitItem())

			store.updateSettings { settings ->
				settings.copy(
					spellCheckSettings = settings.spellCheckSettings.copy(
						locale = Locale.forLanguageTag("fr")
					)
				)
			}

			assertSame(second, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `does not reload when an unrelated setting changes`() = scope.runTest {
		val checker = mockk<PlatformSpellChecker>()
		var loadCount = 0
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } answers { loadCount++; checker }
		coEvery { globalSettingsDatasource.storeSettings(any()) } just Runs

		val store = settingsStore(Locale.forLanguageTag("en"))
		SpellCheckRepository(store, factory)
		advanceUntilIdle()
		val loadsAfterInit = loadCount

		store.updateSettings { it.copy(automaticBackups = false) }
		advanceUntilIdle()

		assertEquals(loadsAfterInit, loadCount)
	}

	@Test
	fun `does not load or emit a checker when spell check is globally disabled`() = scope.runTest {
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } returns mockk()

		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en"), enabled = false), factory)
		advanceUntilIdle()

		assertTrue(repo.dictionaryFlow.replayCache.all { it == null })
		coVerify(exactly = 0) { factory.createSpellChecker(any()) }
	}

	@Test
	fun `emits null when spell check is disabled after being enabled`() = scope.runTest {
		val checker = mockk<PlatformSpellChecker>()
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } returns checker
		coEvery { globalSettingsDatasource.storeSettings(any()) } just Runs

		val store = settingsStore(Locale.forLanguageTag("en"), enabled = true)
		val repo = SpellCheckRepository(store, factory)

		repo.dictionaryFlow.test {
			assertSame(checker, awaitItem())

			store.updateSettings { settings ->
				settings.copy(
					spellCheckSettings = settings.spellCheckSettings.copy(enabled = false)
				)
			}

			assertEquals(null, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `reloads the checker when spell check is re-enabled`() = scope.runTest {
		val checker = mockk<PlatformSpellChecker>()
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(SpLocale("en")) } returns checker
		coEvery { globalSettingsDatasource.storeSettings(any()) } just Runs

		val store = settingsStore(Locale.forLanguageTag("en"), enabled = false)
		val repo = SpellCheckRepository(store, factory)
		advanceUntilIdle()

		repo.dictionaryFlow.test {
			// Disabled at init: no checker handed out.
			assertEquals(null, awaitItem())

			store.updateSettings { settings ->
				settings.copy(
					spellCheckSettings = settings.spellCheckSettings.copy(enabled = true)
				)
			}

			assertSame(checker, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `toSpLocale maps language and region`() {
		val spLocale = Locale.forLanguage("en", "US").toSpLocale()

		assertEquals("en", spLocale.language)
		assertEquals("US", spLocale.country)
	}

	@Test
	fun `toSpLocale falls back to EN_US for a language-less locale`() {
		assertEquals(SpLocale.EN_US, Locale.root.toSpLocale())
	}
}
