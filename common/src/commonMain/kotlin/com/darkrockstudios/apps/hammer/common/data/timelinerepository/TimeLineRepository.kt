package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okio.Closeable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

abstract class TimeLineRepository(
	protected val projectDef: ProjectDef,
	protected val idRepository: IdRepository
) : Closeable, KoinComponent {

	protected val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	protected val scope = CoroutineScope(dispatcherDefault)

	protected val _timelineFlow = MutableSharedFlow<TimeLineContainer>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val timelineFlow: SharedFlow<TimeLineContainer> = _timelineFlow

	init {
		scope.launch {
			val timeline = loadTimeline()
			_timelineFlow.emit(timeline)
		}
	}

	abstract suspend fun loadTimeline(): TimeLineContainer
	abstract fun storeTimeline(timeLine: TimeLineContainer)
	abstract fun getTimelineFile(): HPath

	override fun close() {
		timelineFlow.replayCache.lastOrNull()?.let { timeLineContainer ->
			storeTimeline(timeLineContainer)
		}

		scope.cancel()
	}

	companion object {
		const val TIMELINE_FILENAME = "timeline.toml"
		const val TIMELINE_DIRECTORY = "timeline"
	}
}