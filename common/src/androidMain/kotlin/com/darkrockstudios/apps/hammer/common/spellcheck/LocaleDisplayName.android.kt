package com.darkrockstudios.apps.hammer.common.spellcheck

import io.fluidsonic.locale.Locale

actual fun Locale.displayName(): String {
	val tag = toLanguageTag().toString()
	val javaLocale = java.util.Locale.forLanguageTag(tag)
	val name = javaLocale.getDisplayName(java.util.Locale.getDefault())
	return name.ifBlank { tag }.replaceFirstChar { it.uppercase() }
}
