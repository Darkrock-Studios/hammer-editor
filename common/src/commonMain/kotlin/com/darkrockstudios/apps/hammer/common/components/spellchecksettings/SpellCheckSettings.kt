package com.darkrockstudios.apps.hammer.common.components.spellchecksettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.spellcheck.Language
import kotlinx.serialization.Serializable

interface SpellCheckSettings {
	val state: Value<State>

	suspend fun setSpellcheckEnable(enable: Boolean)
	suspend fun setSpellCheckingInFocusEnabled(enable: Boolean)
	suspend fun setSpellCheckLanguage(language: Language)

	@Serializable
	data class State(
		val spellCheckingEnabled: Boolean,
		val spellCheckingInFocusEnabled: Boolean,
		val spellCheckingLanguage: Language,
	)
}
