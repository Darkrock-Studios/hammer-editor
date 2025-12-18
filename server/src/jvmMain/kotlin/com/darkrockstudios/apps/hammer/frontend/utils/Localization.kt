package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.frontend.LOCALE_COOKIE_NAME
import com.darkrockstudios.apps.hammer.utilities.ResUtils
import io.ktor.http.*
import io.ktor.server.application.*
import java.text.MessageFormat
import java.util.*

/**
 * Checks for a user's logged-in session preferences first,
 * then falls back to signed-out locale cookie
 */
fun ApplicationCall.getLocale(): Locale {
	// Try cookie preference
	val cookieLocale = request.cookies[LOCALE_COOKIE_NAME]
	return if (!cookieLocale.isNullOrBlank()) {
		Locale.forLanguageTag(cookieLocale)
	} else {
		// Fallback to Accept-Language header
		val acceptLanguage = request.headers[HttpHeaders.AcceptLanguage]
		acceptLanguage?.let {
			Locale.forLanguageTag(it.split(",")[0].trim())
		} ?: Locale.ENGLISH
	}
}

fun ApplicationCall.t(key: String, vararg args: Any): String {
	val locale = getLocale()
	val bundle = ResourceBundle.getBundle("i18n.Messages", locale)
	val message = bundle.getString(key)
	return if (args.isEmpty()) message else MessageFormat.format(message, *args)
}

fun ApplicationCall.withMessages(data: Map<String, Any> = emptyMap()): Map<String, Any> {
	val locale = getLocale()
	val bundle = ResourceBundle.getBundle("i18n.Messages", locale)

	val messages = bundle.keys.asSequence().associateWith { key ->
		bundle.getString(key)
	}

	val availableLocales = ResUtils.getTranslatedLocales()
	val localesForTemplate = availableLocales.map { lc ->
		mapOf(
			"tag" to lc.toLanguageTag(),
			// Show each language's name in its own language (autoglottonym)
			"label" to lc.getDisplayName(lc),
			"selected" to (lc.language.equals(locale.language, ignoreCase = true) &&
				(lc.country.isEmpty() || lc.country.equals(locale.country, ignoreCase = true)))
		)
	}

	return data + mapOf(
		"msg" to messages,
		"locale" to locale.toLanguageTag(),
		"locales" to localesForTemplate
	)
}