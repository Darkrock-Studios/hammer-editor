package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Closeable
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

abstract class TimeLineRepository(
	protected val projectDef: ProjectDef,
	protected val idRepository: IdRepository
) : Closeable, ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	protected val projectSynchronizer: ClientProjectSynchronizer by projectInject()
	protected val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	protected val scope = CoroutineScope(dispatcherDefault)

	protected val _timelineFlow = MutableSharedFlow<TimeLineContainer>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		replay = 1
	)
	val timelineFlow: SharedFlow<TimeLineContainer> = _timelineFlow

	fun initialize(): TimeLineRepository {
		scope.launch {
			val timeline = loadTimeline()
			_timelineFlow.emit(timeline)
		}

		return this
	}

	abstract suspend fun loadTimeline(): TimeLineContainer
	abstract suspend fun createEvent(content: String, date: String?, id: Int? = null, order: Int? = null): TimeLineEvent
	abstract suspend fun updateEvent(event: TimeLineEvent, markForSync: Boolean = true): Boolean
	protected abstract fun storeTimeline(timeLine: TimeLineContainer)
	abstract suspend fun deleteEvent(event: TimeLineEvent): Boolean
	abstract fun getTimelineFile(): HPath

	fun storeTimeline() {
		storeTimeline(timelineFlow.replayCache.first())
	}

	suspend fun getTimelineEvent(id: Int): TimeLineEvent? {
		return timelineFlow.first().events.firstOrNull { it.id == id }
	}

	override fun close() {
		timelineFlow.replayCache.lastOrNull()?.let { timeLineContainer ->
			storeTimeline(timeLineContainer)
		}

		scope.cancel()
	}

	abstract suspend fun reIdEvent(oldId: Int, newId: Int)

	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean {
		val originalTimeline = timelineFlow.first()

		val originalEventOrder =
			originalTimeline.events.mapIndexed { index, originalEvent -> Pair(originalEvent.id, index) }.toMap()

		val events = originalTimeline.events.toMutableList()
		val fromIndex = events.indexOfFirst { it.id == event.id }

		return if (fromIndex <= -1) {
			Napier.e { "moveEvent from event not found!" }
			false
		} else if (toIndex >= events.size) {
			Napier.e { "moveEvent to invalid index: $toIndex" }
			false
		} else {
			// Good to go
			val computedToIndex = if (after) {
				toIndex + 1
			} else {
				toIndex
			}

			val moved = if (computedToIndex < fromIndex) {
				events.removeAt(fromIndex)
				events.add(computedToIndex, event)
				true
			} else if (computedToIndex > fromIndex) {
				events.add(computedToIndex, event)
				events.removeAt(fromIndex)
				true
			} else {
				Napier.d { "Can't move" }
				false
			}

			if (moved) {
				val updatedTimeline = originalTimeline.copy(
					events = events
				)

				storeTimeline(updatedTimeline)

				// Calculate events to be marked for update
				val newEventOrder =
					updatedTimeline.events.mapIndexed { index, updatedEvent -> Pair(updatedEvent.id, index) }
				val changedOrder = newEventOrder.mapNotNull { (id, index) ->
					if (originalEventOrder[id] != index) {
						id
					} else {
						null
					}
				}
				// Mark them for sync
				changedOrder.forEach { id ->
					val originalEvent = originalTimeline.events.first { it.id == id }
					val originalOrder = originalEventOrder[id] ?: -1
					markForSynchronization(originalEvent, originalOrder)
				}
			}

			moved
		}
	}

	protected suspend fun markForSynchronization(originalEvent: TimeLineEvent, originalOrder: Int) {
		if (projectSynchronizer.isServerSynchronized() && !projectSynchronizer.isEntityDirty(originalEvent.id)) {
			val hash = EntityHash.hashTimelineEvent(
				id = originalEvent.id,
				order = originalOrder,
				content = originalEvent.content,
				date = originalEvent.date
			)
			projectSynchronizer.markEntityAsDirty(originalEvent.id, hash)
		}
	}

	suspend fun correctEventOrder(timeline: TimeLineContainer? = null) {
		val originalTimeline = timeline ?: timelineFlow.first()

		val events = originalTimeline.events.toMutableList()
		val updatedEvents = events.sortedBy { it.order }

		val updatedTimeline = originalTimeline.copy(
			events = updatedEvents
		)

		storeTimeline(updatedTimeline)
	}

	/*
	private fun saveOnChange() {
		saveJob = scope.launch {
			timelineFlow.debounceUntilQuiescent(1000.milliseconds).collect { timeline ->
				storeTimeline()
			}
		}
	}
	*/

	companion object {
		const val TIMELINE_FILENAME = "timeline.toml"
		const val TIMELINE_DIRECTORY = "timeline"
	}
}