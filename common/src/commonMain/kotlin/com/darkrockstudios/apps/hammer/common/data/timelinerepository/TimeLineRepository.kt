package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineEntityRules
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.tagindex.cleanTags
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcherNow
import korlibs.io.async.asyncImmediately
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

	// Guards every read-modify-write of the timeline. Not reentrant: locked bodies never call a public mutator.
	private val mutex = Mutex()

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
			mutex.withLock { loadAndEmit() }
		}

		return this
	}

	suspend fun loadTimeline(): TimeLineContainer = datasource.loadTimeline(projectDef)

	private suspend fun loadAndEmit(): TimeLineContainer = loadTimeline().also { _timelineFlow.emit(it) }

	/** Loads from disk when [initialize] has not emitted yet, so callers never wait on it. */
	private suspend fun currentTimeline(): TimeLineContainer =
		timelineFlow.replayCache.firstOrNull() ?: loadAndEmit()

	/** The events as currently held, loading them first when nothing has been loaded yet. */
	suspend fun currentEvents(): List<TimeLineEvent> = mutex.withLock { currentTimeline().events }

	suspend fun createEvent(
		content: String,
		label: String?,
		id: Int? = null,
		order: Int? = null,
		tags: Set<String> = emptySet(),
	): TimeLineEvent = createEvent(
		TimelineEventDraft(content = content, label = label, id = id, order = order, tags = tags)
	)

	suspend fun createEvent(draft: TimelineEventDraft): TimeLineEvent {
		val eventId = draft.id ?: idAllocator.claimNextId()
		return mutex.withLock {
			val timeline = currentTimeline()

			val order = draft.order ?: timeline.events.nextOrder(normalizeTimeline(draft.timeline))
			val event = draft.toEvent(eventId, order).normalized()

			storeAndEmitTimeline(timeline.copy(events = timeline.events + event))

			if (draft.id != null) {
				markForSynchronization(event)
			}

			event
		}
	}

	private suspend fun storeAndEmitTimeline(timeLine: TimeLineContainer) {
		datasource.storeTimeline(timeLine, projectDef)
		_timelineFlow.emit(timeLine)
		_eventContentChangedFlow.emit(Unit)
	}

	suspend fun updateEvent(event: TimeLineEvent, markForSync: Boolean = true): Boolean = mutex.withLock {
		val cleaned = event.normalized()
		val timeline = currentTimeline()

		val events = timeline.events.toMutableList()
		val originalIndex = events.indexOfFirst { it.id == cleaned.id }

		var oldEvent: TimeLineEvent? = null
		if (originalIndex != -1) {
			oldEvent = events[originalIndex]
			events[originalIndex] = cleaned
		} else {
			events.add(cleaned)
		}

		val updatedTimeline = emitInOrder(
			timeline.copy(
				events = events
			)
		)

		storeAndEmitTimeline(updatedTimeline)

		if (markForSync) {
			markForSynchronization(oldEvent ?: cleaned)
		}

		true
	}

	/**
	 * Replaces every listed event that exists, storing once and marking the changed ones for sync.
	 * Unknown ids are ignored.
	 */
	suspend fun updateEvents(updates: Collection<TimeLineEvent>) {
		val byId = updates.associateBy { it.id }
		mutex.withLock {
			val originals = rewriteEvents { event ->
				byId[event.id]
					?.normalized()
					?.takeIf { it != event }
			}
			originals.forEach { markForSynchronization(it) }
		}
	}

	suspend fun deleteEvent(event: TimeLineEvent): Boolean = mutex.withLock {
		val timeline = currentTimeline()

		val events = timeline.events.toMutableList()
		val index = events.indexOfFirst { it.id == event.id }
		events.removeAt(index)
		storeAndEmitTimeline(timeline.copy(events = events))

		syncJournal.recordIdDeletion(event.id)

		true
	}

	/** Removes every listed event that exists in one store, recording each deletion for sync. */
	suspend fun deleteEvents(events: Collection<TimeLineEvent>) {
		val ids = events.mapTo(mutableSetOf()) { it.id }
		mutex.withLock {
			val timeline = currentTimeline()
			val (removed, kept) = timeline.events.partition { it.id in ids }
			if (removed.isEmpty()) return@withLock
			storeAndEmitTimeline(timeline.copy(events = kept))
			removed.forEach { syncJournal.recordIdDeletion(it.id) }
		}
	}

	suspend fun updateEventForSync(event: TimeLineEvent) = mutex.withLock {
		val timeline = currentTimeline()
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
		// Nothing to persist if the timeline was never loaded this session.
		timelineFlow.replayCache.firstOrNull()?.let { datasource.storeTimeline(it, projectDef) }
	}

	suspend fun getTimelineEvent(id: Int): TimeLineEvent? {
		return timelineFlow.first().events.firstOrNull { it.id == id }
	}

	/** The [kind] placement of [subjectId] on [timeline], given by id with main spelled out. */
	suspend fun findPlacement(kind: TimelineKind, subjectId: Int, timeline: String): TimeLineEvent? {
		require(kind.isPlacement) { "Not a placement kind: $kind" }
		return mutex.withLock {
			currentTimeline().events.firstOrNull { it.places(kind, subjectId) && it.timelineId == timeline }
		}
	}

	/** Every [kind] placement of [subjectId], on any timeline. */
	suspend fun placementsOf(kind: TimelineKind, subjectId: Int): List<TimeLineEvent> {
		require(kind.isPlacement) { "Not a placement kind: $kind" }
		return mutex.withLock { currentTimeline().events.filter { it.places(kind, subjectId) } }
	}

	private fun TimeLineEvent.places(kind: TimelineKind, subjectId: Int): Boolean =
		this.kind == kind && subject == subjectId

	/**
	 * Points every [subjectKind] placement of [oldId] and every reference to [oldId] at [newId].
	 * References carry no kind, so they follow whichever subject is renumbered. Returns the ids of the
	 * events it rewrote; marking them for sync is left to the caller.
	 */
	suspend fun remapReferencedId(oldId: Int, newId: Int, subjectKind: TimelineKind): Set<Int> {
		require(subjectKind.isPlacement) { "Not a placement kind: $subjectKind" }
		if (oldId == newId) return emptySet()
		return mutex.withLock {
			rewriteEvents { event ->
				val placed = event.kind == subjectKind && event.subject == oldId
				val referenced = oldId in event.references
				if (!placed && !referenced) {
					null
				} else {
					event.copy(
						subject = if (placed) newId else event.subject,
						references = if (referenced) event.references - oldId + newId else event.references,
					)
				}
			}.mapTo(mutableSetOf()) { it.id }
		}
	}

	/**
	 * For a deleted [subjectKind] subject: its placements become plain events titled from
	 * [TimeLineEvent.cachedTitle], or [titleFallback] when there is none, and every event drops
	 * [subjectId] from its references. Returns the ids of the events it rewrote.
	 */
	suspend fun detachSubject(
		subjectId: Int,
		subjectKind: TimelineKind,
		titleFallback: String?,
		markForSync: Boolean,
	): Set<Int> {
		require(subjectKind.isPlacement) { "Not a placement kind: $subjectKind" }
		return mutex.withLock {
			val originals = rewriteEvents { event ->
				val placed = event.kind == subjectKind && event.subject == subjectId
				val referenced = subjectId in event.references
				when {
					placed -> event.copy(
						kind = TimelineKind.EVENT,
						subject = null,
						// Capped so the server accepts the converted event.
						title = (event.cachedTitle ?: titleFallback)?.take(TimelineLimits.MAX_TITLE),
					)

					referenced -> event.copy(references = event.references - subjectId)
					else -> null
				}
			}
			if (markForSync) originals.forEach { markForSynchronization(it) }
			originals.mapTo(mutableSetOf()) { it.id }
		}
	}

	/**
	 * Replaces each event [rewrite] returns non-null for, then stores and emits once if any changed.
	 * Returns the original of every event it replaced. The caller holds [mutex].
	 */
	private suspend fun rewriteEvents(rewrite: (TimeLineEvent) -> TimeLineEvent?): List<TimeLineEvent> {
		val timeline = currentTimeline()
		val originals = mutableListOf<TimeLineEvent>()
		val events = timeline.events.map { event ->
			rewrite(event)?.also { originals += event } ?: event
		}
		if (originals.isNotEmpty()) storeAndEmitTimeline(timeline.copy(events = events))
		return originals
	}

	suspend fun reIdEvent(oldId: Int, newId: Int) = mutex.withLock {
		val timeline = currentTimeline()

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

	/**
	 * Moves [event] within its own timeline. [toIndex] is a position among that timeline's events
	 * sorted by order; only that timeline's events are renumbered and marked for sync.
	 */
	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean = mutex.withLock {
		val originalTimeline = currentTimeline()
		val stored = originalTimeline.events.firstOrNull { it.id == event.id }

		// Indices come from the order-sorted view the UI displays, which can differ
		// from the serialized file order, so move within that same sorted sequence.
		val events = originalTimeline.events
			.filter { it.timelineId == stored?.timelineId }
			.sortedBy { it.order }
			.toMutableList()
		val fromIndex = events.indexOfFirst { it.id == event.id }

		if (fromIndex <= -1) {
			Logger.e { "moveEvent from event not found!" }
			false
		} else if (toIndex >= events.size) {
			Logger.e { "moveEvent to invalid index: $toIndex" }
			false
		} else {
			val computedToIndex = if (after) {
				toIndex + 1
			} else {
				toIndex
			}

			val moved = if (computedToIndex < fromIndex) {
				events.add(computedToIndex, events.removeAt(fromIndex))
				true
			} else if (computedToIndex > fromIndex) {
				events.add(computedToIndex, events[fromIndex])
				events.removeAt(fromIndex)
				true
			} else {
				Logger.d { "Can't move" }
				false
			}

			if (moved) {
				val renumbered = mutableMapOf<Int, TimeLineEvent>()
				for (ii in 0..<events.size) {
					val curEvent = events[ii]
					if (curEvent.order != ii) {
						renumbered[curEvent.id] = curEvent.copy(order = ii)
						val originalEvent = originalTimeline.events.first { it.id == curEvent.id }
						markForSynchronization(originalEvent)
					}
				}

				val updatedTimeline = originalTimeline.copy(
					events = originalTimeline.events.map { renumbered[it.id] ?: it }.sortedBy { it.order }
				)

				storeAndEmitTimeline(updatedTimeline)
			}

			moved
		}
	}

	private suspend fun markForSynchronization(originalEvent: TimeLineEvent) {
		if (syncJournal.isServerSynchronized() && !syncJournal.isEntityDirty(originalEvent.id)) {
			syncJournal.markEntityAsDirty(originalEvent.id)
		}
	}

	fun validateTags(tags: Set<String>): TimeLineEventError {
		return if (tags.any { it.length > TimelineLimits.MAX_TAG }) {
			TimeLineEventError.TAG_TOO_LONG
		} else {
			TimeLineEventError.NONE
		}
	}

	/**
	 * The shared [TimelineEntityRules], which need nothing but [event] itself. Whether its subject,
	 * references and dates make sense for the project is [TimelineService]'s concern.
	 */
	fun validate(event: TimeLineEvent): TimeLineEventError =
		TimelineEntityRules.problem(event.toApiEntity())?.toEventError() ?: TimeLineEventError.NONE

	/**
	 * Sort the states by their order value, rather than the actual place in the serialized file.
	 */
	suspend fun correctEventOrder(timeline: TimeLineContainer? = null): TimeLineContainer = mutex.withLock {
		emitInOrder(timeline ?: currentTimeline())
	}

	private suspend fun emitInOrder(originalTimeline: TimeLineContainer): TimeLineContainer {
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
		this.scope.cancel("Project closed")
	}

	companion object {
		const val MAX_TAG_SIZE = TimelineLimits.MAX_TAG

		/** User write paths only; sync stores what the server sent. */
		fun normalizeLane(lane: String?): String? = lane?.trim()?.ifBlank { null }

		/** Main is stored as null, never spelled out. */
		fun normalizeTimeline(timeline: String?): String? = timeline?.takeUnless { it == TimelineDef.MAIN_ID }

		private fun TimeLineEvent.normalized(): TimeLineEvent =
			copy(tags = cleanTags(tags), lane = normalizeLane(lane), timeline = normalizeTimeline(timeline))

		/** One past the highest order on [timeline] (null for main), or 0 when it has no events. */
		internal fun List<TimeLineEvent>.nextOrder(timeline: String?): Int {
			val timelineId = timeline ?: TimelineDef.MAIN_ID
			return filter { it.timelineId == timelineId }.maxOfOrNull { it.order + 1 } ?: 0
		}
	}
}