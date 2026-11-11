package com.darkrockstudios.apps.hammer.common.data

import com.russhwolf.settings.Settings

/** multiplatform-settings' no-arg store: SharedPreferences, java.util.prefs or NSUserDefaults. */
internal actual fun defaultSettings(): Settings = Settings()
