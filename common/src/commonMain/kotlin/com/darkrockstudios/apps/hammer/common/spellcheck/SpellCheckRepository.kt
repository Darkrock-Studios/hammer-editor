package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import com.darkrockstudios.libs.platformspellchecker.SpLocale
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent

class SpellCheckRepository(
	private val globalSettingsStore: GlobalSettingsStore,
	private val spellCheckFactory: PlatformSpellCheckerFactory,
) : KoinComponent {

	private val dispatcherDefault by injectDefaultDispatcher()
	// Supervisor so a failing platform-checker call can't kill the settings collector.
	private val scope = CoroutineScope(dispatcherDefault + SupervisorJob())

	private val _dictionaryFlow = MutableSharedFlow<PlatformSpellChecker?>(
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		extraBufferCapacity = 1,
	)
	val dictionaryFlow: SharedFlow<PlatformSpellChecker?> = _dictionaryFlow
	private var currentLanguage: Locale? = null
	private var currentEnabled: Boolean? = null

	private val mutex = Mutex()
	private var sessionWords: Map<ProjectDef, Set<String>> = emptyMap()
	private var appliedCandidates: Set<String> = emptySet()

	/**
	 * Replaces [owner]'s session words. Held in-memory only (DictionaryScope.AppLocal),
	 * unioned across owners, and re-applied whenever a new checker is created.
	 * Non-suspend so it is callable from ScopeCallback.onScopeClose.
	 */
	fun setSessionWords(owner: ProjectDef, words: Set<String>) {
		scope.launch {
			mutex.withLock {
				if (sessionWords[owner] == words) return@withLock
				sessionWords = sessionWords + (owner to words)
				recreateCheckerWithSessionWords()
			}
		}
	}

	fun clearSessionWords(owner: ProjectDef) {
		scope.launch {
			mutex.withLock {
				if (owner !in sessionWords) return@withLock
				sessionWords = sessionWords - owner
				recreateCheckerWithSessionWords()
			}
		}
	}

	/**
	 * Downstream editors only re-run a full scan when the checker identity changes,
	 * so word-set changes emit a fresh instance rather than mutating the current one.
	 */
	private suspend fun recreateCheckerWithSessionWords() {
		val language = currentLanguage ?: return
		if (sessionWordUnion() == appliedCandidates) return

		try {
			val checker = spellCheckFactory.createSpellChecker(language.toSpLocale())
			applySessionWords(checker)
			_dictionaryFlow.tryEmit(checker)
		} catch (e: Exception) {
			Napier.e("Spell Check: failed to apply session words", e)
		}
	}

	private fun sessionWordUnion(): Set<String> =
		sessionWords.values.flatMapTo(mutableSetOf()) { it }

	// Words the base dictionary already accepts are filtered, so only unknown spellings are added.
	// appliedCandidates is only recorded on success, so a failed apply retries on the next push.
	private suspend fun applySessionWords(checker: PlatformSpellChecker) {
		val union = sessionWordUnion()
		val toAdd = union.filterNot { checker.isWordCorrect(it) }
		if (toAdd.isNotEmpty()) {
			checker.setUserDictionary(toAdd)
			Napier.i("Spell Check: applied ${toAdd.size} session words")
		}
		appliedCandidates = union
	}

	private suspend fun applySpellCheckSettings(enabled: Boolean, language: Locale) = mutex.withLock {
		if (!enabled) {
			if (currentEnabled != false) {
				currentEnabled = false
				currentLanguage = null
				appliedCandidates = emptySet()
				_dictionaryFlow.tryEmit(null)
				Napier.i("Spell Check disabled: dictionary cleared")
			}
			return@withLock
		}

		if (currentEnabled == true && currentLanguage == language) return@withLock

		val spLocale = language.toSpLocale()
		if (spellCheckFactory.hasLanguage(spLocale).not()) {
			Napier.w("Unsupported Locale type: $language")
		} else {
			val checker = spellCheckFactory.createSpellChecker(spLocale)
			currentLanguage = language
			currentEnabled = true
			try {
				applySessionWords(checker)
			} catch (e: Exception) {
				// The checker still ships without session words; the stale
				// appliedCandidates means the next push retries the apply.
				Napier.e("Spell Check: failed to apply session words", e)
			}
			_dictionaryFlow.tryEmit(checker)

			Napier.i("Spell Checker loaded for: ${language.toLanguageTag()}")
		}
	}

	init {
		scope.launch {
			val initial = globalSettingsStore.globalSettings.spellCheckSettings
			applySpellCheckSettings(initial.enabled, initial.locale)
			Napier.i("Spell Check: Initial settings applied: enabled=${initial.enabled}, locale=${initial.locale}")

			globalSettingsStore.globalSettingsUpdates.collect { settings ->
				applySpellCheckSettings(
					settings.spellCheckSettings.enabled,
					settings.spellCheckSettings.locale,
				)
			}
		}
	}
}

// A null language (root/garbage tag) falls back to the default dictionary rather than crashing.
fun Locale.toSpLocale() =
	language?.let { SpLocale(language = it, country = region) } ?: SpLocale.EN_US
fun SpLocale.toLocale() = Locale.forLanguage(language, region = country)