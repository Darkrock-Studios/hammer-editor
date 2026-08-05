package com.darkrockstudios.apps.hammer.common.util

/** Enumerates every locale the platform knows about, for pickers that need the full list. */
expect class AvailableLocalesProvider {
	fun allLocales(): List<Locale>
}
