package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.libs.platformspellchecker.SpLocale
import kotlin.test.Test
import kotlin.test.assertEquals

class ToSpLocaleTest {

	@Test
	fun nullLanguageFallsBackToDefault() {
		// A root / unparseable locale must not crash spell-checker init.
		assertEquals(SpLocale.EN_US.language, Locale.root.toSpLocale().language)
		assertEquals(SpLocale.EN_US.language, Locale.forLanguageTag("").toSpLocale().language)
	}

	@Test
	fun languageAndRegionPassThrough() {
		val spLocale = Locale.forLanguageTag("en-US").toSpLocale()
		assertEquals("en", spLocale.language)
		assertEquals("US", spLocale.country)
	}
}
