package com.darkrockstudios.apps.hammer.common.components.spellchecksettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.components.savableState
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.spellcheck.toLocale
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class SpellCheckSettingsComponent(
	componentContext: ComponentContext,
) : SpellCheckSettings, HammerComponent, SavableComponent<SpellCheckSettings.State>(componentContext) {

	private val mainDispatcher by injectMainDispatcher()
	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val platformSpellCheckerFactory: PlatformSpellCheckerFactory by inject()

	private val _state by savableState {
		SpellCheckSettings.State(
			spellCheckingEnabled = globalSettingsStore.globalSettings.spellCheckSettings.enabled,
			spellCheckingInFocusEnabled = globalSettingsStore.globalSettings.spellCheckSettings.enabledInFocusMode,
			spellCheckingEncyclopediaEnabled = globalSettingsStore.globalSettings.spellCheckSettings.includeEncyclopediaNames,
			spellCheckingLanguage = globalSettingsStore.globalSettings.spellCheckSettings.locale,
			spellCheckLanguages = platformSpellCheckerFactory.availableLocales().map { it.toLocale() },
		)
	}

	override val state: Value<SpellCheckSettings.State> = _state
	override fun getStateSerializer() = SpellCheckSettings.State.serializer()

	init {
		watchSettingsUpdates()
	}

	private fun watchSettingsUpdates() {
		scope.launch {
			globalSettingsStore.globalSettingsUpdates.collect { settings ->
				withContext(mainDispatcher) {
					_state.getAndUpdate {
						it.copy(
							spellCheckingEnabled = settings.spellCheckSettings.enabled,
							spellCheckingInFocusEnabled = settings.spellCheckSettings.enabledInFocusMode,
							spellCheckingEncyclopediaEnabled = settings.spellCheckSettings.includeEncyclopediaNames,
							spellCheckingLanguage = settings.spellCheckSettings.locale
						)
					}
				}
			}
		}
	}

	override suspend fun setSpellcheckEnable(enable: Boolean) {
		globalSettingsStore.updateSettings {
			it.copy(
				spellCheckSettings = it.spellCheckSettings.copy(
					enabled = enable
				)
			)
		}
	}

	override suspend fun setSpellCheckingInFocusEnabled(enable: Boolean) {
		globalSettingsStore.updateSettings {
			it.copy(
				spellCheckSettings = it.spellCheckSettings.copy(
					enabledInFocusMode = enable
				)
			)
		}
	}

	override suspend fun setSpellCheckEncyclopediaEnabled(enable: Boolean) {
		globalSettingsStore.updateSettings {
			it.copy(
				spellCheckSettings = it.spellCheckSettings.copy(
					includeEncyclopediaNames = enable
				)
			)
		}
	}

	override suspend fun setSpellCheckLanguage(locale: Locale) {
		globalSettingsStore.updateSettings {
			it.copy(
				spellCheckSettings = it.spellCheckSettings.copy(
					locale = locale
				)
			)
		}
	}
}
