package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleDisplayNameTest {

	@Test
	fun displayName_isNonBlankAndCapitalized() {
		// NSLocale output is device-locale dependent, so assert the path ran and
		// produced a capitalized, non-blank name rather than an exact string.
		val name = Locale.forLanguageTag("en-US").displayName()
		assertTrue(name.isNotBlank())
		assertEquals(name.replaceFirstChar { it.uppercase() }, name)
	}

	@Test
	fun deviceLocaleResolver_returnsALanguage() {
		val language = DeviceLocaleResolver().getCurrentLocale().language
		assertTrue(language != null && language.isNotBlank())
	}
}
