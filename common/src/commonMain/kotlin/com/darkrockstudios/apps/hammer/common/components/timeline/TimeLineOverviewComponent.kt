package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineView
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.toCompactText
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimeLineOverviewComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	addMenu: (menu: MenuDescriptor) -> Unit,
	removeMenu: (id: String) -> Unit
) : ProjectComponentBase(projectDef, componentContext), TimeLineOverview {

	private val timelineService: TimelineService by projectInject()
	private val sceneRepository: SceneRepository by projectInject()
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val tagIndexService: TagIndexService by projectInject()

	private val _state = MutableValue(TimeLineOverview.State())
	override val state: Value<TimeLineOverview.State> = _state

	private val _rankedTags = MutableValue<List<TagCount>>(emptyList())
	override val rankedTags: Value<List<TagCount>> = _rankedTags

	override fun onCreate() {
		super.onCreate()

		watchTimeLine()
		watchTags()
	}

	private fun watchTimeLine() {
		scope.launch {
			timelineService.timelineFlow.collect { view ->
				val built = buildRows(view)
				withContext(dispatcherMain) {
					_state.getAndUpdate { current ->
						val laneNames = built.lanes.mapTo(HashSet()) { it.lane }
						current.copy(
							rows = built.rows,
							mode = view.def.mode,
							lanes = built.lanes,
							laneFilter = current.laneFilter.filterTo(LinkedHashSet()) { it in laneNames },
						)
					}
				}
			}
		}
	}

	private data class Built(val rows: List<TimelineRow>, val lanes: List<LaneCount>)

	private suspend fun buildRows(view: TimelineView): Built {
		val events = view.events
		val titles = timelineService.displayTitles(events)
		val lanes = timelineService.lanes()
		val laneIndex = lanes.withIndex().associate { (index, lane) -> lane to index }
		val glances = withContext(dispatcherIo) { glanceSubjects(events, sceneRepository, encyclopediaRepository) }

		val rows = events.map { event ->
			val glance = glances[event.id]
			TimelineRow(
				event = event,
				title = titles[event.id].orEmpty(),
				subtitle = if (event.kind == TimelineKind.SCENE) glance?.chapterPath else null,
				whenText = whenText(event, view.def.mode),
				laneIndex = event.lane?.let { laneIndex[it] },
				subjectMissing = glance?.missing == true,
			)
		}
		val counts = events.mapNotNull { it.lane }.groupingBy { it }.eachCount()
		return Built(rows, lanes.map { LaneCount(it, counts[it] ?: 0) })
	}

	private fun whenText(event: TimeLineEvent, mode: TimelineMode): String {
		val label = event.label?.trim().orEmpty()
		if (label.isNotEmpty() || mode == TimelineMode.MANUAL) return label
		return event.start?.toCompactText().orEmpty()
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
}
