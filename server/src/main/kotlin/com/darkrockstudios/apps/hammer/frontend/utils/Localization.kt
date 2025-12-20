package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.LOCALE_COOKIE_NAME
import com.darkrockstudios.apps.hammer.utilities.ResUtils
import io.ktor.http.*
import io.ktor.server.application.*
import org.koin.ktor.ext.get
import java.text.MessageFormat
import java.util.*

/**
 * Checks for a user's logged-in session preferences first,
 * then falls back to signed-out locale cookie
 */
suspend fun ApplicationCall.getLocale(): Locale {
	// Try cookie preference
	val cookieLocale = request.cookies[LOCALE_COOKIE_NAME]
	if (!cookieLocale.isNullOrBlank()) {
		return Locale.forLanguageTag(cookieLocale)
	}

	// Fallback to Accept-Language header
	val acceptLanguage = request.headers[HttpHeaders.AcceptLanguage]
	if (!acceptLanguage.isNullOrBlank()) {
		return Locale.forLanguageTag(acceptLanguage.split(",")[0].trim())
	}

	// Fallback to Server Config
	return try {
		val configRepository: ConfigRepository = application.get()
		val defaultTag = configRepository.get(AdminServerConfig.DEFAULT_LOCALE)
		Locale.forLanguageTag(defaultTag)
	} catch (_: Exception) {
		Locale.ENGLISH
	}
}

suspend fun ApplicationCall.msg(key: String, vararg args: Any): String {
	val locale = getLocale()
	val bundle = ResourceBundle.getBundle("i18n.Messages", locale)
	val message = bundle.getString(key)
	return if (args.isEmpty()) message else MessageFormat.format(message, *args)
}

suspend fun ApplicationCall.msg(data: MutableMap<String, Any>, key: String, vararg args: Any) {
	val locale = getLocale()
	val bundle = ResourceBundle.getBundle("i18n.Messages", locale)
	val message = bundle.getString(key)
	val msgData = data["msg"] as? MutableMap<String, String> ?: mutableMapOf()
	msgData[key] = if (args.isEmpty()) message else MessageFormat.format(message, *args)
}

suspend fun ApplicationCall.withMessages(data: Map<String, Any> = emptyMap()): MutableMap<String, Any> {
	val locale = getLocale()
	val bundle = ResourceBundle.getBundle("i18n.Messages", locale)

	val messages = mutableMapOf<String, Any>()
	bundle.keys.asSequence().forEach { key ->
		messages[key] = bundle.getString(key)
	}

	val availableLocales = ResUtils.getTranslatedLocales()
	val localesForTemplate = availableLocales.map { lc ->
		mapOf(
			"tag" to lc.toLanguageTag(),
			"label" to lc.getDisplayName(lc),
			"selected" to (lc.language.equals(locale.language, ignoreCase = true) &&
				(lc.country.isEmpty() || lc.country.equals(locale.country, ignoreCase = true)))
		)
	}

	return mutableMapOf(
		"msg" to messages,
		"locale" to locale.toLanguageTag(),
		"locales" to localesForTemplate
	).apply { putAll(data) }
}
