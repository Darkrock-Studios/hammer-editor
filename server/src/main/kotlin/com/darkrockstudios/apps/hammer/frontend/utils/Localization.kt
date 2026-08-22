package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.LOCALE_COOKIE_NAME
import com.darkrockstudios.apps.hammer.plugin.PluginRegistry
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

private fun messagesBundle(locale: Locale): ResourceBundle =
	ResourceBundle.getBundle("i18n.Messages", locale)

/**
 * Bundle lookup that falls back to English for keys a translation doesn't
 * carry yet, instead of throwing MissingResourceException.
 */
private fun localizedString(bundle: ResourceBundle, key: String): String =
	try {
		bundle.getString(key)
	} catch (_: MissingResourceException) {
		messagesBundle(Locale.ENGLISH).getString(key)
	}

suspend fun ApplicationCall.msg(key: String, vararg args: Any): String {
	val locale = getLocale()
	val message = localizedString(messagesBundle(locale), key)
	return if (args.isEmpty()) message else MessageFormat.format(message, *args)
}

/**
 * For callers that already resolved the locale. [ApplicationCall.msg] re-resolves it per call,
 * which costs an uncached config lookup for requests carrying no locale hint.
 */
fun localizedMsg(locale: Locale, key: String, vararg args: Any): String {
	val message = localizedString(messagesBundle(locale), key)
	return if (args.isEmpty()) message else MessageFormat(message, locale).format(args)
}

suspend fun ApplicationCall.msg(data: MutableMap<String, Any>, key: String, vararg args: Any) {
	val message = msg(key, *args)
	val msgData = data["msg"] as? MutableMap<String, String> ?: mutableMapOf()
	msgData[key] = message
}

suspend fun ApplicationCall.withMessages(data: Map<String, Any> = emptyMap()): MutableMap<String, Any> {
	val locale = getLocale()
	val bundle = messagesBundle(locale)

	// English first as the complete baseline, then the locale's translations on
	// top — untranslated keys render in English rather than as blank template vars.
	val messages = mutableMapOf<String, Any>()
	val english = messagesBundle(Locale.ENGLISH)
	english.keys.asSequence().forEach { key ->
		messages[key] = english.getString(key)
	}
	bundle.keys.asSequence().forEach { key ->
		messages[key] = bundle.getString(key)
	}

	// Plugin bundles layer on top, same English-baseline rule per bundle.
	val pluginRegistry = runCatching { application.get<PluginRegistry>() }.getOrNull()
	pluginRegistry?.plugins?.mapNotNull { it.messageBundle() }?.forEach { bundleName ->
		val pluginEnglish = ResourceBundle.getBundle(bundleName, Locale.ENGLISH)
		pluginEnglish.keys.asSequence().forEach { key ->
			messages[key] = pluginEnglish.getString(key)
		}
		val pluginLocalized = ResourceBundle.getBundle(bundleName, locale)
		pluginLocalized.keys.asSequence().forEach { key ->
			messages[key] = pluginLocalized.getString(key)
		}
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
