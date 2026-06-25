package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.ChapterHeadingLevel
import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions

class MarkdownStoryImporter : StoryImporter {
	override val format: ImportFormat = ImportFormat.Markdown

	override fun preview(
		sourceName: String,
		content: ByteArray,
		options: ImportOptions,
	): ImportPreview {
		val text = content.decodeToString()
		val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")

		val tokens = lines.map { line ->
			val heading = parseHeading(line)
			if (heading != null) {
				ImportToken.Heading(heading.level, sanitizeImportName(heading.title), raw = line)
			} else {
				ImportToken.Body(line)
			}
		}

		val fallbackName = sanitizeImportName(sourceName)
		return when (options.chapterHeadingLevel) {
			ChapterHeadingLevel.H1 -> buildImportPreview(tokens, 1, options.createChapterGroups, fallbackName)
			ChapterHeadingLevel.H2 -> buildImportPreview(tokens, 2, options.createChapterGroups, fallbackName)
			ChapterHeadingLevel.Auto -> previewAuto(tokens, options.createChapterGroups, fallbackName)
		}
	}

	/**
	 * Detects the chapter level instead of asking for it: a lone heading at the shallowest level,
	 * sitting above everything, is taken as the story title (surfaced via [ImportPreview.title] and
	 * dropped from the scenes). The chapter/scene level is then the most frequent remaining level, so
	 * any shallower headings (e.g. parts) fold into groups.
	 */
	private fun previewAuto(
		tokens: List<ImportToken>,
		wrapTopLevelInGroups: Boolean,
		fallbackName: String,
	): ImportPreview {
		val headings = tokens.withIndex().filter { it.value is ImportToken.Heading }
		if (headings.isEmpty()) {
			return buildImportPreview(tokens, 1, wrapTopLevelInGroups, fallbackName)
		}

		val levels = headings.map { (it.value as ImportToken.Heading).level }
		val minLevel = levels.min()
		val firstHeading = headings.first().value as ImportToken.Heading
		val titleIndex = if (firstHeading.level == minLevel && levels.count { it == minLevel } == 1) {
			headings.first().index
		} else {
			null
		}

		val title = titleIndex?.let { (tokens[it] as ImportToken.Heading).title }
		val workingTokens = if (titleIndex != null) {
			tokens.filterIndexed { index, _ -> index != titleIndex }
		} else {
			tokens
		}

		val sceneLevel = workingTokens
			.filterIsInstance<ImportToken.Heading>()
			.groupingBy { it.level }
			.eachCount()
			.entries
			// Most headings wins (chapters outnumber parts); ties resolve to the shallower level.
			.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
			.firstOrNull()?.key ?: 1

		return buildImportPreview(workingTokens, sceneLevel, wrapTopLevelInGroups, fallbackName)
			.copy(title = title)
	}

	private fun parseHeading(line: String): Heading? {
		var start = 0
		if (start < line.length && line[start] == BOM) start++
		var leadingSpaces = 0
		while (start < line.length && line[start] == ' ' && leadingSpaces < MAX_HEADING_INDENT) {
			start++
			leadingSpaces++
		}

		var i = start
		while (i < line.length && line[i] == '#') i++
		val level = i - start
		if (level !in 1..MAX_HEADING_LEVEL) return null

		return Heading(level = level, title = line.substring(i).trim())
	}

	private class Heading(val level: Int, val title: String)

	companion object {
		private const val MAX_HEADING_LEVEL = 6
		private const val MAX_HEADING_INDENT = 3
		private const val BOM = '﻿'
	}
}
