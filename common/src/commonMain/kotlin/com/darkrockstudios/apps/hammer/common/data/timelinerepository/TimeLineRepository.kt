package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
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
import io.github.aakira.napier.Napier
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

			val event = TimeLineEvent(
				id = eventId,
				order = draft.order ?: timeline.events.size,
				kind = draft.kind,
				subject = draft.subject,
				title = draft.title,
				label = draft.label,
				content = draft.content,
				tags = cleanTags(draft.tags),
				references = draft.references,
				lane = normalizeLane(draft.lane),
				milestone = draft.milestone,
				start = draft.start,
				end = draft.end,
				cachedTitle = draft.cachedTitle,
			)

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
		val cleaned = event.copy(tags = cleanTags(event.tags), lane = normalizeLane(event.lane))
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
	 * Replaces every listed event that exists, storing once. Unknown ids are ignored. Returns the ids
	 * it changed.
	 */
	suspend fun updateEvents(updates: Collection<TimeLineEvent>, markForSync: Boolean = true): Set<Int> {
		val byId = updates.associateBy { it.id }
		return mutex.withLock {
			val originals = rewriteEvents { event ->
				byId[event.id]
					?.let { it.copy(tags = cleanTags(it.tags), lane = normalizeLane(it.lane)) }
					?.takeIf { it != event }
			}
			if (markForSync) originals.forEach { markForSynchronization(it) }
			originals.mapTo(mutableSetOf()) { it.id }
		}
	}

	/**
	 * Sets each listed event's order to its index in [orderedIds]; the rest follow in their current
	 * order. Changed events are marked for sync.
	 */
	suspend fun renumber(orderedIds: List<Int>) = mutex.withLock {
		val timeline = currentTimeline()
		val rank = HashMap<Int, Int>()
		orderedIds.forEach { id -> rank.getOrPut(id) { rank.size } }

		val (listed, rest) = timeline.events.partition { it.id in rank }
		val sequence = listed.sortedBy { rank.getValue(it.id) } + rest.sortedBy { it.order }

		val changed = mutableListOf<TimeLineEvent>()
		val events = sequence.mapIndexed { index, event ->
			if (event.order == index) {
				event
			} else {
				changed += event
				event.copy(order = index)
			}
		}
		if (changed.isNotEmpty()) {
			storeAndEmitTimeline(timeline.copy(events = events))
			changed.forEach { markForSynchronization(it) }
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

	suspend fun findPlacement(subjectId: Int): TimeLineEvent? = mutex.withLock {
		currentTimeline().events.firstOrNull { it.kind.isPlacement && it.subject == subjectId }
	}

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

	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): Boolean = mutex.withLock {
		val originalTimeline = currentTimeline()

		// Indices come from the order-sorted view the UI displays, which can differ
		// from the serialized file order, so move within that same sorted sequence.
		val events = originalTimeline.events.sortedBy { it.order }.toMutableList()
		val fromIndex = events.indexOfFirst { it.id == event.id }

		if (fromIndex <= -1) {
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
						markForSynchronization(originalEvent)
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
	 * Shape checks that need nothing but [event] itself. Whether its subject, references and dates
	 * make sense for the project is [TimelineService]'s concern.
	 */
	fun validate(event: TimeLineEvent): TimeLineEventError {
		val subject = event.subject
		val placement = event.kind.isPlacement
		return when {
			placement && subject == null -> TimeLineEventError.SUBJECT_REQUIRED
			!placement && subject != null -> TimeLineEventError.SUBJECT_NOT_ALLOWED
			placement && event.references.isNotEmpty() -> TimeLineEventError.REFERENCES_NOT_ALLOWED
			event.id in event.references || subject == event.id -> TimeLineEventError.SELF_REFERENCE
			!validateTags(event.tags).isSuccess -> TimeLineEventError.TAG_TOO_LONG
			event.lane != null && !isValidLane(event.lane) -> TimeLineEventError.LANE_INVALID
			longerThan(event.title, TimelineLimits.MAX_TITLE) -> TimeLineEventError.TITLE_TOO_LONG
			longerThan(event.cachedTitle, TimelineLimits.MAX_TITLE) -> TimeLineEventError.TITLE_TOO_LONG
			longerThan(event.label, TimelineLimits.MAX_LABEL) -> TimeLineEventError.LABEL_TOO_LONG
			!isWellFormed(event.start) || !isWellFormed(event.end) -> TimeLineEventError.DATE_INVALID
			else -> TimeLineEventError.NONE
		}
	}

	private fun isValidLane(lane: String): Boolean = lane.isNotBlank() && lane.length <= TimelineLimits.MAX_LANE

	private fun longerThan(text: String?, max: Int): Boolean = text != null && text.length > max

	private fun isWellFormed(date: StoryDate?): Boolean =
		date == null || date.rungs.size <= TimelineLimits.MAX_RUNGS &&
			date.rungs.keys.all { it.isNotBlank() && it.length <= TimelineLimits.MAX_UNIT_NAME }

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
	}
}