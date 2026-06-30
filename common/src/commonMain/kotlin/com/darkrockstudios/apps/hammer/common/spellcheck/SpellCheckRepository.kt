package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import com.darkrockstudios.libs.platformspellchecker.SpLocale
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class SpellCheckRepository(
	private val globalSettingsStore: GlobalSettingsStore,
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
	private var currentEnabled: Boolean? = null

	private suspend fun applySpellCheckSettings(enabled: Boolean, language: Locale) {
		if (!enabled) {
			if (currentEnabled != false) {
				currentEnabled = false
				currentLanguage = null
				_dictionaryFlow.tryEmit(null)
				Napier.i("Spell Check disabled: dictionary cleared")
			}
			return
		}

		if (currentEnabled == true && currentLanguage == language) return

		val spLocale = language.toSpLocale()
		if (spellCheckFactory.hasLanguage(spLocale).not()) {
			Napier.w("Unsupported Locale type: $language")
		} else {
			val checker = spellCheckFactory.createSpellChecker(spLocale)
			currentLanguage = language
			currentEnabled = true
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