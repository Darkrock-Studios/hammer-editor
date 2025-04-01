package com.darkrockstudios.apps.hammer.common.spellcheck

import io.fluidsonic.locale.Locale

enum class Language(val locale: Locale) {
	English(Locale.forLanguageTag("en")),
	Spanish(Locale.forLanguageTag("es")),
	German(Locale.forLanguageTag("de")),
	French(Locale.forLanguageTag("fr")),
	Italian(Locale.forLanguageTag("it")),
}

/**
 * Finds the best matching Language for a given Locale.
 * Tries to match by language tag first, then by language only if no exact match is found.
 *
 * @param locale The locale to find a matching Language for
 * @return The best matching Language enum value
 */
fun findBestMatchingLanguageOrNull(locale: Locale): Language? {
	val exactMatch = Language.entries.find { it.locale == locale }
	if (exactMatch != null) {
		return exactMatch
	}

	val languageOnlyMatch = Language.entries.find {
		it.locale.language == locale.language
	}
	if (languageOnlyMatch != null) {
		return languageOnlyMatch
	}

	return null
}

fun findBestMatchingLanguage(locale: Locale): Language {
	return findBestMatchingLanguageOrNull(locale) ?: Language.English
}