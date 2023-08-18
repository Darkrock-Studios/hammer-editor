package com.darkrockstudios.apps.hammer.plugins.kweb

import com.github.aymanizz.ktori18n.I18n
import com.github.aymanizz.ktori18n.KeyGenerator
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.i18n
import io.ktor.http.*
import io.ktor.server.application.*
import kweb.html.Document
import java.util.*

class KwebLocalizer(
	application: Application,
	private val doc: Document,
	defaultLocale: Locale
) {
	private val log = application.log
	private val i18n: I18n = application.i18n
	private var curLocale: Locale = defaultLocale

	init {
		// Cookie overrides all
		val cookieLocale = getCookieLocale()
		if (cookieLocale != null) {
			log.info("Setting locale from cookie: ${cookieLocale.toLanguageTag()}")
			this.curLocale = cookieLocale
		} else {
			// Otherwise fallback to header locale
			val headerLocale = getHeaderLocale()
			if (headerLocale != null) {
				log.info("Setting locale from header: ${headerLocale.toLanguageTag()}")
				this.curLocale = headerLocale
			} else {
				log.info("Locale not set, fallback back to server default: ${defaultLocale.toLanguageTag()}")
			}
		}
	}

	fun t(key: String, vararg args: Any): String = t(R(key), *args)

	fun t(keyGenerator: KeyGenerator, vararg args: Any): String {
		return try {
			i18n.t(curLocale, keyGenerator, *args)
		} catch (e: MissingResourceException) {
			i18n.t(Locale.ENGLISH, keyGenerator, *args)
		}
	}

	fun overrideLocale(locale: Locale, refresh: Boolean = true) {
		doc.cookie.set("locale", locale.toLanguageTag())
		curLocale = locale

		if (refresh) {
			reload()
		}
	}

	private fun getHeaderLocale(): Locale? {
		val acceptLanguageHeader = doc.browser.httpRequestInfo.request.headers[HttpHeaders.AcceptLanguage]
		val acceptableLocales = Locale.LanguageRange.parse(acceptLanguageHeader)
		val language = acceptableLocales.firstOrNull()?.range
		return if (language != null) {
			Locale.Builder().setLanguageTag(language).build()
		} else {
			null
		}
	}

	private fun getCookieLocale(): Locale? {
		val localeTag = doc.browser.httpRequestInfo.cookies["locale"]
		return if (localeTag != null) {
			Locale.Builder().setLanguageTag(localeTag).build()
		} else {
			null
		}
	}

	private fun reload() {
		//doc.browser.url.value = doc.browser.url.value
		doc.browser.callJsFunction("location.reload(true);");
	}
}