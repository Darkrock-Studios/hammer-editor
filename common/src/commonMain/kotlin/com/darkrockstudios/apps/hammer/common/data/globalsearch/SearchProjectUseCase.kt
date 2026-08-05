package com.darkrockstudios.apps.hammer.common.data.globalsearch

import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.search.MarkdownProjector
import com.darkrockstudios.apps.hammer.common.data.search.MarkdownProjectorPool
import com.darkrockstudios.apps.hammer.common.data.search.ParsedQuery
import com.darkrockstudios.apps.hammer.common.data.search.matchesAllTags
import com.darkrockstudios.apps.hammer.common.data.search.parseQuery
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

/** A tag match with nothing to preview: the result stands on the tag alone. */
private val EMPTY_SNIPPET = AnnotatedSnippet(text = "", matchStart = 0, matchEnd = 0)

/**
 * Stateless cross-repo project search. Given a query and a filter it fans out across the
 * scene/notes/encyclopedia/timeline repositories and returns the matched results. Holds no state.
 */
class SearchProjectUseCase(
	private val sceneEditor: SceneRepository,
	private val sceneMetadataRepository: SceneMetadataRepository,
	private val sceneContentRepository: SceneContentRepository,
	private val notes: NotesRepository,
	private val encyclopedia: EncyclopediaRepository,
	private val timeLine: TimeLineRepository,
) : KoinComponent {

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val dispatcherIo: CoroutineContext by inject(named(DISPATCHER_IO))

	// Outlives a single search so the buffers one scan grew are still sized for the next keystroke.
	private val projectors = MarkdownProjectorPool()

	suspend fun search(query: String, filter: GlobalSearchFilter): List<SearchResult> {
		val parsed = parseQuery(query)
		if (!parsed.isUsable()) return emptyList()
		return withContext(dispatcherDefault) { runSearch(parsed, filter) }
	}

	private suspend fun runSearch(parsed: ParsedQuery, filter: GlobalSearchFilter): List<SearchResult> =
		coroutineScope {
			val notesDeferred = async {
				if (filter.includesNotes) searchNotes(parsed) else emptyList()
			}
			val timelineDeferred = async {
				if (filter.includesTimeline) searchTimeline(parsed) else emptyList()
			}
			val encyclopediaDeferred = async {
				if (filter.includesEncyclopedia) searchEncyclopedia(parsed) else emptyList()
			}
			val scenesDeferred = async {
				if (filter.includesScenes) searchScenes(parsed) else emptyList()
			}

			awaitAll(notesDeferred, timelineDeferred, encyclopediaDeferred, scenesDeferred)
				.flatten()
		}

	private val GlobalSearchFilter.includesScenes: Boolean
		get() = this == GlobalSearchFilter.All || this == GlobalSearchFilter.Scenes
	private val GlobalSearchFilter.includesNotes: Boolean
		get() = this == GlobalSearchFilter.All || this == GlobalSearchFilter.Notes
	private val GlobalSearchFilter.includesEncyclopedia: Boolean
		get() = this == GlobalSearchFilter.All || this == GlobalSearchFilter.Encyclopedia
	private val GlobalSearchFilter.includesTimeline: Boolean
		get() = this == GlobalSearchFilter.All || this == GlobalSearchFilter.Timeline

	private suspend fun searchNotes(parsed: ParsedQuery): List<SearchResult> =
		projectors.borrow { projector ->
			notes.getNotes()
				.filter { it.note.tags.matchesAllTags(parsed.tags) }
				.mapNotNull { container ->
					val snippet = matchOrPreview(projector, container.note.content, parsed.text)
						?: return@mapNotNull null
					SearchResult.Note(
						noteId = container.note.id,
						title = firstLineTitle(projector, container.note.content, fallback = "(empty note)"),
						snippet = snippet,
					)
				}
				.take(PER_SOURCE_CAP)
		}

	private suspend fun searchTimeline(parsed: ParsedQuery): List<SearchResult> = projectors.borrow { projector ->
		val timeline = timeLine.loadTimeline()
		timeline.events
			.filter { it.tags.matchesAllTags(parsed.tags) }
			.mapNotNull { event ->
				val snippet = matchTimelineEvent(projector, event.date, event.content, parsed.text)
					?: return@mapNotNull null
				SearchResult.TimelineEvent(
					eventId = event.id,
					title = event.date?.takeIf { it.isNotBlank() }
						?: firstLineTitle(projector, event.content, fallback = "(empty event)"),
					snippet = snippet,
				)
			}
			.take(PER_SOURCE_CAP)
	}

	private suspend fun searchEncyclopedia(parsed: ParsedQuery): List<SearchResult> =
		projectors.borrow { projector ->
			collectEntryDefs()
				.mapNotNull { def -> matchEncyclopediaEntry(projector, def, parsed) }
				.take(PER_SOURCE_CAP)
		}

	private suspend fun matchEncyclopediaEntry(
		projector: MarkdownProjector,
		def: EntryDef,
		parsed: ParsedQuery,
	): SearchResult? {
		val needTags = parsed.tags.isNotEmpty()
		val freeText = parsed.text

		if (!needTags) {
			val nameMatch = findMatch(def.name, freeText)
			if (nameMatch != null) {
				return SearchResult.EncyclopediaEntry(
					entryDef = def,
					title = def.name,
					snippet = nameMatch,
				)
			}
		}

		val container = withContext(dispatcherIo) {
			runCatching { encyclopedia.loadEntry(def) }.getOrNull()
		} ?: return null
		val entry = container.entry

		if (needTags && !entry.tags.matchesAllTags(parsed.tags)) return null

		if (needTags) {
			val matchedTag = entry.tags.firstOrNull { tag ->
				parsed.tags.any { tag.contains(it, ignoreCase = true) }
			}
			val title = if (matchedTag != null) "${def.name}  •  #$matchedTag" else def.name
			val snippet = findMatch(def.name, freeText)
				?: matchOrPreview(projector, entry.text, freeText, fallback = def.name)
				?: return null
			return SearchResult.EncyclopediaEntry(
				entryDef = def,
				title = title,
				snippet = snippet,
			)
		}

		val tagMatch = entry.tags.firstNotNullOfOrNull { tag ->
			findMatch(tag, freeText)?.let { tag to it }
		}
		if (tagMatch != null) {
			val (tag, snippet) = tagMatch
			return SearchResult.EncyclopediaEntry(
				entryDef = def,
				title = "${def.name}  •  #$tag",
				snippet = snippet,
			)
		}

		val textMatch = findMarkdownMatch(projector, entry.text, freeText) ?: return null
		return SearchResult.EncyclopediaEntry(
			entryDef = def,
			title = def.name,
			snippet = textMatch,
		)
	}

	private suspend fun collectEntryDefs(): List<EntryDef> = encyclopedia.ensureEntriesLoaded()

	private suspend fun searchScenes(parsed: ParsedQuery): List<SearchResult> =
		projectors.borrow { projector ->
			val query = parsed.text
			val needTags = parsed.tags.isNotEmpty()
			sceneEditor.getScenes()
				.filter { it.type == SceneItem.Type.Scene }
				.mapNotNull { scene -> matchScene(projector, scene, query, needTags, parsed.tags) }
				.take(PER_SOURCE_CAP)
		}

	private suspend fun matchScene(
		projector: MarkdownProjector,
		scene: SceneItem,
		query: String,
		needTags: Boolean,
		tagNeedles: List<String>,
	): SearchResult? {
		if (!needTags) {
			if (query.isEmpty()) return null
			val nameMatch = findMatch(scene.name, query)
			if (nameMatch != null) {
				return SearchResult.Scene(sceneItem = scene, title = scene.name, snippet = nameMatch)
			}
			val loaded = withContext(dispatcherIo) { projectScene(projector, scene) }
			if (!loaded) return null
			val bodyMatch = findProjectedMatch(projector, query) ?: return null
			return SearchResult.Scene(sceneItem = scene, title = scene.name, snippet = bodyMatch)
		}

		val metadata = withContext(dispatcherIo) {
			runCatching { sceneMetadataRepository.loadSceneMetadata(scene.id) }.getOrNull()
		} ?: return null
		if (!metadata.tags.matchesAllTags(tagNeedles)) return null

		val matchedTag = metadata.tags.firstOrNull { tag ->
			tagNeedles.any { tag.contains(it, ignoreCase = true) }
		}
		val title = if (matchedTag != null) "${scene.name}  •  #$matchedTag" else scene.name

		if (query.isEmpty()) {
			val snippet = previewSnippet(scene.name) ?: EMPTY_SNIPPET
			return SearchResult.Scene(sceneItem = scene, title = title, snippet = snippet)
		}
		val nameMatch = findMatch(scene.name, query)
		if (nameMatch != null) {
			return SearchResult.Scene(sceneItem = scene, title = title, snippet = nameMatch)
		}
		val loaded = withContext(dispatcherIo) { projectScene(projector, scene) }
		if (!loaded) return null
		val bodyMatch = findProjectedMatch(projector, query) ?: return null
		return SearchResult.Scene(sceneItem = scene, title = title, snippet = bodyMatch)
	}

	/**
	 * Both callers pass stored Markdown, so both paths go through the prose projection. With no free
	 * text the item already matched on its tags, so a blank body falls back to [fallback] and then to
	 * an empty snippet rather than discarding the result.
	 */
	private fun matchOrPreview(
		projector: MarkdownProjector,
		content: String,
		query: String,
		fallback: String = "",
	): AnnotatedSnippet? {
		if (query.isEmpty()) {
			return markdownPreviewSnippet(projector, content)
				?: previewSnippet(fallback)
				?: EMPTY_SNIPPET
		}
		return findMarkdownMatch(projector, content, query)
	}

	/**
	 * The date is a plain-text field, so only the body is projected. Both halves are searched as one
	 * string so a query can span the separator.
	 */
	private fun matchTimelineEvent(
		projector: MarkdownProjector,
		date: String?,
		content: String,
		query: String,
	): AnnotatedSnippet? {
		projector.project(content)
		val projected = withDate(date, projector.projected())
		if (query.isEmpty()) {
			return previewSnippet(projected)
				?: previewSnippet(withDate(date, content))
				?: EMPTY_SNIPPET
		}
		findMatch(projected, query)?.let { return it }
		return findMatch(withDate(date, content), query)
	}

	private fun withDate(date: String?, content: String): String =
		if (date.isNullOrBlank()) content else "$date — $content"

	/**
	 * Loads the scene into [projector] and returns whether there is anything to match. An unsaved
	 * scene is already in memory as a string; a saved one is decoded straight off disk into the
	 * projector's own buffer, so scanning a project takes no string per file.
	 */
	private fun projectScene(projector: MarkdownProjector, scene: SceneItem): Boolean {
		val bufferText = sceneContentRepository.getSceneBuffer(scene)?.content?.markdown
		if (bufferText != null) {
			if (bufferText.isEmpty()) return false
			projector.project(bufferText)
			return true
		}
		val chars = runCatching {
			sceneEditor.readSceneMarkdownInto(scene, projector)
		}.getOrDefault(0)
		if (chars <= 0) return false
		projector.projectSource(chars)
		return true
	}

	/** [content] is stored Markdown, so the derived title is projected the same way the UI does. */
	private fun firstLineTitle(
		projector: MarkdownProjector,
		content: String,
		fallback: String,
	): String {
		projector.project(content)
		val title = projector.firstNonBlankLine().ifEmpty {
			content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
		}
		return when {
			title.isEmpty() -> fallback
			title.length > TITLE_MAX -> title.take(TITLE_MAX).trimEnd() + "…"
			else -> title
		}
	}

	companion object {
		const val PER_SOURCE_CAP = 25
		const val TITLE_MAX = 60
		const val SNIPPET_BEFORE = 40
		const val SNIPPET_AFTER = 80

		/** Compiled once: snippet assembly runs per result, and `Regex(…)` rebuilds the pattern. */
		private val WHITESPACE_RUN = Regex("\\s+")

		internal fun previewSnippet(content: String): AnnotatedSnippet? {
			if (content.isEmpty()) return null
			val flattened = content.replace(WHITESPACE_RUN, " ").trim()
			if (flattened.isEmpty()) return null
			val maxLen = SNIPPET_BEFORE + SNIPPET_AFTER
			val truncated = if (flattened.length > maxLen) {
				flattened.take(maxLen).trimEnd() + "…"
			} else {
				flattened
			}
			return AnnotatedSnippet(text = truncated, matchStart = 0, matchEnd = 0)
		}

		internal fun findMatch(text: String, query: String): AnnotatedSnippet? {
			if (text.isEmpty() || query.isEmpty()) return null
			val pos = text.indexOf(query, ignoreCase = true)
			if (pos < 0) return null
			return buildSnippet(text, pos, query.length)
		}

		/**
		 * Matches against the prose projection of stored Markdown, so queries spanning storage
		 * syntax still hit and the snippet reads the way the document does. The raw source is
		 * searched as a fallback, which keeps queries for literal markup working.
		 *
		 * The projection is matched inside [projector]'s buffer, so a document that does not match
		 * costs nothing but the scan.
		 */
		internal fun findMarkdownMatch(
			projector: MarkdownProjector,
			markdown: String,
			query: String,
		): AnnotatedSnippet? {
			if (markdown.isEmpty() || query.isEmpty()) return null
			projector.project(markdown)
			return findProjectedMatch(projector, query)
		}

		/**
		 * Matches a document [projector] already holds, whether it was copied in or read straight
		 * off disk. The raw fallback searches the source buffer, so neither path needs the document
		 * as a string.
		 */
		internal fun findProjectedMatch(
			projector: MarkdownProjector,
			query: String,
		): AnnotatedSnippet? {
			if (query.isEmpty()) return null
			val pos = projector.indexOf(query)
			if (pos >= 0) {
				return buildSnippetFrom(projector.length, pos, query.length, projector::substring)
			}
			return null
		}

		/** Falls back to the raw source so a document made only of markup still previews. */
		internal fun markdownPreviewSnippet(
			projector: MarkdownProjector,
			markdown: String,
		): AnnotatedSnippet? {
			if (markdown.isEmpty()) return null
			projector.project(markdown)
			val preview = projector.collapsedPreview(SNIPPET_BEFORE + SNIPPET_AFTER)
			if (preview.isNotEmpty()) {
				return AnnotatedSnippet(text = preview, matchStart = 0, matchEnd = 0)
			}
			return previewSnippet(markdown)
		}

		internal fun buildSnippet(text: String, matchPos: Int, queryLen: Int): AnnotatedSnippet =
			buildSnippetFrom(text.length, matchPos, queryLen, text::substring)

		/**
		 * Only the snippet window is copied out of [slice], so a hit costs a line rather than a
		 * document, whether the text lives in a String or in a scan buffer.
		 */
		private fun buildSnippetFrom(
			totalLength: Int,
			matchPos: Int,
			queryLen: Int,
			slice: (Int, Int) -> String,
		): AnnotatedSnippet {
			val windowStart = (matchPos - SNIPPET_BEFORE).coerceAtLeast(0)
			val windowEnd = (matchPos + queryLen + SNIPPET_AFTER).coerceAtMost(totalLength)
			return assembleSnippet(
				rawWindow = slice(windowStart, windowEnd),
				matchedTerm = slice(matchPos, matchPos + queryLen),
				hasPrefix = windowStart > 0,
				hasSuffix = windowEnd < totalLength,
				queryLen = queryLen,
			)
		}

		private fun assembleSnippet(
			rawWindow: String,
			matchedTerm: String,
			hasPrefix: Boolean,
			hasSuffix: Boolean,
			queryLen: Int,
		): AnnotatedSnippet {
			val flattened = rawWindow.replace(WHITESPACE_RUN, " ").trim()
			val matchInFlattened = flattened.indexOf(matchedTerm, ignoreCase = true)

			val prefix = if (hasPrefix) "…" else ""
			val suffix = if (hasSuffix) "…" else ""
			val snippet = prefix + flattened + suffix

			val highlightStart = if (matchInFlattened >= 0) prefix.length + matchInFlattened else prefix.length
			val highlightEnd = (highlightStart + queryLen).coerceAtMost(snippet.length)
			return AnnotatedSnippet(
				text = snippet,
				matchStart = highlightStart,
				matchEnd = highlightEnd,
			)
		}
	}
}
