package com.darkrockstudios.apps.hammer.common.components.spellchecksettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.globalsettings.LocaleListSerializer
import com.darkrockstudios.apps.hammer.common.data.globalsettings.LocaleSerializer
import io.fluidsonic.locale.Locale
import kotlinx.serialization.Serializable

interface SpellCheckSettings {
	val state: Value<State>

	suspend fun setSpellcheckEnable(enable: Boolean)
	suspend fun setSpellCheckingInFocusEnabled(enable: Boolean)
	suspend fun setSpellCheckLanguage(language: Locale)

	@Serializable
	data class State(
		val spellCheckingEnabled: Boolean,
		val spellCheckingInFocusEnabled: Boolean,
		@Serializable(with = LocaleSerializer::class)
		val spellCheckingLanguage: Locale,
		@Serializable(with = LocaleListSerializer::class)
		val spellCheckLanguages: List<Locale>,
	)
}
