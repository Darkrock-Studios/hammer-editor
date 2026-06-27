package com.darkrockstudios.apps.hammer.common.data.importer

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

internal const val UNTITLED = "Untitled"

/** Cleans an imported heading/source name into something storable, falling back to [UNTITLED]. */
internal fun sanitizeImportName(raw: String): String =
	ProjectsRepository.sanitizeFileName(raw).ifEmpty { UNTITLED }

/**
 * A single unit of an import stream. A [Heading] opens a group or scene depending on its [level]
 * relative to the chosen scene level; a [Body] line is prose for the open scene.
 */
internal sealed interface ImportToken {
	/**
	 * [title] is the sanitized name used when this heading opens a group/scene. [raw] is the original
	 * line, appended verbatim as body when the heading is deeper than the chosen scene level.
	 */
	data class Heading(val level: Int, val title: String, val raw: String) : ImportToken
	data class Body(val line: String) : ImportToken
}

/**
 * Folds an [ImportToken] stream into top-level [PreviewItem]s. Headings shallower than [sceneLevel]
 * open groups, scene-level headings open scenes, deeper headings fall back to body text, and any
 * other line is body for the open scene (creating an implicit "Untitled" scene when prose appears
 * with no scene open). When no scene-or-shallower heading is seen, the whole document collapses to a
 * single scene named [fallbackName].
 */
internal fun buildImportPreview(
	tokens: List<ImportToken>,
	sceneLevel: Int,
	wrapTopLevelInGroups: Boolean,
	fallbackName: String,
): ImportPreview {
	val builder = StructureBuilder(wrapTopLevelInGroups)
	var sawHeading = false

	for (token in tokens) {
		when (token) {
			is ImportToken.Heading -> when {
				token.level < sceneLevel -> {
					builder.startGroup(token.title)
					sawHeading = true
				}

				token.level == sceneLevel -> {
					builder.startScene(token.title)
					sawHeading = true
				}

				else -> builder.appendBody(token.raw)
			}

			is ImportToken.Body -> builder.appendBody(token.line)
		}
	}

	if (!sawHeading) {
		val body = builder.leadingBody()
		if (body.isBlank()) return ImportPreview(emptyList())
		return ImportPreview(listOf(PreviewItem.Scene(name = fallbackName, markdown = body.trim())))
	}

	return ImportPreview(builder.build())
}

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

	private class SceneAcc(val name: String, val explicit: Boolean) {
		val body = StringBuilder()
	}

	private class GroupAcc(val name: String) {
		val scenes = mutableListOf<PreviewItem.Scene>()
	}
}
