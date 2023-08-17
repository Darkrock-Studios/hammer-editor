package com.darkrockstudios.apps.hammer.plugins.kweb

import com.github.aymanizz.ktori18n.KeyGenerator
import com.github.aymanizz.ktori18n.i18n
import io.ktor.server.application.*
import java.util.*

class KwebStringTranslator(
	private val application: Application,
	defaultLocale: Locale
) {
	var locale: Locale = defaultLocale

	fun t(keyGenerator: KeyGenerator, vararg args: Any = arrayOf()): String {
		return application.i18n.t(locale, keyGenerator, args)
	}
}