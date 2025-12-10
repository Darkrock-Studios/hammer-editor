package com.darkrockstudios.apps.hammer.common.util

import io.fluidsonic.locale.Locale
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual class DeviceLocaleResolver {
	actual fun getCurrentLocale(): Locale {
		return Locale.forLanguage(NSLocale.currentLocale.languageCode)
	}
}