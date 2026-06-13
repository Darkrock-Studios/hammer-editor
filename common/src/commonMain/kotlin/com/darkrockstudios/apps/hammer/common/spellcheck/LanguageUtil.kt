package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.Locale

expect class LanguageUtil {
	fun getCurrentLocale(): Locale
}