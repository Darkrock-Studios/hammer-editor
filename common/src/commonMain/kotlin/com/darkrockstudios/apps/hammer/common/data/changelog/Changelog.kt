package com.darkrockstudios.apps.hammer.common.data.changelog

import com.darkrockstudios.apps.hammer.base.BuildMetadata

data class Changelog(
	val version: String,
	val date: String?,
	val notes: String,
)

private val HEADER = Regex("""^##\s*\[([^]]+)]\s*(?:-\s*(.+))?$""")

/**
 * Parses one `## [version] - date` CHANGELOG.md entry. A file without that header is still
 * usable — it becomes notes for the running version — so a hand-edited resource never
 * silently shows nothing.
 */
fun parseChangelog(text: String): Changelog? {
	if (text.isBlank()) return null

	val lines = text.trimStart().lines()
	val match = HEADER.matchEntire(lines.first().trim())
		?: return Changelog(
			version = BuildMetadata.APP_VERSION,
			date = null,
			notes = text.trim(),
		)

	val notes = lines.drop(1).joinToString("\n").trim()
	return Changelog(
		version = match.groupValues[1].trim(),
		date = match.groupValues[2].trim().ifEmpty { null },
		notes = notes,
	)
}
