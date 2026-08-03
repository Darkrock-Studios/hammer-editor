package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.Locale

/**
 * Lenient locale match: languages must be equal; regions are compared only when
 * both sides have one. So "en" matches "en-US", but "en-US" does not match "en-GB".
 */
fun localesMatchLeniently(a: Locale, b: Locale): Boolean {
	if (a.language == null || b.language == null) return false
	if (a.language != b.language) return false
	val aRegion = a.region
	val bRegion = b.region
	return aRegion == null || bRegion == null || aRegion == bRegion
}

/**
 * Whether spell checking may run for a project written in [projectLanguageTag]
 * (BCP-47, null/blank = unset) given the user's [dictionary] locale. An unset
 * project language never gates spell check.
 */
fun isSpellCheckAllowedForProject(projectLanguageTag: String?, dictionary: Locale): Boolean {
	if (projectLanguageTag.isNullOrBlank()) return true
	return localesMatchLeniently(Locale.forLanguageTag(projectLanguageTag), dictionary)
}
