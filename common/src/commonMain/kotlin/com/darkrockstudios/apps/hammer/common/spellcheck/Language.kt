package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import com.darkrockstudios.libs.platformspellchecker.SpLocale
import io.fluidsonic.locale.Locale

/**
 * Finds the best matching Language for a given Locale.
 * Tries to match by language tag first, then by language only if no exact match is found.
 *
 * @param locale The locale to find a matching Language for
 * @return The best matching Language enum value
 */
fun PlatformSpellCheckerFactory.findBestMatchingLanguageOrNull(locale: Locale): SpLocale? {
	val available = availableLocales()

	val exactMatch = available.find { it.language == locale.language && it.country == locale.region }
	if (exactMatch != null) {
		return exactMatch
	}

	val languageOnlyMatch = available.find {
		it.language == locale.language
	}
	if (languageOnlyMatch != null) {
		return languageOnlyMatch
	}

	return null
}

fun PlatformSpellCheckerFactory.findBestMatchingLanguage(locale: Locale): SpLocale {
	return findBestMatchingLanguageOrNull(locale) ?: SpLocale.EN_US
}