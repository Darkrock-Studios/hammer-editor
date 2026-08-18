package com.darkrockstudios.apps.hammer.desktop.sandbox

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SandboxStrings] reads a Compose Resources implementation detail (the `.cvr`
 * value files). These tests are the tripwire for that coupling: if a Compose
 * upgrade changes the format or the resource path, the first-run dialogs would
 * silently degrade to showing raw key names, and only on a Mac App Store first
 * launch. Catch it here instead.
 */
class SandboxStringsTest {

	@Test
	fun `every first-run dialog key resolves to real text`() {
		val keys = listOf(
			SandboxStrings.INTRO_TITLE,
			SandboxStrings.INTRO_MESSAGE,
			SandboxStrings.PICKER_TITLE,
			SandboxStrings.CHOOSE_FOLDER_BUTTON,
			SandboxStrings.QUIT_BUTTON,
			SandboxStrings.RETRY_MESSAGE,
		)
		keys.forEach { key ->
			val value = SandboxStrings.get(key)
			// get() falls back to the key name, so equality means it failed to load.
			assertNotEquals(key, value, "'$key' did not resolve from the packaged value files")
			assertTrue(value.isNotBlank(), "'$key' resolved to blank text")
		}
	}

	@Test
	fun `english values load and match the known source text`() {
		val strings = SandboxStrings.load(Locale.ENGLISH)
		assertEquals("Welcome to Hammer", strings[SandboxStrings.INTRO_TITLE])
		assertEquals("Choose Folder", strings[SandboxStrings.CHOOSE_FOLDER_BUTTON])
		assertEquals("Quit", strings[SandboxStrings.QUIT_BUTTON])
	}

	@Test
	fun `a translated locale resolves to its own text`() {
		// French is checked in rather than generated, so it is a stable fixture.
		val french = SandboxStrings.load(Locale.FRENCH)
		val english = SandboxStrings.load(Locale.ENGLISH)
		assertNotEquals(
			english[SandboxStrings.INTRO_TITLE],
			french[SandboxStrings.INTRO_TITLE],
			"French should not fall back to the English value file",
		)
	}

	@Test
	fun `a translation missing a key still resolves it from english`() {
		// Translated value files run behind the English one — the encyclopedia
		// strings already differ by two keys — so every locale must layer English
		// underneath rather than replace it, or an untranslated key would render
		// as its own name in the first-run dialog.
		val english = SandboxStrings.load(Locale.ENGLISH)
		listOf(Locale.FRENCH, Locale.GERMAN, Locale.ITALIAN).forEach { locale ->
			val strings = SandboxStrings.load(locale)
			english.keys.forEach { key ->
				assertTrue(
					strings[key]?.isNotBlank() == true,
					"'$key' is missing from $locale instead of falling back to English",
				)
			}
		}
	}

	@Test
	fun `unknown locale falls back to english`() {
		val strings = SandboxStrings.load(Locale.forLanguageTag("zz"))
		assertEquals("Welcome to Hammer", strings[SandboxStrings.INTRO_TITLE])
	}

	@Test
	fun `candidate dirs go most specific first and end at the default`() {
		assertEquals(
			listOf("values-pt-rBR", "values-pt", "values"),
			SandboxStrings.candidateDirs(Locale.forLanguageTag("pt-BR")),
		)
		assertEquals(listOf("values-fr", "values"), SandboxStrings.candidateDirs(Locale.FRENCH))
	}

	@Test
	fun `multi-line values survive the line-based parse`() {
		// Values are base64 on one line each, so an embedded newline must round-trip.
		val encoded = java.util.Base64.getEncoder().encodeToString("first\nsecond".toByteArray())
		val parsed = SandboxStrings.parse("version:0\nstring|multiline|$encoded")
		assertEquals("first\nsecond", parsed?.get("multiline"))
	}

	@Test
	fun `an unsupported file version is rejected rather than half-parsed`() {
		val encoded = java.util.Base64.getEncoder().encodeToString("value".toByteArray())
		assertNull(SandboxStrings.parse("version:99\nstring|key|$encoded"))
	}

	@Test
	fun `malformed lines are skipped without failing the whole file`() {
		val encoded = java.util.Base64.getEncoder().encodeToString("kept".toByteArray())
		val parsed = SandboxStrings.parse(
			"""
			version:0
			garbage-with-no-delimiters
			plural|ignored_type|$encoded
			string|good|$encoded
			string|bad_base64|!!!not-base64!!!
			""".trimIndent()
		)
		assertEquals("kept", parsed?.get("good"))
		assertNull(parsed?.get("ignored_type"))
		assertNull(parsed?.get("bad_base64"))
	}
}
