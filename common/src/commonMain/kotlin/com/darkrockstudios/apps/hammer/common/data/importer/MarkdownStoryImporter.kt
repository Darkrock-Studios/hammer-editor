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

		val sceneLevel = when (options.chapterHeadingLevel) {
			ChapterHeadingLevel.H1 -> 1
			ChapterHeadingLevel.H2 -> 2
		}

		val builder = StructureBuilder(wrapTopLevelInGroups = options.createChapterGroups)
		var sawHeading = false

		for (line in lines) {
			val heading = parseHeading(line)
			when {
				heading != null && heading.level < sceneLevel -> {
					builder.startGroup(sanitizeName(heading.title))
					sawHeading = true
				}

				heading != null && heading.level == sceneLevel -> {
					builder.startScene(sanitizeName(heading.title))
					sawHeading = true
				}

				else -> builder.appendBody(line)
			}
		}

		if (!sawHeading) {
			val body = builder.leadingBody()
			if (body.isBlank()) return ImportPreview(emptyList())
			return ImportPreview(listOf(PreviewItem.Scene(name = cleanSourceName, markdown = body.trim())))
		}

		return ImportPreview(builder.build())
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

	private fun sanitizeName(raw: String): String {
		val cleaned = ProjectsRepository.sanitizeFileName(raw)
		return cleaned.ifEmpty { UNTITLED }
	}

	private class Heading(val level: Int, val title: String)

	/**
	 * Folds the heading stream into top-level [PreviewItem]s. Headings shallower than the chosen
	 * scene level open groups, scene-level headings open scenes, and any other line is body text
	 * for the open scene (creating an implicit "Untitled" scene when prose appears with no scene open).
	 */
	private class StructureBuilder(private val wrapTopLevelInGroups: Boolean) {
		private val items = mutableListOf<PreviewItem>()
		private val leading = StringBuilder()
		private var group: GroupAcc? = null
		private var scene: SceneAcc? = null

		fun appendBody(line: String) {
			val openScene = scene
			when {
				openScene != null -> openScene.body.appendLine(line)
				group != null -> scene = SceneAcc(UNTITLED, explicit = false).apply { body.appendLine(line) }
				else -> leading.appendLine(line)
			}
		}

		fun startGroup(name: String) {
			flushScene()
			flushGroup()
			flushLeading()
			group = GroupAcc(name)
		}

		fun startScene(name: String) {
			flushScene()
			flushLeading()
			scene = SceneAcc(name, explicit = true)
		}

		fun build(): List<PreviewItem> {
			flushScene()
			flushGroup()
			return items
		}

		fun leadingBody(): String = leading.toString()

		private fun flushLeading() {
			val body = leading.toString()
			if (body.isNotBlank()) {
				items.add(PreviewItem.Scene(name = UNTITLED, markdown = body.trim()))
			}
			leading.clear()
		}

		private fun flushScene() {
			val acc = scene ?: return
			scene = null
			val body = acc.body.toString().trim()
			if (!acc.explicit && body.isBlank()) return

			val sceneItem = PreviewItem.Scene(name = acc.name, markdown = body)
			val openGroup = group
			when {
				openGroup != null -> openGroup.scenes.add(sceneItem)
				wrapTopLevelInGroups -> items.add(PreviewItem.Group(name = acc.name, scenes = listOf(sceneItem)))
				else -> items.add(sceneItem)
			}
		}

		private fun flushGroup() {
			val acc = group ?: return
			group = null
			if (acc.scenes.isNotEmpty()) {
				items.add(PreviewItem.Group(name = acc.name, scenes = acc.scenes.toList()))
			}
		}
	}

	private class SceneAcc(val name: String, val explicit: Boolean) {
		val body = StringBuilder()
	}

	private class GroupAcc(val name: String) {
		val scenes = mutableListOf<PreviewItem.Scene>()
	}

	companion object {
		private const val UNTITLED = "Untitled"
		private const val MAX_HEADING_LEVEL = 6
		private const val MAX_HEADING_INDENT = 3
		private const val BOM = '﻿'
	}
}
