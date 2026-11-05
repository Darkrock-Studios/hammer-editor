package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.tagindex.toRankedTagCounts
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StrResStoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.hasValidStart
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView as DrawingView
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.timeline_menu_group
import com.darkrockstudios.apps.hammer.timeline_switcher_new
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.koin.core.component.inject

/** Follows [selection]: switching resets the frame and keeps the drawing, search and filters, which prune themselves. */
class TimeLineOverviewComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val selection: Value<TimelineSelection>,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val onRequestNewTimeline: () -> Unit,
	private val onSelectTimeline: (TimelineSelection) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), TimeLineOverview {

	private val timelineService: TimelineService by projectInject()
	private val sceneRepository: SceneRepository by projectInject()
	private val entityCatalog: EntityCatalog by projectInject()
	private val strRes: StrRes by inject()

	private val selectionFlow = MutableStateFlow(selection.value)

	private val _state = MutableValue(restoredState())
	override val state: Value<TimeLineOverview.State> = _state

	private val _rankedTags = MutableValue<List<TagCount>>(emptyList())
	override val rankedTags: Value<List<TagCount>> = _rankedTags

	private val _previews = MutableValue<Map<Int, EventPreview>>(emptyMap())
	override val previews: Value<Map<Int, EventPreview>> = _previews

	// The row each preview was read from, which it stays good for, and the rows being read.
	// Both are read and written on the main thread only.
	private val previewRows = HashMap<Int, TimelineRow>()
	private val readingPreviews = HashMap<Int, TimelineRow>()

	private val frameBackCallback = BackCallback(isEnabled = false) {
		setFrame(TimelineFrame.Everything)
	}

	init {
		stateKeeper.register(VIEW_KEY, String.serializer()) { _state.value.view.name }
		stateKeeper.register(FRAME_KEY, SavedFrame.serializer()) { SavedFrame.of(_state.value.frame) }
		stateKeeper.register(FILTERS_KEY, SavedFilters.serializer()) {
			_state.value.let { SavedFilters(it.searchQuery, it.tagFilter.toList(), it.laneFilter.toList()) }
		}
		backHandler.register(frameBackCallback)
		state.subscribe(lifecycle) {
			frameBackCallback.isEnabled = it.frame != TimelineFrame.Everything
		}
		selection.subscribe(lifecycle) { onSelected(it) }
	}

	private fun onSelected(selected: TimelineSelection) {
		selectionFlow.value = selected
		_state.getAndUpdate {
			if (it.selection == selected) it else it.copy(selection = selected, rows = null, frame = TimelineFrame.Everything)
		}
	}

	private fun restoredState(): TimeLineOverview.State {
		val filters = stateKeeper.consume(FILTERS_KEY, SavedFilters.serializer()) ?: SavedFilters()
		return TimeLineOverview.State(
			searchQuery = filters.query,
			tagFilter = filters.tags.toCollection(LinkedHashSet()),
			laneFilter = filters.lanes.toCollection(LinkedHashSet()),
			view = stateKeeper.consume(VIEW_KEY, String.serializer())
				?.let { saved -> DrawingView.entries.firstOrNull { it.name == saved } }
				?: DrawingView.SEQUENCE,
			frame = stateKeeper.consume(FRAME_KEY, SavedFrame.serializer())?.toFrame() ?: TimelineFrame.Everything,
			selection = selection.value,
		)
	}

	override fun onCreate() {
		super.onCreate()

		watchTimeLine()
		watchTimelines()
	}

	override fun onStart() {
		super.onStart()
		val newTimeline = MenuItemDescriptor(NEW_TIMELINE_ITEM_ID, Res.string.timeline_switcher_new, "") {
			onRequestNewTimeline()
		}
		addMenu(MenuDescriptor(MENU_ID, Res.string.timeline_menu_group, listOf(newTimeline)))
	}

	override fun onStop() {
		super.onStop()
		removeMenu(MENU_ID)
	}

	/** What [selection] shows: its def, or for the deleted-timeline bucket a manual one without a calendar. */
	private data class Shown(val selection: TimelineSelection, val def: TimelineDef, val events: List<TimeLineEvent>)

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun shownFlow(): Flow<Shown> = selectionFlow.flatMapLatest { selected ->
		when (selected) {
			is TimelineSelection.Def -> timelineService.timelineFlow(selected.id).map { Shown(selected, it.def, it.events) }
			TimelineSelection.Orphans -> timelineService.orphansFlow.map { Shown(selected, ORPHANS_DEF, it) }
		}
	}

	private fun watchTimeLine() {
		scope.launch {
			// The scene tree is watched too, so a placed scene that is archived or unarchived redraws.
			val sceneTree = sceneRepository.sceneTreeUpdates.map { }.onStart { emit(Unit) }
			combine(shownFlow(), sceneTree) { shown, _ -> shown }.collect { view ->
				val built = buildRows(view)
				val ranked = built.rows.flatMap { it.event.tags }.groupingBy { it }.eachCount()
					.toRankedTagCounts(Int.MAX_VALUE) { it }
				withContext(dispatcherMain) {
					// A timeline switched away from while its rows were built.
					if (_state.value.selection != view.selection) return@withContext
					if (ranked != _rankedTags.value) _rankedTags.value = ranked
					val rowsById = built.rows.associateBy { it.event.id }
					val stale = previewRows.filter { (id, row) -> rowsById[id] != row }.keys
					if (stale.isNotEmpty()) {
						_previews.value -= stale
						previewRows -= stale
					}
					_state.getAndUpdate { current ->
						val laneNames = built.lanes.mapTo(HashSet()) { it.lane }
						val tagNames = built.rows.flatMapTo(HashSet()) { it.event.tags }
						// Only drawn events (dated, and not an archived scene) can be framed.
						val drawn = built.rows.mapNotNullTo(HashSet()) { row -> row.event.id.takeIf { row.isDrawn } }
						val storyAvailable = built.rows.any { it.event.kind == TimelineKind.SCENE && it.isDrawn }
						val frame = when (val frame = current.frame) {
							TimelineFrame.Everything -> frame
							TimelineFrame.Story -> frame.takeIf { storyAvailable }
							is TimelineFrame.Band -> frame.takeIf { it.eventId in drawn }
						}
						current.copy(
							rows = built.rows,
							mode = view.def.mode,
							calendar = view.def.calendar,
							undatedIds = built.undatedIds,
							lanes = built.lanes,
							tagFilter = current.tagFilter.filterTo(LinkedHashSet()) { it in tagNames },
							laneFilter = current.laneFilter.filterTo(LinkedHashSet()) { it in laneNames },
							frame = frame ?: TimelineFrame.Everything,
							storyFrameAvailable = storyAvailable,
						)
					}
				}
			}
		}
	}

	private data class Built(val rows: List<TimelineRow>, val lanes: List<LaneCount>, val undatedIds: List<Int>)

	private suspend fun buildRows(view: Shown): Built {
		val events = view.events
		val glances = timelineService.glances(events)
		val lanes = if (view.selection is TimelineSelection.Def) {
			timelineService.lanes(view.def.id)
		} else {
			events.mapNotNull { it.lane }.distinct().sortedWith(compareBy<String> { it.lowercase() }.thenBy { it })
		}
		val laneIndex = lanes.withIndex().associate { (index, lane) -> lane to index }
		val dated = view.def.mode == TimelineMode.DATED
		val calendar = view.def.calendar ?: CalendarDef()

		val rows = events.map { event ->
			val glance = glances[event.id]
			val start = event.start?.takeIf { dated && hasValidStart(event, calendar) }
			TimelineRow(
				event = event,
				title = glance?.title.orEmpty(),
				subtitle = if (event.kind == TimelineKind.SCENE) glance?.chapterPath else null,
				whenText = event.label?.trim().orEmpty(),
				laneIndex = event.lane?.let { laneIndex[it] },
				subjectMissing = glance?.missing == true,
				subjectArchived = glance?.archived == true,
				start = start,
				end = event.end.takeIf { start != null },
				calendar = calendar.takeIf { start != null },
			)
		}
		val counts = events.mapNotNull { it.lane }.groupingBy { it }.eachCount()
		val undated = if (dated) rows.filter { it.start == null }.map { it.event.id } else emptyList()
		return Built(rows, lanes.map { LaneCount(it, counts[it] ?: 0) }, undated)
	}

	private fun watchTimelines() {
		scope.launch {
			combine(timelineService.summaries, timelineService.orphansFlow) { timelines, orphans ->
				timelines to orphans.size
			}.collect { (timelines, orphanCount) ->
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(timelines = timelines, orphanCount = orphanCount) }
				}
			}
		}
	}

	override suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean {
		if (_state.value.isOrphans) return false
		return timelineService.moveEvent(event, toIndex, after).isSuccess
	}

	override suspend fun moveOrphansTo(target: String): Int {
		val words = StrResStoryDateWords(strRes)
		val targetSelection = TimelineSelection.Def(target)
		// Selected before the moves, so the bucket emptying never triggers the fall back to main.
		onSelectTimeline(targetSelection)
		val moved = timelineService.moveOrphans(target, words)
		if (moved == 0 && selection.value == targetSelection) onSelectTimeline(TimelineSelection.Orphans)
		return moved
	}

	override suspend fun deleteOrphans(): Int = timelineService.deleteOrphans()

	override fun setSearchQuery(query: String) {
		_state.getAndUpdate { if (it.searchQuery == query) it else it.copy(searchQuery = query) }
	}

	override fun toggleTag(tag: String) {
		_state.getAndUpdate { it.copy(tagFilter = it.tagFilter.toggled(tag)) }
	}

	override fun toggleLane(lane: String) {
		_state.getAndUpdate { it.copy(laneFilter = it.laneFilter.toggled(lane)) }
	}

	override fun clearFilters() {
		_state.getAndUpdate { it.copy(tagFilter = emptySet(), laneFilter = emptySet()) }
	}

	private fun Set<String>.toggled(item: String): Set<String> = if (item in this) this - item else this + item

	override fun setView(view: DrawingView) {
		_state.getAndUpdate { it.copy(view = view) }
	}

	override fun canFrame(eventId: Int): Boolean {
		val current = _state.value
		if (current.mode != TimelineMode.DATED || current.frame == TimelineFrame.Band(eventId)) return false
		val calendar = current.calendar ?: CalendarDef()
		val events = current.filtered.drawingRows.mapNotNull { it.toLayoutEvent(calendar, current.laneFilter) }
		return TimelineLayoutEngine.drawsSpan(events, current.frame, eventId)
	}

	override fun setFrame(frame: TimelineFrame) {
		_state.getAndUpdate {
			if (frame == TimelineFrame.Story && !it.storyFrameAvailable) it else it.copy(frame = frame)
		}
	}

	override fun requestPreview(eventId: Int) {
		val row = rowOf(eventId) ?: return
		if (previewRows[eventId] == row || readingPreviews[eventId] == row) return
		readingPreviews[eventId] = row
		scope.launch(dispatcherMain) {
			try {
				val preview = withContext(dispatcherDefault) { resolvePreview(row) }
				if (rowOf(eventId) == row) {
					previewRows[eventId] = row
					_previews.value += eventId to preview
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// The card keeps the row's basic preview, and a later request reads again.
				Napier.w("Failed to read the preview of timeline event $eventId", e)
			} finally {
				if (readingPreviews[eventId] == row) readingPreviews -= eventId
			}
		}
	}

	private fun rowOf(eventId: Int): TimelineRow? = _state.value.rows?.firstOrNull { it.event.id == eventId }

	private suspend fun resolvePreview(row: TimelineRow): EventPreview {
		val event = row.event
		val subject = timelineService.resolveSubject(event)
		val catalog = if (event.kind == TimelineKind.EVENT && event.references.isNotEmpty()) {
			withContext(dispatcherIo) { entityCatalog.items(setOf(CatalogKind.Scene, CatalogKind.Entry)) }
		} else {
			emptyList()
		}
		val excerpt = when (subject) {
			// A plain event without a title is titled by its first line, which the excerpt then skips.
			null -> eventExcerpt(event.content, skipTitle = row.title.takeIf { event.title.isNullOrBlank() })
			is SubjectSummary.Scene -> subject.excerpt
			is SubjectSummary.Entry -> subject.excerpt
			is SubjectSummary.Archived, SubjectSummary.Missing -> null
		}
		return row.basicPreview().copy(
			excerpt = excerpt?.takeIf { it.isNotBlank() },
			imageId = (subject as? SubjectSummary.Entry)?.primaryImageId,
			references = timelineReferenceItems(event.kind, subject, event.references, catalog).inCastOrder(),
			subjectState = when (subject) {
				SubjectSummary.Missing -> EventPreview.SubjectState.Missing
				is SubjectSummary.Archived -> EventPreview.SubjectState.Archived
				else -> EventPreview.SubjectState.Present
			},
		)
	}

	/** [TimelineFrame] as the state keeper saves it. */
	@Serializable
	private data class SavedFrame(val story: Boolean = false, val bandId: Int? = null) {
		fun toFrame(): TimelineFrame = when {
			bandId != null -> TimelineFrame.Band(bandId)
			story -> TimelineFrame.Story
			else -> TimelineFrame.Everything
		}

		companion object {
			fun of(frame: TimelineFrame) = when (frame) {
				TimelineFrame.Everything -> SavedFrame()
				TimelineFrame.Story -> SavedFrame(story = true)
				is TimelineFrame.Band -> SavedFrame(bandId = frame.eventId)
			}
		}
	}

	@Serializable
	private data class SavedFilters(
		val query: String = "",
		val tags: List<String> = emptyList(),
		val lanes: List<String> = emptyList(),
	)

	private companion object {
		const val MENU_ID = "timeline-overview"
		const val NEW_TIMELINE_ITEM_ID = "timeline-overview-new-timeline"

		// Stragglers have no live def; they read as a manual timeline without a calendar.
		val ORPHANS_DEF = TimelineDef(id = "", name = "")

		const val VIEW_KEY = "timeline_overview_view"
		const val FRAME_KEY = "timeline_overview_frame"
		const val FILTERS_KEY = "timeline_overview_filters"
	}
}
