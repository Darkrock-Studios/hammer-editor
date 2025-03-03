package com.darkrockstudios.apps.hammer.common.spellcheck

import io.fluidsonic.locale.Locale

actual class LanguageUtil {
	actual fun getCurrentLocale(): Locale {
		val tag = java.util.Locale.getDefault().toLanguageTag()
		return Locale.forLanguageTag(tag)
	}
}