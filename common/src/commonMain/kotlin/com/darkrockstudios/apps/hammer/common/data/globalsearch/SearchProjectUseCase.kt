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
import com.darkrockstudios.apps.hammer.common.data.search.ParsedQuery
import com.darkrockstudios.apps.hammer.common.data.search.matchesAllTags
import com.darkrockstudios.apps.hammer.common.data.search.parseQuery
import com.darkrockstudios.apps.hammer.common.data.search.unescapeMarkdown
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

	private fun searchNotes(parsed: ParsedQuery): List<SearchResult> {
		return notes.getNotes()
			.filter { it.note.tags.matchesAllTags(parsed.tags) }
			.mapNotNull { container ->
				val snippet = matchOrPreview(container.note.content, parsed.text)
					?: return@mapNotNull null
				SearchResult.Note(
					noteId = container.note.id,
					title = firstLineTitle(container.note.content, fallback = "(empty note)"),
					snippet = snippet,
				)
			}
			.take(PER_SOURCE_CAP)
	}

	private suspend fun searchTimeline(parsed: ParsedQuery): List<SearchResult> {
		val timeline = timeLine.loadTimeline()
		return timeline.events
			.filter { it.tags.matchesAllTags(parsed.tags) }
			.mapNotNull { event ->
				val snippet = matchTimelineEvent(event.date, event.content, parsed.text)
					?: return@mapNotNull null
				SearchResult.TimelineEvent(
					eventId = event.id,
					title = event.date?.takeIf { it.isNotBlank() }
						?: firstLineTitle(event.content, fallback = "(empty event)"),
					snippet = snippet,
				)
			}
			.take(PER_SOURCE_CAP)
	}

	private suspend fun searchEncyclopedia(parsed: ParsedQuery): List<SearchResult> {
		val entries = collectEntryDefs()
		return entries
			.mapNotNull { def -> matchEncyclopediaEntry(def, parsed) }
			.take(PER_SOURCE_CAP)
	}

	private suspend fun matchEncyclopediaEntry(def: EntryDef, parsed: ParsedQuery): SearchResult? {
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
				?: matchOrPreview(entry.text, freeText, fallback = def.name)
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

		val textMatch = findMarkdownMatch(entry.text, freeText) ?: return null
		return SearchResult.EncyclopediaEntry(
			entryDef = def,
			title = def.name,
			snippet = textMatch,
		)
	}

	private suspend fun collectEntryDefs(): List<EntryDef> = encyclopedia.ensureEntriesLoaded()

	private suspend fun searchScenes(parsed: ParsedQuery): List<SearchResult> {
		val query = parsed.text
		val needTags = parsed.tags.isNotEmpty()
		val scenes = sceneEditor.getScenes().filter { it.type == SceneItem.Type.Scene }
		return scenes
			.mapNotNull { scene -> matchScene(scene, query, needTags, parsed.tags) }
			.take(PER_SOURCE_CAP)
	}

	private suspend fun matchScene(
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
			val text = withContext(dispatcherIo) { loadSceneText(scene) } ?: return null
			val bodyMatch = findMarkdownMatch(text, query) ?: return null
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
		val text = withContext(dispatcherIo) { loadSceneText(scene) } ?: return null
		val bodyMatch = findMarkdownMatch(text, query) ?: return null
		return SearchResult.Scene(sceneItem = scene, title = title, snippet = bodyMatch)
	}

	/**
	 * Both callers pass stored Markdown, so escapes are resolved before matching. With no free text
	 * the item already matched on its tags, so a blank body falls back to [fallback] and then to an
	 * empty snippet rather than discarding the result.
	 */
	private fun matchOrPreview(content: String, query: String, fallback: String = ""): AnnotatedSnippet? {
		if (query.isEmpty()) {
			return previewSnippet(unescapeMarkdown(content))
				?: previewSnippet(fallback)
				?: EMPTY_SNIPPET
		}
		return findMarkdownMatch(content, query)
	}

	/** The date is a plain-text field, so only the event body is unescaped. */
	private fun matchTimelineEvent(date: String?, content: String, query: String): AnnotatedSnippet? {
		val resolved = withDate(date, unescapeMarkdown(content))
		if (query.isEmpty()) return previewSnippet(resolved) ?: EMPTY_SNIPPET
		findMatch(resolved, query)?.let { return it }
		if (query.contains('\\')) return findMatch(withDate(date, content), query)
		return null
	}

	private fun withDate(date: String?, content: String): String =
		if (date.isNullOrBlank()) content else "$date — $content"

	private fun loadSceneText(scene: SceneItem): String? {
		val buffer = sceneContentRepository.getSceneBuffer(scene)
		val bufferText = buffer?.content?.markdown
		if (bufferText != null) return bufferText
		return runCatching { sceneEditor.loadSceneMarkdownRaw(scene) }.getOrNull()
	}

	/** [content] is stored Markdown, so the title resolves escapes the way its snippet does. */
	private fun firstLineTitle(content: String, fallback: String): String {
		val firstLine = content.lineSequence()
			.firstOrNull { it.isNotBlank() }
			?.let { unescapeMarkdown(it) }
			?.trim()
			.orEmpty()
		return when {
			firstLine.isEmpty() -> fallback
			firstLine.length > TITLE_MAX -> firstLine.take(TITLE_MAX).trimEnd() + "…"
			else -> firstLine
		}
	}

	companion object {
		const val PER_SOURCE_CAP = 25
		const val TITLE_MAX = 60
		const val SNIPPET_BEFORE = 40
		const val SNIPPET_AFTER = 80

		internal fun previewSnippet(content: String): AnnotatedSnippet? {
			if (content.isEmpty()) return null
			val flattened = content.replace(Regex("\\s+"), " ").trim()
			if (flattened.isEmpty()) return null
			val maxLen = SNIPPET_BEFORE + SNIPPET_AFTER
			val truncated = if (flattened.length > maxLen) {
				flattened.take(maxLen).trimEnd() + "…"
			} else {
				flattened
			}
			return AnnotatedSnippet(text = truncated, matchStart = 0, matchEnd = 0)
		}

		/**
		 * Matches stored Markdown with its escapes resolved, so a query matches the prose on screen
		 * and the snippet renders the same way. A query holding a backslash also tries the raw
		 * source, so searching for a literal escape keeps working.
		 */
		internal fun findMarkdownMatch(markdown: String, query: String): AnnotatedSnippet? {
			if (markdown.isEmpty() || query.isEmpty()) return null
			val resolved = unescapeMarkdown(markdown)
			findMatch(resolved, query)?.let { return it }
			if (query.contains('\\')) return findMatch(markdown, query)
			return null
		}

		internal fun findMatch(text: String, query: String): AnnotatedSnippet? {
			if (text.isEmpty() || query.isEmpty()) return null
			val pos = text.indexOf(query, ignoreCase = true)
			if (pos < 0) return null
			return buildSnippet(text, pos, query.length)
		}

		internal fun buildSnippet(text: String, matchPos: Int, queryLen: Int): AnnotatedSnippet {
			val windowStart = (matchPos - SNIPPET_BEFORE).coerceAtLeast(0)
			val windowEnd = (matchPos + queryLen + SNIPPET_AFTER).coerceAtMost(text.length)
			val rawWindow = text.substring(windowStart, windowEnd)
			val flattened = rawWindow.replace(Regex("\\s+"), " ").trim()

			val matchedTerm = text.substring(matchPos, matchPos + queryLen)
			val matchInFlattened = flattened.indexOf(matchedTerm, ignoreCase = true)

			val prefix = if (windowStart > 0) "…" else ""
			val suffix = if (windowEnd < text.length) "…" else ""
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
