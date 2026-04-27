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
		_state.getAndUpdate { it.copy(query = query) }
		startSearch(query, _state.value.filter, debounce = true)
	}

	fun setFilter(filter: GlobalSearchFilter) {
		if (_state.value.filter == filter) return
		_state.getAndUpdate { it.copy(filter = filter) }
		startSearch(_state.value.query, filter, debounce = false)
	}

	private fun startSearch(query: String, filter: GlobalSearchFilter, debounce: Boolean) {
		searchJob?.cancel()

		if (query.length < MIN_QUERY_LENGTH) {
			_state.getAndUpdate { it.copy(isSearching = false, results = emptyList()) }
			return
		}

		searchJob = scope.launch {
			try {
				if (debounce) delay(DEBOUNCE_MS)
				_state.getAndUpdate { it.copy(isSearching = true) }
				val results = runSearch(query, filter)
				_state.getAndUpdate { it.copy(isSearching = false, results = results) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Napier.e("Global search failed", e)
				_state.getAndUpdate { it.copy(isSearching = false) }
			}
		}
	}

	private suspend fun runSearch(query: String, filter: GlobalSearchFilter): List<SearchResult> = coroutineScope {
		val notesDeferred = async {
			if (filter.includesNotes) searchNotes(query) else emptyList()
		}
		val timelineDeferred = async {
			if (filter.includesTimeline) searchTimeline(query) else emptyList()
		}
		val encyclopediaDeferred = async {
			if (filter.includesEncyclopedia) searchEncyclopedia(query) else emptyList()
		}
		val scenesDeferred = async {
			if (filter.includesScenes) searchScenes(query) else emptyList()
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

	private fun searchNotes(query: String): List<SearchResult> {
		return notes.getNotes()
			.mapNotNull { container ->
				val match = findMatch(container.note.content, query) ?: return@mapNotNull null
				SearchResult.Note(
					noteId = container.note.id,
					title = firstLineTitle(container.note.content, fallback = "(empty note)"),
					snippet = match,
				)
			}
			.take(PER_SOURCE_CAP)
	}

	private suspend fun searchTimeline(query: String): List<SearchResult> {
		val timeline = timeLine.loadTimeline()
		return timeline.events
			.mapNotNull { event ->
				val combined = if (event.date.isNullOrBlank()) {
					event.content
				} else {
					"${event.date} — ${event.content}"
				}
				val match = findMatch(combined, query) ?: return@mapNotNull null
				SearchResult.TimelineEvent(
					eventId = event.id,
					title = event.date?.takeIf { it.isNotBlank() }
						?: firstLineTitle(event.content, fallback = "(empty event)"),
					snippet = match,
				)
			}
			.take(PER_SOURCE_CAP)
	}

	private suspend fun searchEncyclopedia(query: String): List<SearchResult> {
		val entries = collectEntryDefs() ?: return emptyList()
		return entries
			.mapNotNull { def -> matchEncyclopediaEntry(def, query) }
			.take(PER_SOURCE_CAP)
	}

	private suspend fun matchEncyclopediaEntry(def: EntryDef, query: String): SearchResult? {
		val nameMatch = findMatch(def.name, query)
		if (nameMatch != null) {
			return SearchResult.EncyclopediaEntry(
				entryDef = def,
				title = def.name,
				snippet = nameMatch,
			)
		}

		val container = withContext(dispatcherIo) {
			runCatching { encyclopedia.loadEntry(def) }.getOrNull()
		} ?: return null
		val entry = container.entry

		val tagMatch = entry.tags.firstNotNullOfOrNull { tag ->
			findMatch(tag, query)?.let { tag to it }
		}
		if (tagMatch != null) {
			val (tag, snippet) = tagMatch
			return SearchResult.EncyclopediaEntry(
				entryDef = def,
				title = "${def.name}  •  #$tag",
				snippet = snippet,
			)
		}

		val textMatch = findMatch(entry.text, query) ?: return null
		return SearchResult.EncyclopediaEntry(
			entryDef = def,
			title = def.name,
			snippet = textMatch,
		)
	}

	private suspend fun collectEntryDefs(): List<EntryDef>? {
		val replay = encyclopedia.entryListFlow.replayCache.firstOrNull()
		if (replay != null) return replay
		encyclopedia.loadEntriesImperative()
		return encyclopedia.entryListFlow.replayCache.firstOrNull()
	}

	private suspend fun searchScenes(query: String): List<SearchResult> {
		val scenes = sceneEditor.getScenes().filter { it.type == SceneItem.Type.Scene }
		return scenes
			.mapNotNull { scene ->
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
		const val DEBOUNCE_MS = 250L
		const val PER_SOURCE_CAP = 25
		const val TITLE_MAX = 60
		const val SNIPPET_BEFORE = 40
		const val SNIPPET_AFTER = 80

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
