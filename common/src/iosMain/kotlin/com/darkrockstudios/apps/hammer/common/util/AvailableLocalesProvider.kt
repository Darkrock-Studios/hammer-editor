package com.darkrockstudios.apps.hammer.common.util

import platform.Foundation.NSLocale
import platform.Foundation.availableLocaleIdentifiers

actual class AvailableLocalesProvider {
	actual fun allLocales(): List<Locale> =
		NSLocale.availableLocaleIdentifiers
			.filterIsInstance<String>()
			.map { Locale.forLanguageTag(it) }
			.filter { it.language != null }
			.distinctBy { it.toLanguageTag() }
}
