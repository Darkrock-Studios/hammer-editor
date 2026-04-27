package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ChapterHeadingLevel
import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

class MarkdownStoryImporter : StoryImporter {
	override val format: ImportFormat = ImportFormat.Markdown

	override fun preview(
		sourceName: String,
		content: String,
		options: ImportOptions,
	): ImportPreview {
		val cleanSourceName = sanitizeName(sourceName)
		val lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n")

		val headingLevel = when (options.chapterHeadingLevel) {
			ChapterHeadingLevel.H1 -> 1
			ChapterHeadingLevel.H2 -> 2
		}

		val segments = mutableListOf<Segment>()
		var current: Segment? = null
		val leadingBuffer = StringBuilder()
		var sawHeading = false

		for (line in lines) {
			val headingTitle = matchHeading(line, headingLevel)
			if (headingTitle != null) {
				current?.let { segments.add(it) }
				current = Segment(name = sanitizeName(headingTitle))
				sawHeading = true
			} else if (current != null) {
				current.body.appendLine(line)
			} else {
				leadingBuffer.appendLine(line)
			}
		}
		current?.let { segments.add(it) }

		if (!sawHeading) {
			val body = leadingBuffer.toString().trimEnd()
			if (body.isEmpty()) return ImportPreview(emptyList())
			return ImportPreview(listOf(PreviewItem.Scene(name = cleanSourceName, markdown = body)))
		}

		val items = mutableListOf<PreviewItem>()

		val leadingBody = leadingBuffer.toString().trimEnd()
		if (leadingBody.isNotEmpty()) {
			items.add(PreviewItem.Scene(name = UNTITLED, markdown = leadingBody))
		}

		segments.forEach { segment ->
			val body = segment.body.toString().trimEnd()
			val name = segment.name
			val item = if (options.createChapterGroups) {
				PreviewItem.Group(
					name = name,
					scenes = listOf(PreviewItem.Scene(name = name, markdown = body)),
				)
			} else {
				PreviewItem.Scene(name = name, markdown = body)
			}
			items.add(item)
		}

		return ImportPreview(items)
	}

	private fun matchHeading(line: String, level: Int): String? {
		var i = 0
		while (i < line.length && line[i] == '#') i++
		if (i != level) return null
		return line.substring(i).trim()
	}

	private fun sanitizeName(raw: String): String {
		val cleaned = ProjectsRepository.sanitizeFileName(raw)
		return cleaned.ifEmpty { UNTITLED }
	}

	private class Segment(
		val name: String,
		val body: StringBuilder = StringBuilder(),
	)

	companion object {
		private const val UNTITLED = "Untitled"
	}
}
