package com.darkrockstudios.apps.hammer.frontend.utils

import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * English is the fallback every other locale resolves through, so a key that is
 * looked up but absent from its English bundle throws MissingResourceException at
 * request time instead of degrading to untranslated text. Kotlin call sites resolve
 * only the default Messages bundle; templates read the msg map, which also merges
 * plugin-declared bundles.
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

	private val i18nDir = File("src/main/resources/i18n")

	private fun loadProperties(file: File): Properties {
		val properties = Properties()
		file.reader(Charsets.UTF_8).use { properties.load(it) }
		return properties
	}

	private fun englishBundleFiles(): List<File> =
		i18nDir.listFiles { f -> f.name.endsWith("_en.properties") }.orEmpty().toList()

	/** Kotlin call sites resolve only the default bundle, so they get no plugin-bundle credit. */
	private fun coreEnglishKeys(): Set<String> =
		loadProperties(File(i18nDir, "Messages_en.properties")).stringPropertyNames()

	/** Templates read the msg map, which merges plugin bundles, so the union applies. */
	private fun allEnglishKeys(): Set<String> =
		englishBundleFiles().flatMapTo(mutableSetOf()) { loadProperties(it).stringPropertyNames() }

	private fun filesUnder(root: File, extension: String): List<File> =
		root.walkTopDown().filter { it.isFile && it.extension == extension }.toList()

	private fun sourceReferences(): Map<String, String> {
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
		return references
	}

	private fun templateReferences(): Map<String, String> {
		val references = mutableMapOf<String, String>()
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

		val sourceRefs = sourceReferences()
		val templateRefs = templateReferences()
		// A regex that quietly stops matching would turn this into a vacuous pass.
		val total = sourceRefs.size + templateRefs.size
		assertTrue(total > 200, "Only found $total message references; the scan is broken")

		val coreKeys = coreEnglishKeys()
		val allKeys = allEnglishKeys()
		val missing = sourceRefs.filterKeys { it !in coreKeys } +
			templateRefs.filterKeys { it !in allKeys }

		if (missing.isNotEmpty()) {
			val report = missing.toSortedMap().entries.joinToString("\n") { (key, source) -> "  $key (used by $source)" }
			fail(
				"The English bundles are the fallback and must carry every key the server looks up.\n" +
					"$report\n" +
					"If a Crowdin sync rewrote the English source file, restore the dropped keys."
			)
		}
	}

	@Test
	fun `every English key resolves to a non-blank value`() {
		val blank = englishBundleFiles().flatMap { file ->
			val properties = loadProperties(file)
			properties.stringPropertyNames()
				.filter { properties.getProperty(it).isNullOrBlank() }
				.map { "${file.name}: $it" }
		}

		assertTrue(blank.isEmpty(), "English keys with blank values: ${blank.sorted()}")
	}
}
