package com.darkrockstudios.apps.hammer.common.util

actual class AvailableLocalesProvider {
	actual fun allLocales(): List<Locale> =
		java.util.Locale.getAvailableLocales()
			// Parse the full tag rather than language+country so script-distinct
			// locales (zh-Hans vs zh-Hant) stay separate entries.
			.map { Locale.forLanguageTag(it.toLanguageTag()) }
			.filter { it.language != null }
			.distinctBy { it.toLanguageTag() }
}
