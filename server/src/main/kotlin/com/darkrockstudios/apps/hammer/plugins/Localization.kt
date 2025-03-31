package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.utilities.ResUtils
import com.github.aymanizz.ktori18n.I18n
import io.ktor.server.application.*
import java.util.*

fun Application.configureLocalization() {
	install(I18n) {
		supportedLocales = ResUtils.getTranslatedLocales()
		defaultLocale = Locale.ENGLISH
		//useOfCookie = true
		//useOfRedirection = true
		//excludePrefixes("/api")
	}
}