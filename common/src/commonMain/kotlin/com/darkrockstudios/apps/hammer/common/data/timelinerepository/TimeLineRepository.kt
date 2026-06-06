package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.tagindex.cleanTags
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcherNow
import io.github.aakira.napier.Napier
import korlibs.io.async.asyncImmediately
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext

class TimeLineRepository(
	private val projectDef: ProjectDef,
	private val idAllocator: IdAllocator,
	private val datasource: TimeLineDatasource,
) : ProjectScoped, ScopeCallback {
	override val projectScope = ProjectDefScope(projectDef)

	private val syncJournal: SyncJournal by projectInject()
	private val dispatcherDefault: CoroutineContext by injectDefaultDispatcher()

	// Get this one eagerly, it's used during Koin teardown when we can't get it from the scope
	private val dispatcherIo: CoroutineContext = injectIoDispatcherNow()
	private val scope = CoroutineScope(dispatcherDefault)

	private val _timelineFlow = MutableSharedFlow<TimeLineContainer>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		replay = 1
	)
	val timelineFlow: SharedFlow<TimeLineContainer> = _timelineFlow

	private val _eventContentChangedFlow = MutableSharedFlow<Unit>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val eventContentChangedFlow: SharedFlow<Unit> = _eventContentChangedFlow

	fun initialize(): TimeLineRepository {
		projectScope.scope.registerCallback(this)

		scope.launch {
			val timeline = loadTimeline()
			_timelineFlow.emit(timeline)
		}

		return this
	}

	suspend fun loadTimeline(): TimeLineContainer = datasource.loadTimeline(projectDef)

	suspend fun createEvent(
		content: String,
		date: String?,
		id: Int? = null,
		order: Int? = null,
		tags: Set<String> = emptySet(),
	): TimeLineEvent {
		val eventId = id ?: idAllocator.claimNextId()
		val timeline = timelineFlow.first()

		val event = TimeLineEvent(
			id = eventId,
			order = order ?: timeline.events.size,
			content = content,
			date = date,
			tags = cleanTags(tags),
		)

		val newTimeline = timeline.copy(
			events = timeline.events.toMutableList() + event
		)

		storeAndEmitTimeline(newTimeline)

		if (id != null) {
			val index = newTimeline.events.indexOf(event)
			markForSynchronization(event, index)
		}

		return event
	}

	private suspend fun storeAndEmitTimeline(timeLine: TimeLineContainer) {
		datasource.storeTimeline(timeLine, projectDef)
		_timelineFlow.emit(timeLine)
		_eventContentChangedFlow.emit(Unit)
	}

	suspend fun updateEvent(event: TimeLineEvent, markForSync: Boolean = true): Boolean {
		val cleaned = event.copy(tags = cleanTags(event.tags))
		val timeline = timelineFlow.first()

		val events = timeline.events.toMutableList()
		val originalIndex = events.indexOfFirst { it.id == cleaned.id }

		var oldEvent: TimeLineEvent? = null
		if (originalIndex != -1) {
			oldEvent = events[originalIndex]
			events[originalIndex] = cleaned
		} else {
			events.add(cleaned)
		}

		val updatedTimeline = correctEventOrder(
			timeline.copy(
				events = events
			)
		)

		storeAndEmitTimeline(updatedTimeline)

		if (markForSync) {
			markForSynchronization(oldEvent ?: cleaned, originalIndex)
		}

		return true
	}

	suspend fun deleteEvent(event: TimeLineEvent): Boolean {
		val timeline = timelineFlow.first()

		val events = timeline.events.toMutableList()
		val index = events.indexOfFirst { it.id == event.id }
		events.removeAt(index)
		storeAndEmitTimeline(timeline.copy(events = events))

		syncJournal.recordIdDeletion(event.id)

		return true
	}

	suspend fun updateEventForSync(event: TimeLineEvent) {
		val timeline = timelineFlow.replayCache.first()
		val events = timeline.events.toMutableList()
		val originalIndex = events.indexOfFirst { it.id == event.id }

		if (originalIndex != -1) {
			events[originalIndex] = event
		} else {
			events.add(event)
		}

		val updatedTimeline = timeline.copy(
			events = events
		)

		_timelineFlow.emit(updatedTimeline)
		_eventContentChangedFlow.emit(Unit)
	}

	suspend fun storeTimeline() {
		datasource.storeTimeline(timelineFlow.replayCache.first(), projectDef)
	}

	suspend fun getTimelineEvent(id: Int): TimeLineEvent? {
		return timelineFlow.first().events.firstOrNull { it.id == id }
	}

	suspend fun reIdEvent(oldId: Int, newId: Int) {
		val timeline = timelineFlow.first()

		val events = timeline.events.toMutableList()
		val index = events.indexOfFirst { it.id == oldId }
		val oldEvent = events[index]

		val newEvent = oldEvent.copy(
			id = newId
		)

		events[index] = newEvent

		val updatedTimeline = timeline.copy(
			events = events
		)

		storeAndEmitTimeline(updatedTimeline)
	}

	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean {
		val originalTimeline = timelineFlow.first()

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
				for (ii in 0..<events.size) {
					val curEvent = events[ii]
					if (curEvent.order != ii) {
						// Set the correct new order for this event
						events[ii] = curEvent.copy(order = ii)

						// Mark for synchronization
						val originalEvent = originalTimeline.events.first { it.id == curEvent.id }
						markForSynchronization(originalEvent, originalEvent.order)
					}
				}

				val updatedTimeline = originalTimeline.copy(
					events = events
				)

				storeAndEmitTimeline(updatedTimeline)
			}

			moved
		}
	}

	private suspend fun markForSynchronization(originalEvent: TimeLineEvent, originalOrder: Int) {
		if (syncJournal.isServerSynchronized() && !syncJournal.isEntityDirty(
				originalEvent.id
			)
		) {
			val hash = EntityHasher.hashTimelineEvent(
				id = originalEvent.id,
				order = originalOrder,
				content = originalEvent.content,
				date = originalEvent.date,
				tags = originalEvent.tags,
			)
			syncJournal.markEntityAsDirty(originalEvent.id, hash)
		}
	}

	fun validateTags(tags: Set<String>): TimeLineEventError {
		return if (tags.any { it.length > MAX_TAG_SIZE }) {
			TimeLineEventError.TAG_TOO_LONG
		} else {
			TimeLineEventError.NONE
		}
	}

	/**
	 * Sort the states by their order value, rather than the actual place in the serialized file.
	 */
	suspend fun correctEventOrder(timeline: TimeLineContainer? = null): TimeLineContainer {
		val originalTimeline = timeline ?: timelineFlow.first()

		val events = originalTimeline.events
		val updatedEvents = events.sortedBy { it.order }

		val updatedTimeline = originalTimeline.copy(
			events = updatedEvents
		)

		_timelineFlow.emit(updatedTimeline)

		return updatedTimeline
	}

	override fun onScopeClose(scope: Scope) {
		_timelineFlow.replayCache.firstOrNull()?.let { timeLineContainer ->
			// This is whack. It's here to fix hanging tests,
			// it seems very picky about being the same context
			runBlocking {
				asyncImmediately(dispatcherIo) {
					datasource.storeTimeline(timeLineContainer, projectDef)
				}.await()
			}
		}
	}

	companion object {
		const val MAX_TAG_SIZE = 32
	}
}