package com.darkrockstudios.apps.hammer.common.spellcheck

import io.fluidsonic.locale.Locale
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

actual fun Locale.displayName(): String {
	val tag = toLanguageTag().toString()
	val identifier = tag.replace('-', '_')
	return NSLocale.currentLocale.localizedStringForLocaleIdentifier(identifier)
		?.replaceFirstChar { it.uppercase() }
		?: tag
}
