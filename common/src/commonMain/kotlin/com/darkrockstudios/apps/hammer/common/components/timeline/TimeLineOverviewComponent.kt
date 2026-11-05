package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineView
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.hasValidStart
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView as DrawingView
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

class TimeLineOverviewComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	addMenu: (menu: MenuDescriptor) -> Unit,
	removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), TimeLineOverview {

	private val timelineService: TimelineService by projectInject()
	private val sceneRepository: SceneRepository by projectInject()
	private val tagIndexService: TagIndexService by projectInject()
	private val entityCatalog: EntityCatalog by projectInject()

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
		)
	}

	override fun onCreate() {
		super.onCreate()

		watchTimeLine()
		watchTags()
	}

	private fun watchTimeLine() {
		scope.launch {
			// The scene tree is watched too, so a placed scene that is archived or unarchived redraws.
			val sceneTree = sceneRepository.sceneTreeUpdates.map { }.onStart { emit(Unit) }
			combine(timelineService.timelineFlow, sceneTree) { view, _ -> view }.collect { view ->
				val built = buildRows(view)
				withContext(dispatcherMain) {
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

	private suspend fun buildRows(view: TimelineView): Built {
		val events = view.events
		val glances = timelineService.glances(events)
		val lanes = timelineService.lanes()
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

	private fun watchTags() {
		scope.launch {
			tagIndexService.tagIndex.collect {
				val ranked = tagIndexService.getRankedTags(TaggedEntityType.TimelineEvent)
				withContext(dispatcherMain) {
					if (ranked != _rankedTags.value) _rankedTags.value = ranked
				}
			}
		}
	}

	override suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean =
		timelineService.moveEvent(event, toIndex, after).isSuccess

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
		const val VIEW_KEY = "timeline_overview_view"
		const val FRAME_KEY = "timeline_overview_frame"
		const val FILTERS_KEY = "timeline_overview_filters"
	}
}
