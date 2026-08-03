package com.darkrockstudios.apps.hammer.common.util

actual class AvailableLocalesProvider {
	actual fun allLocales(): List<Locale> =
		java.util.Locale.getAvailableLocales()
			.map { Locale.forLanguage(it.language, it.country) }
			.filter { it.language != null }
			.distinctBy { it.toLanguageTag() }
}
