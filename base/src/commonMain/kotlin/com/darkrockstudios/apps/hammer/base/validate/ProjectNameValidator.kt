package com.darkrockstudios.apps.hammer.base.validate

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Project / file name validation results. Shared by client and server so both enforce the
 * same rules for what a project (and scene) name may contain.
 */
enum class ProjectNameValidationResult {
	VALID,
	NULL,
	BLANK,
	INVALID_CHARACTERS,
	TOO_LONG,
}

/**
 * Shared project-name validator, used by both client and server to keep validation consistent.
 *
 * This object only defines what a user is allowed to *type*. The client additionally maps a few
 * OS-forbidden characters to filesystem-safe lookalikes when storing a project on disk (see
 * `ProjectsRepository` in `:common`); that encoding is a storage concern and lives there.
 */
object ProjectNameValidator {
	const val MAX_LENGTH = 128

	/** Delimiter used in scene filenames (e.g. `order~name~id.md`). Reserved — disallowed in user input. */
	const val FILENAME_DELIMITER = '~'

	// Allowed characters in user-entered project/scene names. Includes:
	//   - letters (\p{L}), digits, space, _, ', +
	//   - natively-OS-safe punctuation: -.,!?:()&"
	//   - encoded-on-disk via lookalike map: /\*|<>
	//   - typographic quotes: ’ “ ” (U+2019, U+201C, U+201D)
	// Disallowed: ~ (reserved delimiter), control chars, leading/trailing dot, trailing space,
	// Windows reserved names.
	private val allowedCharRegex = Regex("""[\d\p{L}+ _'\-.,!?:()&"/\\*|<>’“”]""")
	private val allowedNameRegex = Regex("""[\d\p{L}+ _'\-.,!?:()&"/\\*|<>’“”]+""")

	// Windows reserved basenames (case-insensitive, with or without extension). The superscript
	// COM¹/LPT¹ variants Windows also reserves are already excluded by the allowed-character set.
	private val windowsReservedNames: Set<String> = buildSet {
		addAll(listOf("CON", "PRN", "AUX", "NUL"))
		for (i in 0..9) {
			add("COM$i")
			add("LPT$i")
		}
	}

	/** True if [ch] may appear in a project/scene name. Used by the client when sanitizing names. */
	fun isCharacterAllowed(ch: Char): Boolean = allowedCharRegex.matches(ch.toString())

	private fun isWindowsReservedName(name: String): Boolean {
		val basename = name.substringBeforeLast('.', name).uppercase()
		return basename in windowsReservedNames
	}

	/**
	 * Validates a project/file name, returning the first failure found or
	 * [ProjectNameValidationResult.VALID]. The checks run in the order the UI relies on:
	 * null, then blank, then invalid characters, then length.
	 *
	 * [usedAsRawFilename] is true when the name becomes a filesystem basename verbatim (a project
	 * directory). Scene/group titles are stored wrapped as `order~name~id`, so for them a leading
	 * dot or a Windows reserved word can never collide on disk; pass false to allow those. A
	 * trailing `.`/` ` is rejected either way — the on-disk encoder strips it, so it could never
	 * survive in the stored name.
	 */
	fun validate(name: String?, usedAsRawFilename: Boolean = true): ProjectNameValidationResult {
		if (name == null) return ProjectNameValidationResult.NULL
		if (name.isBlank()) return ProjectNameValidationResult.BLANK

		val charactersValid = allowedNameRegex.matches(name) &&
			!name.endsWith('.') &&
			!name.endsWith(' ')
		val rawFilenameValid = !usedAsRawFilename ||
			(!name.startsWith('.') && !isWindowsReservedName(name))
		if (!charactersValid || !rawFilenameValid) return ProjectNameValidationResult.INVALID_CHARACTERS

		if (name.length > MAX_LENGTH) return ProjectNameValidationResult.TOO_LONG

		return ProjectNameValidationResult.VALID
	}
}

const val MAX_PROJECT_NAME_LENGTH = ProjectNameValidator.MAX_LENGTH

/**
 * Backwards-compatible boolean wrapper for existing call sites (notably the server). Prefer
 * [ProjectNameValidator.validate] when you need to know *why* a name was rejected.
 */
@OptIn(ExperimentalContracts::class)
fun validateProjectName(name: String?): Boolean {
	contract {
		returns(true) implies (name != null)
	}
	return ProjectNameValidator.validate(name) == ProjectNameValidationResult.VALID
}
