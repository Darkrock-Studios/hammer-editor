package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.symspellkt.api.SpellChecker
import com.darkrockstudios.symspellkt.common.DictionaryItem
import com.darkrockstudios.symspellkt.common.SpellCheckSettings
import com.darkrockstudios.symspellkt.impl.SymSpell
import io.fluidsonic.locale.Locale
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import kotlin.time.measureTime

class SpellCheckRepository(
	private val dictionaryLoader: SpellCheckDictionaryLoader,
	private val globalSettingsRepository: GlobalSettingsRepository,
) : KoinComponent {

	private val dispatcherDefault by injectDefaultDispatcher()
	private val scope = CoroutineScope(dispatcherDefault)

	private val dictionaries = mapOf(
		Language.English to "en_fdic",
		Language.Spanish to "es_fdic",
		Language.Italian to "it_fdic",
		Language.German to "de_fdic",
		Language.French to "fr_fdic",
	)

	private val _dictionaryFlow = MutableSharedFlow<SpellChecker?>(
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		extraBufferCapacity = 1,
	)
	val dictionaryFlow: SharedFlow<SpellChecker?> = _dictionaryFlow
	private var currentLanguage: Language? = null

	private fun requestSpellChecker(locale: Locale) {
		val language = findBestMatchingLanguage(locale)
		requestSpellChecker(language)
	}

	private fun requestSpellChecker(language: Language) {
		scope.launch {
			val dictionaryName: String? = dictionaries[language]
			if (dictionaryName == null) {
				error("Unsupported Locale type: $language")
			} else {
				currentLanguage = language

				val newSpellChecker = SymSpell(
					spellCheckSettings = SpellCheckSettings(
						topK = 5
					)
				)

				val elapsed = measureTime {
					val freqDic = dictionaryLoader.loadDictionary(dictionaryName)
					freqDic.terms.entries.forEach { (term, frequency) ->
						newSpellChecker.dictionary.addItem(
							DictionaryItem(
								term,
								frequency.toDouble(),
								-1.0
							)
						)
					}

					_dictionaryFlow.tryEmit(newSpellChecker)
				}

				Napier.i("Dictionary loaded for: ${language.locale.toLanguageTag()} (took: $elapsed)")
			}
		}
	}

	init {
		scope.launch {
			val initialLanguage =
				globalSettingsRepository.globalSettings.spellCheckSettings.language
			requestSpellChecker(initialLanguage)
			Napier.i("Spell Check: Initial language set: $initialLanguage")

			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				val newLanguage = settings.spellCheckSettings.language
				if (currentLanguage != settings.spellCheckSettings.language) {
					Napier.i("Updating Spell Check Language: $newLanguage")
					requestSpellChecker(newLanguage)
				}
			}
		}
	}
}