package com.darkrockstudios.apps.hammer.common.util

import io.fluidsonic.locale.Locale

actual class DeviceLocaleResolver {
	actual fun getCurrentLocale(): Locale {
		val locale = java.util.Locale.getDefault()
		return Locale.forLanguageTag(locale.toLanguageTag())
	}
}