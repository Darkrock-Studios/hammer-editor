package com.darkrockstudios.apps.hammer.common.util

/**
 * Minimal locale value type: a language plus an optional region. Replaces
 * io.fluidsonic.locale, which dropped its Kotlin/Native (iOS) targets in 0.14.0.
 * We only ever model language + region, so script/variant subtags are ignored.
 */
class Locale private constructor(
	val language: String?,
	val region: String?,
) {
	fun toLanguageTag(): String = listOfNotNull(language, region).joinToString("-")

	override fun equals(other: Any?): Boolean =
		other is Locale && other.language == language && other.region == region

	override fun hashCode(): Int = 31 * (language?.hashCode() ?: 0) + (region?.hashCode() ?: 0)

	override fun toString(): String = toLanguageTag()

	companion object {
		val root = Locale(null, null)

		fun forLanguage(language: String?, region: String? = null): Locale =
			Locale(
				language = language?.lowercase()?.ifBlank { null },
				region = region?.uppercase()?.ifBlank { null },
			)

		// Subset BCP-47: split on '-'/'_', take the language; the first 2-alpha or
		// 3-digit subtag is the region. Script (4-alpha) and variants are ignored.
		// Scanning stops at a single-char subtag — the extension/private-use
		// singleton ('u', 't', 'x') — so e.g. "de-u-co-phonebk" yields no region.
		fun forLanguageTag(tag: String): Locale {
			val parts = tag.split('-', '_').filter { it.isNotBlank() }
			if (parts.isEmpty()) return root
			val region = parts.drop(1)
				.takeWhile { it.length > 1 }
				.firstOrNull { it.matches(REGION_ALPHA) || it.matches(REGION_NUMERIC) }
				?.uppercase()
			return Locale(parts[0].lowercase(), region)
		}

		private val REGION_ALPHA = Regex("[A-Za-z]{2}")
		private val REGION_NUMERIC = Regex("[0-9]{3}")
	}
}
