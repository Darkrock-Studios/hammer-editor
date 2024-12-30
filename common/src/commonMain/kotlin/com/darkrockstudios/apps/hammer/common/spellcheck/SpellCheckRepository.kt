package com.darkrockstudios.apps.hammer.common.spellcheck

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
) : KoinComponent {

	private val dispatcherDefault by injectDefaultDispatcher()
	private val scope = CoroutineScope(dispatcherDefault)

	private val dictionaries = mapOf(
		Languages.EN to "en.fdic",
		Languages.ES to "es.fdic",
	)

	private val _dictionaryFlow = MutableSharedFlow<SpellChecker>(
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		extraBufferCapacity = 1,
	)
	val dictionaryFlow: SharedFlow<SpellChecker> = _dictionaryFlow

	fun requestSpellChecker(locale: Locale) {
		scope.launch {
			val dictionaryName = dictionaries[locale]
			if (dictionaryName == null) {
				error("Unsupported Locale type: $locale")
			} else {
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

				Napier.i("Dictionary loaded for: ${locale.toLanguageTag()} (took: $elapsed)")
			}
		}
	}

	companion object {
		object Languages {
			val EN = Locale.forLanguageTag("en")
			val ES = Locale.forLanguageTag("es")
		}

		val avalibleLanguages = listOf(
			Languages.EN,
			Languages.ES,
		)
	}
}