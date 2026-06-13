package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.Locale
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LocaleDisplayNameTest {

	private val previousDefault = java.util.Locale.getDefault()

	@BeforeEach
	fun forceUsDefault() {
		java.util.Locale.setDefault(java.util.Locale.US)
	}

	@AfterEach
	fun restoreDefault() {
		java.util.Locale.setDefault(previousDefault)
	}

	@Test
	fun displayName_languageAndRegion() {
		assertEquals("English (United States)", Locale.forLanguageTag("en-US").displayName())
	}

	@Test
	fun displayName_languageOnly() {
		assertEquals("French", Locale.forLanguageTag("fr").displayName())
	}

	@Test
	fun deviceLocaleResolver_readsJvmDefault() {
		assertEquals(Locale.forLanguageTag("en-US"), DeviceLocaleResolver().getCurrentLocale())
	}

	@Test
	fun languageUtil_readsJvmDefault() {
		assertEquals(Locale.forLanguageTag("en-US"), LanguageUtil().getCurrentLocale())
	}
}
