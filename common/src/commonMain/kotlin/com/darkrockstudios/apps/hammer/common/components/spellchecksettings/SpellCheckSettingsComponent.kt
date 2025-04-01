package com.darkrockstudios.apps.hammer.common.components.spellchecksettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.components.savableState
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.spellcheck.Language
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class SpellCheckSettingsComponent(
	componentContext: ComponentContext,
) : SpellCheckSettings, HammerComponent, SavableComponent<SpellCheckSettings.State>(componentContext) {

	private val mainDispatcher by injectMainDispatcher()
	private val globalSettingsRepository: GlobalSettingsRepository by inject()

	private val _state by savableState {
		SpellCheckSettings.State(
			spellCheckingEnabled = globalSettingsRepository.globalSettings.spellCheckSettings.enabled,
			spellCheckingInFocusEnabled = globalSettingsRepository.globalSettings.spellCheckSettings.enabledInFocusMode,
			spellCheckingLanguage = globalSettingsRepository.globalSettings.spellCheckSettings.language,
		)
	}

	override val state: Value<SpellCheckSettings.State> = _state
	override fun getStateSerializer() = SpellCheckSettings.State.serializer()

	init {
		watchSettingsUpdates()
	}

	private fun watchSettingsUpdates() {
		scope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				withContext(mainDispatcher) {
					_state.getAndUpdate {
						it.copy(
							spellCheckingEnabled = settings.spellCheckSettings.enabled,
							spellCheckingInFocusEnabled = settings.spellCheckSettings.enabledInFocusMode,
							spellCheckingLanguage = settings.spellCheckSettings.language
						)
					}
				}
			}
		}
	}

	override suspend fun setSpellcheckEnable(enable: Boolean) {
		globalSettingsRepository.updateSettings {
			it.copy(
				spellCheckSettings = it.spellCheckSettings.copy(
					enabled = enable
				)
			)
		}
	}

	override suspend fun setSpellCheckingInFocusEnabled(enable: Boolean) {
		globalSettingsRepository.updateSettings {
			it.copy(
				spellCheckSettings = it.spellCheckSettings.copy(
					enabledInFocusMode = enable
				)
			)
		}
	}

	override suspend fun setSpellCheckLanguage(language: Language) {
		globalSettingsRepository.updateSettings {
			it.copy(
				spellCheckSettings = it.spellCheckSettings.copy(
					language = language
				)
			)
		}
	}
}
