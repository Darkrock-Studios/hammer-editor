package com.darkrockstudios.apps.hammer.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocaleTest {

	@Test
	fun forLanguageTag_languageOnly() {
		val locale = Locale.forLanguageTag("en")
		assertEquals("en", locale.language)
		assertNull(locale.region)
		assertEquals("en", locale.toLanguageTag())
	}

	@Test
	fun forLanguageTag_languageAndRegion() {
		val locale = Locale.forLanguageTag("en-US")
		assertEquals("en", locale.language)
		assertEquals("US", locale.region)
		assertEquals("en-US", locale.toLanguageTag())
	}

	@Test
	fun forLanguageTag_underscoreSeparator() {
		assertEquals(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("en_US"))
	}

	@Test
	fun forLanguageTag_normalizesCase() {
		val locale = Locale.forLanguageTag("EN-us")
		assertEquals("en", locale.language)
		assertEquals("US", locale.region)
	}

	@Test
	fun forLanguageTag_skipsScriptSubtag() {
		val locale = Locale.forLanguageTag("zh-Hant-TW")
		assertEquals("zh", locale.language)
		assertEquals("TW", locale.region)
	}

	@Test
	fun forLanguageTag_numericRegion() {
		val locale = Locale.forLanguageTag("es-419")
		assertEquals("es", locale.language)
		assertEquals("419", locale.region)
	}

	@Test
	fun forLanguageTag_blankIsRoot() {
		assertEquals(Locale.root, Locale.forLanguageTag(""))
	}

	@Test
	fun forLanguageTag_extensionSingletonNotReadAsRegion() {
		// "co" here is a Unicode -u- extension key, not a region.
		val locale = Locale.forLanguageTag("de-u-co-phonebk")
		assertEquals("de", locale.language)
		assertNull(locale.region)
	}

	@Test
	fun forLanguageTag_regionBeforeExtension() {
		val locale = Locale.forLanguageTag("en-US-u-ca-gregory")
		assertEquals("en", locale.language)
		assertEquals("US", locale.region)
	}

	@Test
	fun forLanguage_nullLanguageIsRoot() {
		assertEquals(Locale.root, Locale.forLanguage(null))
	}

	@Test
	fun forLanguage_blankLanguageIsNull() {
		assertNull(Locale.forLanguage("  ", "US").language)
	}

	@Test
	fun forLanguage_roundTripsThroughTag() {
		val locale = Locale.forLanguage(language = "en", region = "US")
		assertEquals(locale, Locale.forLanguageTag(locale.toLanguageTag()))
	}

	@Test
	fun forLanguage_equalsForLanguageTag() {
		assertEquals(Locale.forLanguageTag("en-US"), Locale.forLanguage("en", "US"))
	}

	@Test
	fun root_isEmpty() {
		assertNull(Locale.root.language)
		assertNull(Locale.root.region)
		assertTrue(Locale.root.toLanguageTag().isEmpty())
	}
}
