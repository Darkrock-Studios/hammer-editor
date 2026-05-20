package com.darkrockstudios.apps.hammer.common.spellcheck

import io.fluidsonic.locale.Locale
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual class LanguageUtil {
	actual fun getCurrentLocale(): Locale {
		val tag = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
		return Locale.forLanguageTag(tag)
	}
}
