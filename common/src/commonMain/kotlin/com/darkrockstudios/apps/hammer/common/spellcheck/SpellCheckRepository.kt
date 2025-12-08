package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import com.darkrockstudios.libs.platformspellchecker.SpLocale
import io.fluidsonic.locale.Locale
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class SpellCheckRepository(
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val spellCheckFactory: PlatformSpellCheckerFactory,
) : KoinComponent {

	private val dispatcherDefault by injectDefaultDispatcher()
	private val scope = CoroutineScope(dispatcherDefault)

	private val _dictionaryFlow = MutableSharedFlow<PlatformSpellChecker?>(
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		extraBufferCapacity = 1,
	)
	val dictionaryFlow: SharedFlow<PlatformSpellChecker?> = _dictionaryFlow
	private var currentLanguage: Locale? = null

	private fun requestSpellChecker(language: Locale) {
		scope.launch {
			val spLocale = language.toSpLocale()
			if (spellCheckFactory.hasLanguage(spLocale).not()) {
				Napier.w("Unsupported Locale type: $language")
			} else {
				val checker = spellCheckFactory.createSpellChecker(spLocale)
				currentLanguage = language
				_dictionaryFlow.tryEmit(checker)

				Napier.i("Spell Checker loaded for: ${language.toLanguageTag()}")
			}
		}
	}

	init {
		scope.launch {
			val initialLocale =
				globalSettingsRepository.globalSettings.spellCheckSettings.locale
			requestSpellChecker(initialLocale)
			Napier.i("Spell Check: Initial language set: $initialLocale")

			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				val newLanguage = settings.spellCheckSettings.locale
				if (currentLanguage != settings.spellCheckSettings.locale) {
					Napier.i("Updating Spell Check Language: $newLanguage")
					requestSpellChecker(newLanguage)
				}
			}
		}
	}
}

fun Locale.toSpLocale() = SpLocale(language = language!!, country = region)
fun SpLocale.toLocale() = Locale.forLanguage(language, region = country)