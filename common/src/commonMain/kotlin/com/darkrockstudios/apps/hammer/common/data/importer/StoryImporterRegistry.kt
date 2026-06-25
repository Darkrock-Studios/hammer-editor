package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ImportFormat

/** Resolves the right [StoryImporter] for an import, by format or by source file name. */
class StoryImporterRegistry(importers: List<StoryImporter>) {
	private val byFormat: Map<ImportFormat, StoryImporter> = importers.associateBy { it.format }

	fun forFormat(format: ImportFormat): StoryImporter =
		byFormat[format] ?: error("No importer registered for format $format")

	fun formatForFileName(name: String): ImportFormat =
		when (name.substringAfterLast('.', "").lowercase()) {
			"rtf" -> ImportFormat.Rtf
			else -> ImportFormat.Markdown
		}

	fun forFileName(name: String): StoryImporter = forFormat(formatForFileName(name))
}
