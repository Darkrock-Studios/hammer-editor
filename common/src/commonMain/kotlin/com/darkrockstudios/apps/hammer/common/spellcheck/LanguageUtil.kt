package com.darkrockstudios.apps.hammer.common.spellcheck

import io.fluidsonic.locale.Locale

expect class LanguageUtil {
	fun getCurrentLocale(): Locale
}