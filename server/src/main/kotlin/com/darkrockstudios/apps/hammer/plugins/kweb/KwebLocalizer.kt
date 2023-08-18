package com.darkrockstudios.apps.hammer.plugins.kweb

import com.github.aymanizz.ktori18n.I18n
import com.github.aymanizz.ktori18n.KeyGenerator
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.i18n
import io.ktor.server.application.*
import kweb.html.Document
import java.util.*

class KwebLocalizer(
	application: Application,
	private val doc: Document,
	defaultLocale: Locale
) {
	private val i18n: I18n = application.i18n
	private var curLocale: Locale = defaultLocale

	init {
		val locale = getLocale()
		if (locale == null) {
			setLocale(curLocale)
		} else if (curLocale != locale) {
			this.curLocale = locale
		}
	}

	fun t(key: String, vararg args: Any = arrayOf()): String {
		return i18n.t(curLocale, R(key), args)
	}

	fun t(keyGenerator: KeyGenerator, vararg args: Any = arrayOf()): String {
		return i18n.t(curLocale, keyGenerator, args)
	}

	fun setLocale(locale: Locale, refresh: Boolean = true) {
		doc.cookie.set("locale", locale.toLanguageTag())
		curLocale = locale

		if (refresh) {
			reload()
		}
	}

	fun getLocale(): Locale? {
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