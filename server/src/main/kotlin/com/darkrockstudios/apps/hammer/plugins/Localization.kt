package com.darkrockstudios.apps.hammer.plugins

import com.github.aymanizz.ktori18n.I18n
import io.ktor.server.application.*

fun Application.configureLocalization() {
	install(I18n) {
		supportedLocales = listOf("en", "de").map(java.util.Locale::forLanguageTag)
		//useOfCookie = true
		//useOfRedirection = true
		//excludePrefixes("/api")
	}
}