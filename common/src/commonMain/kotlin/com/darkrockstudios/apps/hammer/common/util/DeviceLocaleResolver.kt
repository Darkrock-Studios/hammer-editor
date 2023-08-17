package com.darkrockstudios.apps.hammer.common.util

import io.fluidsonic.locale.Locale

expect class DeviceLocaleResolver {
	fun getCurrentLocale(): Locale
}