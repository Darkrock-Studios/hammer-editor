package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.utilities.ResUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import java.text.MessageFormat
import java.util.*

/**
 * Checks for a user's logged-in session preferences first,
 * then falls back to signed-out locale cookie
 */
fun ApplicationCall.getLocale(): Locale {
	val session = sessions.get<UserSession>()
	if (session != null) {
		return Locale.forLanguageTag(session.locale)
	}

	// Try cookie preference
	val cookieLocale = request.cookies["locale"]
	if (!cookieLocale.isNullOrBlank()) {
		return Locale.forLanguageTag(cookieLocale)
	}

	// Fallback to Accept-Language header
	val acceptLanguage = request.headers["Accept-Language"]
	return acceptLanguage?.let {
		Locale.forLanguageTag(it.split(",")[0].trim())
	} ?: Locale.ENGLISH
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

/**
 * Helper to set the locale cookie and redirect back to the given URL.
 */
suspend fun ApplicationCall.setLocaleAndRedirect(localeTag: String, redirectTo: String?) {
	// Validate locale
	val supported = ResUtils.getTranslatedLocales().map { it.toLanguageTag() }.toSet()
	val tag = if (supported.contains(localeTag)) localeTag else Locale.ENGLISH.toLanguageTag()

	response.cookies.append(
		Cookie(
			name = "locale",
			value = tag,
			path = "/",
			maxAge = (60 * 60 * 24 * 365), // 1 year
		)
	)

	val back = redirectTo ?: request.headers["Referer"] ?: "/"
	respondRedirect(back)
}