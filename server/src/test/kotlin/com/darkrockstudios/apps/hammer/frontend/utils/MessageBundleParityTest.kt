package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.utilities.ResUtils
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Locale
import java.util.Properties
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * English is the fallback bundle every other locale resolves through, so a key
 * that is looked up but absent from Messages_en.properties throws
 * MissingResourceException at request time instead of degrading to untranslated
 * text.
 *
 * Parity is asserted against the keys the server actually references rather than
 * against the translation files, which Crowdin owns and which carry strings that
 * have outlived their use in the templates.
 */
class MessageBundleParityTest {

	private val kotlinSources = File("src/main/kotlin")
	private val templates = File("src/main/resources/templates")

	private val callSitePatterns = listOf(
		Regex("""Msg\.r\(\s*"([^"]+)""""),
		Regex("""\bmsg\(\s*"([^"]+)""""),
		Regex("""\bmsg\(\s*[A-Za-z_][A-Za-z0-9_]*\s*,\s*"([^"]+)""""),
		Regex("""localizedMsg\(\s*[^,]+,\s*"([^"]+)""""),
	)
	private val templatePattern = Regex("""\{\{[{&]?\s*msg\.([A-Za-z0-9_]+)""")

	private fun keysOf(locale: Locale): Set<String> {
		val properties = Properties()
		ResUtils.getResourceAsStream("i18n/Messages_$locale.properties")
			.reader(Charsets.UTF_8)
			.use { properties.load(it) }
		return properties.stringPropertyNames()
	}

	private fun filesUnder(root: File, extension: String): List<File> =
		root.walkTopDown().filter { it.isFile && it.extension == extension }.toList()

	private fun referencedKeys(): Map<String, String> {
		val references = mutableMapOf<String, String>()

		filesUnder(kotlinSources, "kt").forEach { file ->
			val text = file.readText()
			callSitePatterns.forEach { pattern ->
				pattern.findAll(text).forEach { match ->
					val key = match.groupValues[1]
					// Interpolated keys are assembled at runtime and cannot be resolved here.
					if (!key.contains('$')) references.putIfAbsent(key, file.name)
				}
			}
		}

		filesUnder(templates, "mustache").forEach { file ->
			val text = file.readText()
			templatePattern.findAll(text).forEach { match ->
				references.putIfAbsent(match.groupValues[1], file.name)
			}
		}

		return references
	}

	@Test
	fun `every referenced message key resolves in English`() {
		assertTrue(kotlinSources.isDirectory, "Kotlin sources not found at ${kotlinSources.absolutePath}")
		assertTrue(templates.isDirectory, "Templates not found at ${templates.absolutePath}")

		val references = referencedKeys()
		// A regex that quietly stops matching would turn this into a vacuous pass.
		assertTrue(references.size > 200, "Only found ${references.size} message references; the scan is broken")

		val englishKeys = keysOf(Locale.ENGLISH)
		val missing = references.filterKeys { it !in englishKeys }

		if (missing.isNotEmpty()) {
			val report = missing.toSortedMap().entries.joinToString("\n") { (key, source) -> "  $key (used by $source)" }
			fail(
				"Messages_en.properties is the fallback bundle and must carry every key the server looks up.\n" +
					"$report\n" +
					"If a Crowdin sync rewrote the English source file, restore the dropped keys."
			)
		}
	}

	@Test
	fun `every English key resolves to a non-blank value`() {
		val properties = Properties()
		ResUtils.getResourceAsStream("i18n/Messages_en.properties")
			.reader(Charsets.UTF_8)
			.use { properties.load(it) }

		val blank = properties.stringPropertyNames().filter { properties.getProperty(it).isNullOrBlank() }

		assertTrue(blank.isEmpty(), "English keys with blank values: ${blank.sorted()}")
	}
}
