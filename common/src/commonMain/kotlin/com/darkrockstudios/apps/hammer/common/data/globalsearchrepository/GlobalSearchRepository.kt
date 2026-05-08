package com.darkrockstudios.apps.hammer.common.data.globalsearchrepository

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearch
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext

class GlobalSearchRepository(
	projectDef: ProjectDef,
	private val sceneEditor: SceneEditorRepository,
	private val notes: NotesRepository,
	private val encyclopedia: EncyclopediaRepository,
	private val timeLine: TimeLineRepository,
) : ScopeCallback, ProjectScoped, KoinComponent {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val dispatcherIo: CoroutineContext by inject(named(DISPATCHER_IO))

	private val scope = CoroutineScope(dispatcherDefault)
	private var searchJob: Job? = null

	private val _state = MutableValue(GlobalSearch.State())
	val state: Value<GlobalSearch.State> = _state

	init {
		projectScope.scope.registerCallback(this)
	}

	fun setQuery(query: String) {
		val parsed = parseQuery(query)
		_state.getAndUpdate {
			it.copy(query = query, parsedText = parsed.text, parsedTags = parsed.tags)
		}
		startSearch(parsed, _state.value.filter, debounce = true)
	}

	fun setFilter(filter: GlobalSearchFilter) {
		if (_state.value.filter == filter) return
		_state.getAndUpdate { it.copy(filter = filter) }
		startSearch(parseQuery(_state.value.query), filter, debounce = false)
	}

	private fun startSearch(parsed: ParsedQuery, filter: GlobalSearchFilter, debounce: Boolean) {
		searchJob?.cancel()

		if (!parsed.isUsable()) {
			_state.getAndUpdate { it.copy(isSearching = false, results = emptyList()) }
			return
		}

		searchJob = scope.launch {
			try {
				if (debounce) delay(DEBOUNCE_MS)
				_state.getAndUpdate { it.copy(isSearching = true) }
				val results = runSearch(parsed, filter)
				_state.getAndUpdate { it.copy(isSearching = false, results = results) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Napier.e("Global search failed", e)
				_state.getAndUpdate { it.copy(isSearching = false) }
			}
		}
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
			// Scenes have no tags — skip them when the user is filtering by tag.
			if (filter.includesScenes && parsed.tags.isEmpty()) searchScenes(parsed) else emptyList()
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
				val combined = if (event.date.isNullOrBlank()) {
					event.content
				} else {
					"${event.date} — ${event.content}"
				}
				val snippet = matchOrPreview(combined, parsed.text) ?: return@mapNotNull null
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
			val snippet = matchOrPreview(entry.text, freeText) ?: return null
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

		val textMatch = findMatch(entry.text, freeText) ?: return null
		return SearchResult.EncyclopediaEntry(
			entryDef = def,
			title = def.name,
			snippet = textMatch,
		)
	}

	private suspend fun collectEntryDefs(): List<EntryDef> = encyclopedia.ensureEntriesLoaded()

	private suspend fun searchScenes(parsed: ParsedQuery): List<SearchResult> {
		val query = parsed.text
		val scenes = sceneEditor.getScenes().filter { it.type == SceneItem.Type.Scene }
		return scenes
			.mapNotNull { scene ->
				if (query.isEmpty()) return@mapNotNull null
				val nameMatch = findMatch(scene.name, query)
				if (nameMatch != null) {
					return@mapNotNull SearchResult.Scene(
						sceneItem = scene,
						title = scene.name,
						snippet = nameMatch,
					)
				}
				val text = withContext(dispatcherIo) { loadSceneText(scene) } ?: return@mapNotNull null
				val bodyMatch = findMatch(text, query) ?: return@mapNotNull null
				SearchResult.Scene(
					sceneItem = scene,
					title = scene.name,
					snippet = bodyMatch,
				)
			}
			.take(PER_SOURCE_CAP)
	}

	private fun matchOrPreview(content: String, query: String): AnnotatedSnippet? {
		if (query.isEmpty()) return previewSnippet(content)
		return findMatch(content, query)
	}

	private fun loadSceneText(scene: SceneItem): String? {
		val buffer = sceneEditor.getSceneBuffer(scene)
		val bufferText = buffer?.content?.markdown
		if (bufferText != null) return bufferText
		return runCatching { sceneEditor.loadSceneMarkdownRaw(scene) }.getOrNull()
	}

	private fun firstLineTitle(content: String, fallback: String): String {
		val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
		return when {
			firstLine.isEmpty() -> fallback
			firstLine.length > TITLE_MAX -> firstLine.take(TITLE_MAX).trimEnd() + "…"
			else -> firstLine
		}
	}

	override fun onScopeClose(scope: Scope) {
		this.scope.cancel("Closing GlobalSearchRepository")
	}

	companion object {
		const val MIN_QUERY_LENGTH = 2
		const val MIN_TAG_LENGTH = 1
		const val DEBOUNCE_MS = 250L
		const val PER_SOURCE_CAP = 25
		const val TITLE_MAX = 60
		const val SNIPPET_BEFORE = 40
		const val SNIPPET_AFTER = 80

		internal data class ParsedQuery(
			val text: String,
			val tags: List<String>,
		) {
			fun isUsable(): Boolean {
				val tagOk = tags.any { it.length >= MIN_TAG_LENGTH }
				val textOk = text.length >= MIN_QUERY_LENGTH
				return tagOk || textOk
			}
		}

		internal fun parseQuery(query: String): ParsedQuery {
			val tags = mutableListOf<String>()
			val textBuilder = StringBuilder()
			var i = 0
			while (i < query.length) {
				val c = query[i]
				if (c == '#') {
					i++
					val tagStart = i
					while (i < query.length && !query[i].isWhitespace()) i++
					if (i > tagStart) tags.add(query.substring(tagStart, i))
				} else {
					textBuilder.append(c)
					i++
				}
			}
			val text = textBuilder.toString().replace(Regex("\\s+"), " ").trim()
			return ParsedQuery(text = text, tags = tags)
		}

		internal fun Set<String>.matchesAllTags(needles: List<String>): Boolean {
			if (needles.isEmpty()) return true
			return needles.all { needle ->
				any { it.contains(needle, ignoreCase = true) }
			}
		}

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
