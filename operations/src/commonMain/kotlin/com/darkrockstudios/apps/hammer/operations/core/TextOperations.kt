package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OperationContext
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.Serializable

internal fun textOperations(): List<Operation<*, *>> = listOf(
	operation<TextCountInput, TextCounts>(
		name = "text.count",
		description = "Exact count of each term in scene text, per scene: every occurrence, not one hit per scene as " +
			"search gives. Matches whole words unless wholeWord is false, where search matches inside words, and " +
			"ignores case unless caseSensitive. Ignores italics and curly quotes, and skips titles, outlines, notes, " +
			"and archived scenes.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		if (input.terms.isEmpty() || input.terms.size > MAX_TERMS) invalidInput("terms must hold 1 to $MAX_TERMS terms")
		val matchers = input.terms.mapIndexed { i, term ->
			matcher(term, "terms[$i]", input.caseSensitive, input.wholeWord)
		}

		val found = input.terms.map { mutableListOf<TermScene>() }
		var scanned = 0
		forEachScene(input.project, input.group, input.ids) { scene, markdown ->
			scanned++
			val text = normalizeText(markdown)
			matchers.forEachIndexed { i, matcher ->
				val count = matcher.count(text)
				if (count > 0) found[i] += TermScene(scene.id, scene.name, count)
			}
		}
		TextCounts(
			scenesScanned = scanned,
			terms = input.terms.mapIndexed { i, term -> TermCount(term, found[i].sumOf { it.count }, found[i]) },
		)
	},
	operation<TextFindInput, TextMatches>(
		name = "text.find",
		description = "Every occurrence of a term in scene text, in story order, with the text around it. Matches as " +
			"text.count does; total is always the full count, and next is the offset of the following page.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		val matcher = matcher(input.term, "term", input.caseSensitive, input.wholeWord)
		if (input.limit !in 1..MAX_LIMIT) invalidInput("limit must be from 1 to $MAX_LIMIT")
		if (input.offset < 0) invalidInput("offset must be at least 0")

		// Every scene is scanned for the total, but only this page's matches are kept.
		var total = 0
		val matches = mutableListOf<TextMatch>()
		forEachScene(input.project, input.group, input.ids) { scene, markdown ->
			val text = normalizeText(markdown)
			for (start in matcher.matches(text)) {
				if (total >= input.offset && total - input.offset < input.limit) {
					matches += TextMatch(scene.id, scene.name, context(text, start, start + matcher.term.length))
				}
				total++
			}
		}
		TextMatches(
			total = total,
			matches = matches,
			next = if (total - input.offset > input.limit) input.offset + input.limit else null,
		)
	},
)

private fun matcher(term: String, name: String, caseSensitive: Boolean, wholeWord: Boolean): TextMatcher =
	TextMatcher(term, caseSensitive, wholeWord).also {
		if (it.term.isEmpty()) invalidInput("$name is blank")
	}

/** Runs [action] on the markdown, unsaved edits included, of each scene chosen as scene.read.many chooses them. */
private suspend fun OperationContext.forEachScene(
	project: String,
	group: Int?,
	ids: List<Int>?,
	action: (SceneItem, String) -> Unit,
) {
	projects.withProject(project) { open ->
		val content = open.scope.get<SceneContentRepository>()
		open.scope.get<SceneEditorService>().storyScenes(group, ids).forEach { (scene) ->
			action(scene, content.getCurrentSceneContent(scene))
		}
	}
}

private const val MAX_TERMS = 50
private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 200

@Serializable
data class TextCountInput(
	val project: String,
	/** Forms to count, each separately. */
	val terms: List<String>,
	val caseSensitive: Boolean = false,
	/** Not inside a longer word. */
	val wholeWord: Boolean = true,
	/** Only this group's scenes. */
	val group: Int? = null,
	/** Only these scenes. */
	val ids: List<Int>? = null,
)

@Serializable
data class TextCounts(val scenesScanned: Int, val terms: List<TermCount>)

@Serializable
data class TermCount(
	val term: String,
	val total: Int,
	/** Only the scenes the term is in, in story order. */
	val scenes: List<TermScene>,
)

@Serializable
data class TermScene(val id: Int, val name: String, val count: Int)

@Serializable
data class TextFindInput(
	val project: String,
	val term: String,
	val caseSensitive: Boolean = false,
	/** Not inside a longer word. */
	val wholeWord: Boolean = true,
	/** Only this group's scenes. */
	val group: Int? = null,
	/** Only these scenes. */
	val ids: List<Int>? = null,
	val limit: Int = DEFAULT_LIMIT,
	val offset: Int = 0,
)

@Serializable
data class TextMatches(
	/** Every match, not only this page's. */
	val total: Int,
	val matches: List<TextMatch>,
	/** The offset of the following page; null on the last. */
	val next: Int?,
)

@Serializable
data class TextMatch(
	val id: Int,
	val name: String,
	/** The paragraph around the match, normalised, cut with an ellipsis about 80 characters either side. */
	val context: String,
)
