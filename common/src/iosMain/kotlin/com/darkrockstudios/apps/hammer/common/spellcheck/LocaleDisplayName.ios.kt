package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.Locale
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.currentLocale

actual fun Locale.displayName(): String {
	val tag = toLanguageTag()
	val identifier = tag.replace('-', '_')
	val localized = NSLocale.currentLocale.displayNameForKey(NSLocaleIdentifier, value = identifier)
	return localized?.replaceFirstChar { it.uppercase() } ?: tag
}
