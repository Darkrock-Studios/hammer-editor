package com.darkrockstudios.apps.hammer.common.util

/**
 * Minimal locale value type: a language plus optional script and region. Replaces
 * io.fluidsonic.locale, which dropped its Kotlin/Native (iOS) targets in 0.14.0.
 * Variant and extension subtags are ignored. The script matters for languages
 * written in more than one (zh-Hans vs zh-Hant, sr-Latn vs sr-Cyrl): dropping it
 * would collapse distinct platform locales into one tag.
 */
class Locale private constructor(
	val language: String?,
	val script: String?,
	val region: String?,
) {
	fun toLanguageTag(): String = listOfNotNull(language, script, region).joinToString("-")

	override fun equals(other: Any?): Boolean =
		other is Locale && other.language == language && other.script == script && other.region == region

	override fun hashCode(): Int =
		31 * (31 * (language?.hashCode() ?: 0) + (script?.hashCode() ?: 0)) + (region?.hashCode() ?: 0)

	override fun toString(): String = toLanguageTag()

	companion object {
		val root = Locale(null, null, null)

		fun forLanguage(language: String?, region: String? = null, script: String? = null): Locale =
			Locale(
				language = language?.lowercase()?.ifBlank { null },
				script = script?.normalizeScript(),
				region = region?.uppercase()?.ifBlank { null },
			)

		// Subset BCP-47: split on '-'/'_', take the language; the first 4-alpha subtag
		// is the script, the first 2-alpha or 3-digit subtag is the region. Variants
		// are ignored. Scanning stops at a single-char subtag — the extension/private-use
		// singleton ('u', 't', 'x') — so e.g. "de-u-co-phonebk" yields no region.
		fun forLanguageTag(tag: String): Locale {
			val parts = tag.split('-', '_').filter { it.isNotBlank() }
			if (parts.isEmpty()) return root
			val subtags = parts.drop(1).takeWhile { it.length > 1 }
			val script = subtags
				.firstOrNull { it.matches(SCRIPT_ALPHA) }
				?.normalizeScript()
			val region = subtags
				.firstOrNull { it.matches(REGION_ALPHA) || it.matches(REGION_NUMERIC) }
				?.uppercase()
			return Locale(parts[0].lowercase(), script, region)
		}

		private fun String.normalizeScript(): String? =
			lowercase().replaceFirstChar { it.uppercase() }.ifBlank { null }

		private val SCRIPT_ALPHA = Regex("[A-Za-z]{4}")
		private val REGION_ALPHA = Regex("[A-Za-z]{2}")
		private val REGION_NUMERIC = Regex("[0-9]{3}")
	}
}
