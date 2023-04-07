package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimeLineOverviewComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	addMenu: (menu: MenuDescriptor) -> Unit,
	removeMenu: (id: String) -> Unit
) : ProjectComponentBase(projectDef, componentContext), TimeLineOverview {

	private val mainDispatcher by injectMainDispatcher()
	private val timeLineRepository: TimeLineRepository by projectInject()

	private val _state = MutableValue(TimeLineOverview.State(timeLine = null))
	override val state: Value<TimeLineOverview.State> = _state

	private var saveJob: Job? = null

	override fun onCreate() {
		super.onCreate()

		watchTimeLine()
	}

	override fun onDestroy() {
		super.onDestroy()

		saveJob?.cancel()
		// Save final
		timeLineRepository.storeTimeline()
	}

	private fun watchTimeLine() {
		scope.launch {
			timeLineRepository.timelineFlow.collect { timeLine ->
				withContext(mainDispatcher) {
					if (timeLine != state.value.timeLine) {
						_state.reduce {
							it.copy(timeLine = timeLine)
						}
					}
				}
			}
		}
	}

	override suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean {
		return timeLineRepository.moveEvent(event, toIndex, after)
	}
}