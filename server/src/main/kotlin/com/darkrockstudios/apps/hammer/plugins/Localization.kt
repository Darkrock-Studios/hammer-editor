package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.utilities.ResUtils
import com.github.aymanizz.ktori18n.I18n
import com.github.aymanizz.ktori18n.KeyGenerator
import com.github.aymanizz.ktori18n.MessageResolver
import com.github.aymanizz.ktori18n.ResourceBundleMessageResolver
import io.ktor.server.application.*
import java.util.*

fun Application.configureLocalization() {
	install(I18n) {
		supportedLocales = ResUtils.getTranslatedLocales()
		defaultLocale = Locale.ENGLISH
		messageResolver = EnglishFallbackMessageResolver()
		//useOfCookie = true
		//useOfRedirection = true
		//excludePrefixes("/api")
	}
}

/**
 * A locale bundle has no parent bundle to inherit from, so a key its translation
 * doesn't carry yet resolves against English rather than throwing
 * MissingResourceException at the caller.
 */
private class EnglishFallbackMessageResolver : MessageResolver {
	private val delegate = ResourceBundleMessageResolver()

	override fun t(locale: Locale, keyGenerator: KeyGenerator, vararg args: Any): String =
		try {
			delegate.t(locale, keyGenerator, *args)
		} catch (e: MissingResourceException) {
			if (locale.language == Locale.ENGLISH.language) throw e
			delegate.t(Locale.ENGLISH, keyGenerator, *args)
		}
}
