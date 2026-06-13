package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleDisplayNameTest {

	private val previousDefault = java.util.Locale.getDefault()

	@BeforeTest
	fun forceUsDefault() {
		java.util.Locale.setDefault(java.util.Locale.US)
	}

	@AfterTest
	fun restoreDefault() {
		java.util.Locale.setDefault(previousDefault)
	}

	@Test
	fun displayName_languageAndRegion() {
		assertEquals("English (United States)", Locale.forLanguageTag("en-US").displayName())
	}

	@Test
	fun deviceLocaleResolver_readsJvmDefault() {
		assertEquals(Locale.forLanguageTag("en-US"), DeviceLocaleResolver().getCurrentLocale())
	}
}
