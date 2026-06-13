package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.Locale

actual fun Locale.displayName(): String {
	val tag = toLanguageTag()
	val javaLocale = java.util.Locale.forLanguageTag(tag)
	val name = javaLocale.getDisplayName(java.util.Locale.getDefault())
	return name.ifBlank { tag }.replaceFirstChar { it.uppercase() }
}
