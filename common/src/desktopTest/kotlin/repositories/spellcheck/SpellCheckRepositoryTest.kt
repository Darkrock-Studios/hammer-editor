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
import getProject1Def
import getProjectDef
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

	private fun sessionCapableChecker(correctWords: Set<String> = emptySet()): Pair<PlatformSpellChecker, MutableList<Collection<String>>> {
		val applied = mutableListOf<Collection<String>>()
		val checker = mockk<PlatformSpellChecker>()
		coEvery { checker.isWordCorrect(any()) } answers { firstArg<String>() in correctWords }
		coEvery { checker.setUserDictionary(any()) } answers { applied.add(firstArg()); Unit }
		return checker to applied
	}

	@Test
	fun `setSessionWords emits a new checker carrying the words`() = scope.runTest {
		val (first, _) = sessionCapableChecker()
		val (second, secondApplied) = sessionCapableChecker()
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } returnsMany listOf(first, second)

		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en")), factory)

		repo.dictionaryFlow.test {
			assertSame(first, awaitItem())

			repo.setSessionWords(getProject1Def(), setOf("zaltharion", "kastle"))

			assertSame(second, awaitItem())
			assertEquals(setOf("zaltharion", "kastle"), secondApplied.single().toSet())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `words the base dictionary already accepts are not added`() = scope.runTest {
		val (first, _) = sessionCapableChecker()
		val (second, secondApplied) = sessionCapableChecker(correctWords = setOf("paris"))
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } returnsMany listOf(first, second)

		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en")), factory)

		repo.dictionaryFlow.test {
			assertSame(first, awaitItem())

			repo.setSessionWords(getProject1Def(), setOf("paris", "zaltharion"))

			assertSame(second, awaitItem())
			assertEquals(setOf("zaltharion"), secondApplied.single().toSet())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `locale change re-applies session words to the new checker`() = scope.runTest {
		val (en, _) = sessionCapableChecker()
		val (enWithWords, _) = sessionCapableChecker()
		val (fr, frApplied) = sessionCapableChecker()
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(SpLocale("en")) } returnsMany listOf(en, enWithWords)
		coEvery { factory.createSpellChecker(SpLocale("fr")) } returns fr
		coEvery { globalSettingsDatasource.storeSettings(any()) } just Runs

		val store = settingsStore(Locale.forLanguageTag("en"))
		val repo = SpellCheckRepository(store, factory)

		repo.dictionaryFlow.test {
			assertSame(en, awaitItem())

			repo.setSessionWords(getProject1Def(), setOf("zaltharion"))
			assertSame(enWithWords, awaitItem())

			store.updateSettings { settings ->
				settings.copy(
					spellCheckSettings = settings.spellCheckSettings.copy(
						locale = Locale.forLanguageTag("fr")
					)
				)
			}

			assertSame(fr, awaitItem())
			assertEquals(setOf("zaltharion"), frApplied.single().toSet())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `session words set while disabled are applied on re-enable`() = scope.runTest {
		val (checker, applied) = sessionCapableChecker()
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(SpLocale("en")) } returns checker
		coEvery { globalSettingsDatasource.storeSettings(any()) } just Runs

		val store = settingsStore(Locale.forLanguageTag("en"), enabled = false)
		val repo = SpellCheckRepository(store, factory)
		advanceUntilIdle()

		repo.setSessionWords(getProject1Def(), setOf("zaltharion"))
		advanceUntilIdle()
		coVerify(exactly = 0) { factory.createSpellChecker(any()) }

		repo.dictionaryFlow.test {
			assertEquals(null, awaitItem())

			store.updateSettings { settings ->
				settings.copy(
					spellCheckSettings = settings.spellCheckSettings.copy(enabled = true)
				)
			}

			assertSame(checker, awaitItem())
			assertEquals(setOf("zaltharion"), applied.single().toSet())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `clearSessionWords emits a fresh checker without the words`() = scope.runTest {
		val (first, _) = sessionCapableChecker()
		val (second, secondApplied) = sessionCapableChecker()
		val (third, thirdApplied) = sessionCapableChecker()
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } returnsMany listOf(first, second, third)

		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en")), factory)

		repo.dictionaryFlow.test {
			assertSame(first, awaitItem())

			repo.setSessionWords(getProject1Def(), setOf("zaltharion"))
			assertSame(second, awaitItem())
			assertEquals(setOf("zaltharion"), secondApplied.single().toSet())

			repo.clearSessionWords(getProject1Def())
			assertSame(third, awaitItem())
			assertTrue(thirdApplied.isEmpty())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `clearing an owner that contributed nothing does not recreate the checker`() = scope.runTest {
		val (checker, _) = sessionCapableChecker()
		var loadCount = 0
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } answers { loadCount++; checker }

		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en")), factory)
		advanceUntilIdle()
		val loadsAfterInit = loadCount

		repo.clearSessionWords(getProject1Def())
		advanceUntilIdle()

		assertEquals(loadsAfterInit, loadCount)
	}

	@Test
	fun `session words are unioned across owners and cleared per-owner`() = scope.runTest {
		val (first, _) = sessionCapableChecker()
		val (second, secondApplied) = sessionCapableChecker()
		val (third, thirdApplied) = sessionCapableChecker()
		val (fourth, fourthApplied) = sessionCapableChecker()
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } returnsMany listOf(first, second, third, fourth)

		val projectA = getProject1Def()
		val projectB = getProjectDef("Project B")
		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en")), factory)

		repo.dictionaryFlow.test {
			assertSame(first, awaitItem())

			repo.setSessionWords(projectA, setOf("zaltharion"))
			assertSame(second, awaitItem())
			assertEquals(setOf("zaltharion"), secondApplied.single().toSet())

			repo.setSessionWords(projectB, setOf("kastle"))
			assertSame(third, awaitItem())
			assertEquals(setOf("zaltharion", "kastle"), thirdApplied.single().toSet())

			repo.clearSessionWords(projectA)
			assertSame(fourth, awaitItem())
			assertEquals(setOf("kastle"), fourthApplied.single().toSet())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `pushing an identical word set does not recreate the checker`() = scope.runTest {
		val (checker, _) = sessionCapableChecker()
		var loadCount = 0
		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } answers { loadCount++; checker }

		val repo = SpellCheckRepository(settingsStore(Locale.forLanguageTag("en")), factory)
		advanceUntilIdle()

		repo.setSessionWords(getProject1Def(), setOf("zaltharion"))
		advanceUntilIdle()
		val loadsAfterFirstPush = loadCount

		repo.setSessionWords(getProject1Def(), setOf("zaltharion"))
		advanceUntilIdle()

		assertEquals(loadsAfterFirstPush, loadCount)
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
