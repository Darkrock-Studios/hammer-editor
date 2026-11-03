package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
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
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView as DrawingView
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

	private val _state = MutableValue(
		TimeLineOverview.State(
			view = stateKeeper.consume(VIEW_KEY, String.serializer())
				?.let { saved -> DrawingView.entries.firstOrNull { it.name == saved } }
				?: DrawingView.SEQUENCE,
			frame = stateKeeper.consume(FRAME_KEY, SavedFrame.serializer())?.toFrame() ?: TimelineFrame.Everything,
		)
	)
	override val state: Value<TimeLineOverview.State> = _state

	private val _rankedTags = MutableValue<List<TagCount>>(emptyList())
	override val rankedTags: Value<List<TagCount>> = _rankedTags

	/** Read and written on the main thread only. */
	private val previews = HashMap<Int, CachedPreview>()

	/** A preview stays good while its event's row is unchanged. */
	private class CachedPreview(val row: TimelineRow, val preview: EventPreview)

	private val frameBackCallback = BackCallback(isEnabled = false) {
		setFrame(TimelineFrame.Everything)
	}

	init {
		stateKeeper.register(VIEW_KEY, String.serializer()) { _state.value.view.name }
		stateKeeper.register(FRAME_KEY, SavedFrame.serializer()) { SavedFrame.of(_state.value.frame) }
		backHandler.register(frameBackCallback)
		state.subscribe(lifecycle) {
			frameBackCallback.isEnabled = it.frame != TimelineFrame.Everything
		}
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
					previews.entries.removeAll { (id, cached) -> rowsById[id] != cached.row }
					_state.getAndUpdate { current ->
						val laneNames = built.lanes.mapTo(HashSet()) { it.lane }
						// Only drawn events (dated, and not an archived scene) can be selected or framed.
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
							laneFilter = current.laneFilter.filterTo(LinkedHashSet()) { it in laneNames },
							selectedId = current.selectedId?.takeIf { it in drawn },
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

	override fun setLaneFilter(lanes: Set<String>) {
		_state.getAndUpdate { it.copy(laneFilter = lanes) }
	}

	override fun toggleLane(lane: String) {
		_state.getAndUpdate {
			val filter = if (lane in it.laneFilter) it.laneFilter - lane else it.laneFilter + lane
			it.copy(laneFilter = filter)
		}
	}

	override fun setView(view: DrawingView) {
		_state.getAndUpdate { it.copy(view = view) }
	}

	override fun setScrolledDown(scrolledDown: Boolean) {
		_state.getAndUpdate { it.copy(scrolledDown = scrolledDown) }
	}

	override fun select(eventId: Int?) {
		_state.getAndUpdate { it.copy(selectedId = eventId) }
	}

	override fun canFrame(eventId: Int): Boolean {
		val current = _state.value
		if (current.mode != TimelineMode.DATED || current.frame == TimelineFrame.Band(eventId)) return false
		val row = current.rows?.firstOrNull { it.event.id == eventId }?.takeIf { it.isDrawn } ?: return false
		val start = row.start ?: return false
		val calendar = row.calendar ?: CalendarDef()
		val end = row.end?.let { rangeEnd(calendar, it) } ?: return precisionExtent(calendar, start) > 1
		val startAt = StoryCalendar.position(calendar, start) ?: return false
		return end > startAt
	}

	override fun setFrame(frame: TimelineFrame) {
		_state.getAndUpdate {
			if (frame == TimelineFrame.Story && !it.storyFrameAvailable) it else it.copy(frame = frame)
		}
	}

	override suspend fun preview(eventId: Int): EventPreview? {
		var cached: EventPreview? = null
		val row = withContext(dispatcherMain) {
			_state.value.rows?.firstOrNull { it.event.id == eventId }?.also { row ->
				cached = previews[eventId]?.takeIf { it.row == row }?.preview
			}
		} ?: return null
		cached?.let { return it }

		val preview = resolvePreview(row)
		withContext(dispatcherMain) {
			if (_state.value.rows?.firstOrNull { it.event.id == eventId } == row) {
				previews[eventId] = CachedPreview(row, preview)
			}
		}
		return preview
	}

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

	private companion object {
		const val VIEW_KEY = "timeline_overview_view"
		const val FRAME_KEY = "timeline_overview_frame"
	}
}
