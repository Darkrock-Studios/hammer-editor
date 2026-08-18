package com.darkrockstudios.apps.hammer.desktop.sandbox

import io.github.aakira.napier.Napier
import java.util.Base64
import java.util.Locale

/**
 * Reads the first-run dialog strings straight out of the packaged Compose
 * Resources value files, without going through Compose Resources itself.
 *
 * That detour exists because `getString()` cannot be called before the Compose
 * application starts. On desktop it resolves a [org.jetbrains.compose.resources.ResourceEnvironment]
 * via `getSystemEnvironment()`, which calls `Toolkit.getDefaultToolkit()` to
 * pick a density qualifier — and that initializes AWT. AWT then owns `NSApp`,
 * the Tao backend's event loop gets no events, and the app runs on with no
 * window and no crash (see [SandboxStartup]). Supplying our own environment
 * isn't an option either: `ResourceEnvironment`'s constructor is internal.
 *
 * The `.cvr` files are a Compose Resources implementation detail — an ASCII
 * `version:0` header followed by `string|<name>|<base64 utf-8>` lines, with
 * XML escapes already resolved at build time, so a value here matches what
 * `getString()` would have returned. Reading them keeps the translations
 * Crowdin manages rather than forking a second copy of these strings. Since
 * the format is not a public contract, every step degrades rather than throws:
 * an unknown version or unreadable file falls back to English, and a key missing
 * from every layer falls back to the key name, so a Compose upgrade can spoil
 * the dialog text but never block startup.
 */
internal object SandboxStrings {

	private const val RESOURCE_DIR = "composeResources/com.darkrockstudios.apps.hammer"
	private const val FILE_NAME = "strings_desktop.commonMain.cvr"
	private const val SUPPORTED_VERSION = "version:0"
	private const val DEFAULT_VALUES_DIR = "values"

	const val INTRO_TITLE = "sandbox_intro_title"
	const val INTRO_MESSAGE = "sandbox_intro_message"
	const val PICKER_TITLE = "sandbox_picker_title"
	const val CHOOSE_FOLDER_BUTTON = "sandbox_choose_folder_button"
	const val QUIT_BUTTON = "sandbox_quit_button"
	const val RETRY_MESSAGE = "sandbox_retry_message"

	/** Localized value for [key], falling back to the key itself if it can't be resolved. */
	fun get(key: String): String = strings[key]?.takeIf { it.isNotBlank() } ?: key

	private val strings: Map<String, String> by lazy { load(Locale.getDefault()) }

	/**
	 * Layers each value file over the less specific ones beneath it, so English
	 * still backs a key a translation is missing. Compose's own fallback is
	 * per-key; taking a single value file wholesale would render the raw key name
	 * for anything Crowdin has not round-tripped yet.
	 */
	internal fun load(locale: Locale): Map<String, String> =
		candidateDirs(locale).asReversed().fold(emptyMap<String, String>()) { merged, dir ->
			merged + (readValues(dir) ?: emptyMap())
		}

	/** Most specific locale first, English last — mirrors Compose's own qualifier fallback. */
	internal fun candidateDirs(locale: Locale): List<String> = buildList {
		val language = locale.language
		val country = locale.country
		if (language.isNotEmpty() && country.isNotEmpty()) add("$DEFAULT_VALUES_DIR-$language-r$country")
		if (language.isNotEmpty()) add("$DEFAULT_VALUES_DIR-$language")
		add(DEFAULT_VALUES_DIR)
	}

	private fun readValues(valuesDir: String): Map<String, String>? {
		val path = "$RESOURCE_DIR/$valuesDir/$FILE_NAME"
		val text = runCatching {
			javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
		}.onFailure { Napier.w("Could not read sandbox strings from $path", it) }.getOrNull() ?: return null

		return parse(text, path)
	}

	internal fun parse(text: String, path: String = FILE_NAME): Map<String, String>? {
		val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
		if (lines.firstOrNull()?.trim() != SUPPORTED_VERSION) {
			Napier.w("Unexpected Compose Resources value-file version in $path — ignoring it")
			return null
		}

		// Values are base64 on a single line each, so multi-line strings survive
		// this line-based parse intact.
		val parsed = lines.drop(1).mapNotNull { line ->
			val parts = line.split('|', limit = 3)
			if (parts.size < 3 || parts[0] != "string") return@mapNotNull null
			val decoded = runCatching { Base64.getDecoder().decode(parts[2]).decodeToString() }.getOrNull()
			// Blank values are dropped rather than kept, so an empty translation
			// falls through to the English layer instead of shadowing it.
			decoded?.trim()?.takeIf { it.isNotEmpty() }?.let { parts[1] to it }
		}.toMap()

		return parsed.ifEmpty { null }
	}
}
