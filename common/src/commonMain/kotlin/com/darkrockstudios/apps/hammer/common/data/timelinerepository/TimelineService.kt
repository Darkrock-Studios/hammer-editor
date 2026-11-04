package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDateProblem
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.base.timeline.isValidTimelineId
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryLoadError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.search.MarkdownProjector
import com.darkrockstudios.apps.hammer.common.data.search.markdownTitleLine
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository.Companion.nextOrder
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository.Companion.normalizeLane
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository.Companion.normalizeTimeline
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

/** Where an entry of [type] sorts in a cast or reference list: people, then places, then the rest. */
fun timelineCastRank(type: EntryType): Int = when (type) {
	EntryType.PERSON -> 0
	EntryType.PLACE -> 1
	else -> 2
}

/** The main timeline's definition, or the default before one is stored. */
fun ProjectData.mainTimeline(): TimelineDef =
	timelines.firstOrNull { it.id == TimelineDef.MAIN_ID } ?: TimelineDef.DEFAULT

/**
 * Main first, stored or [TimelineDef.DEFAULT], then the other definitions in list order. A deleted
 * timeline's id is left out; main is never deleted.
 */
fun ProjectData.liveTimelines(): List<TimelineDef> =
	listOf(mainTimeline()) + timelines
		.filter { it.id != TimelineDef.MAIN_ID && it.id !in deletedTimelines }
		.distinctBy { it.id }

/** The live definition with [id], or null when there is none. */
fun ProjectData.timelineDef(id: String): TimelineDef? = liveTimelines().firstOrNull { it.id == id }

/** Whether [event] has a start, and it is a valid date on [calendar]. */
fun hasValidStart(event: TimeLineEvent, calendar: CalendarDef): Boolean =
	event.start?.let { StoryCalendar.validate(calendar, it) == null } == true

/**
 * Up to [chars] of [markdown] as plain prose with whitespace collapsed; blank for blank markdown. A
 * project image reads as the name [imageName] gives it.
 */
internal fun markdownExcerpt(markdown: String, chars: Int, imageName: (imageId: Int) -> String? = { null }): String {
	if (markdown.isBlank()) return ""
	val projector = MarkdownProjector(markdown.length, imageName)
	projector.project(markdown)
	return projector.collapsedPreview(chars)
}

/** A timeline's settings and its events in display order. */
data class TimelineView(val def: TimelineDef, val events: List<TimeLineEvent>)

/** A live timeline and how many events it holds. */
data class TimelineSummary(val def: TimelineDef, val eventCount: Int)

/** Placement [eventId] of the same subject on another live timeline. */
data class TimelinePlacement(val def: TimelineDef, val eventId: Int)

/** A placement of a scene or entry with the live timeline it is on, null for a straggler. */
data class SubjectPlacement(val event: TimeLineEvent, val def: TimelineDef?)

/**
 * An event with the live timeline it is on, null for a straggler, and for a placement the other
 * live timelines its subject is placed on, in timeline order.
 */
data class TimelineEventView(
	val event: TimeLineEvent,
	val def: TimelineDef?,
	val alsoOn: List<TimelinePlacement> = emptyList(),
)

sealed interface TimelineResult {
	val isSuccess: Boolean get() = this is Ok

	data class Ok(val event: TimeLineEvent?) : TimelineResult
	data class Invalid(val error: TimeLineEventError) : TimelineResult
}

/** The outcome of creating, renaming or deleting a whole timeline. */
sealed interface TimelineDefResult {
	val isSuccess: Boolean get() = this is Ok

	/** [def] as written, or as it was before a deletion; [deletedEvents] counts what a deletion removed. */
	data class Ok(val def: TimelineDef, val deletedEvents: Int = 0) : TimelineDefResult
	data class Invalid(val error: TimeLineEventError) : TimelineDefResult
}

/** What a placement points at, as the timeline shows it. */
sealed class SubjectSummary {
	data class Scene(
		val scene: SceneItem,
		/** The groups above the scene, outermost first; empty for a top-level or archived scene. */
		val chapterPath: String,
		val excerpt: String,
		/** The scene's confirmed references: people, then places, then the rest. */
		val cast: List<EntryDef>,
	) : SubjectSummary()

	data class Entry(val entry: EntryDef, val primaryImageId: Int?, val excerpt: String) : SubjectSummary()

	/** A placed scene that has since been archived; unarchiving it brings the placement back. */
	data class Archived(val scene: SceneItem) : SubjectSummary()

	data object Missing : SubjectSummary()
}

/** A placement's subject as resolved, with the title that follows from it. */
data class PlacedSubject(val summary: SubjectSummary, val displayTitle: String)

/** What a list row shows of an event without reading any text. */
data class EventGlance(
	/** As [TimelineService.displayTitle]. */
	val title: String,
	/** The groups above a placed scene in the tree, outermost first; null when there are none. */
	val chapterPath: String? = null,
	/** A placement whose scene or entry is gone. */
	val missing: Boolean = false,
	/** A placed scene that is in the archive. */
	val archived: Boolean = false,
)

/**
 * The timeline's API for components and operations. Every rule that needs more than the event itself
 * lives here: subjects exist and have the right type, one placement per subject on each timeline,
 * references exist, and dates are valid for the calendar of the event's own timeline and required
 * when it is DATED. Settings are read and written through
 * [ProjectDataRepository], never `settings.toml` directly.
 */
class TimelineService(
	private val timeLineRepository: TimeLineRepository,
	private val projectDataRepository: ProjectDataRepository,
	private val sceneRepository: SceneRepository,
	private val sceneMetadataRepository: SceneMetadataRepository,
	private val encyclopediaRepository: EncyclopediaRepository,
) : KoinComponent {

	private val dispatcherIo by injectIoDispatcher()

	// Validation and the write it guards happen under one lock, so two callers cannot both pass a
	// check such as one placement per subject. Not reentrant: locked bodies call only private helpers.
	private val writeLock = Mutex()

	/** Draws a candidate id for a new timeline; replaced in tests to force collisions. */
	internal var nextTimelineId: () -> String = { TimelineDef.randomId() }

	// Loads the timeline when nothing has yet, so the repository's flow has a value to replay.
	private val storedTimeline: Flow<TimeLineContainer> = flow {
		timeLineRepository.currentEvents()
		emitAll(timeLineRepository.timelineFlow)
	}

	private val projectData: Flow<ProjectData> = flow {
		projectDataRepository.load()
		emitAll(projectDataRepository.state.filterNotNull())
	}.map { it.data }

	/** The live timelines, main first. */
	val timelines: Flow<List<TimelineDef>> = projectData.map { it.liveTimelines() }.distinctUntilChanged()

	/** Timeline [id] and its events in display order; emits nothing while [id] names no live timeline. */
	fun timelineFlow(id: String): Flow<TimelineView> = combine(storedTimeline, projectData) { timeline, data ->
		data.timelineDef(id)?.let { def ->
			TimelineView(def, displayOrder(timeline.events.filter { it.timelineId == id }, def.mode, def.calendar))
		}
	}.filterNotNull().distinctUntilChanged()

	/** Events whose timeline is not live, such as one created offline in a timeline deleted since, by order. */
	val orphansFlow: Flow<List<TimeLineEvent>> = combine(storedTimeline, projectData) { timeline, data ->
		val live = data.liveTimelines().mapTo(mutableSetOf()) { it.id }
		timeline.events.filter { it.timelineId !in live }.sortedBy { it.order }
	}.distinctUntilChanged()

	/** The live timelines, main first, each with how many events it holds. */
	val summaries: Flow<List<TimelineSummary>> = combine(storedTimeline, projectData) { timeline, data ->
		val counts = timeline.events.groupingBy { it.timelineId }.eachCount()
		data.liveTimelines().map { TimelineSummary(it, counts[it.id] ?: 0) }
	}.distinctUntilChanged()

	/** Event [eventId] as [TimelineEventView], or null while there is no such event. */
	fun eventFlow(eventId: Int): Flow<TimelineEventView?> = combine(storedTimeline, projectData) { timeline, data ->
		timeline.events.firstOrNull { it.id == eventId }?.let { event ->
			val live = data.liveTimelines()
			val subject = event.subject
			val alsoOn = if (event.kind.isPlacement && subject != null) {
				val others = timeline.events.filter {
					it.id != event.id && it.kind == event.kind && it.subject == subject && it.timelineId != event.timelineId
				}
				live.mapNotNull { def -> others.firstOrNull { it.timelineId == def.id }?.let { TimelinePlacement(def, it.id) } }
			} else {
				emptyList()
			}
			TimelineEventView(event, live.firstOrNull { it.id == event.timelineId }, alsoOn)
		}
	}.distinctUntilChanged()

	/** Every [kind] placement of [subjectId]: on live timelines in timeline order, then stragglers. */
	fun placementsFlow(kind: TimelineKind, subjectId: Int): Flow<List<SubjectPlacement>> {
		require(kind.isPlacement) { "Not a placement kind: $kind" }
		return combine(storedTimeline, projectData) { timeline, data ->
			val placements = timeline.events.filter { it.kind == kind && it.subject == subjectId }.sortedBy { it.order }
			val live = data.liveTimelines()
			val liveIds = live.mapTo(mutableSetOf()) { it.id }
			live.flatMap { def -> placements.filter { it.timelineId == def.id }.map { SubjectPlacement(it, def) } } +
				placements.filter { it.timelineId !in liveIds }.map { SubjectPlacement(it, null) }
		}.distinctUntilChanged()
	}

	/** Event [eventId], on any timeline, or null when there is none. */
	suspend fun event(eventId: Int): TimeLineEvent? = findEvent(eventId)

	/** Live timeline [timeline], or null when there is none. Main is always there. */
	suspend fun definition(timeline: String = TimelineDef.MAIN_ID): TimelineDef? =
		projectDataRepository.load().data.timelineDef(timeline)

	/**
	 * Writes the result of [transform] over live timeline [timeline]; its id cannot change. Rejects an
	 * invalid calendar, a DATED def without one, a bad lane, a lane order that takes the timeline past
	 * [TimelineLimits.MAX_LANES], and a new name that is blank, too long or already taken. A stored name
	 * is not checked unless it changes, so a def that arrived with one the rules refuse can still be edited.
	 */
	suspend fun updateDefinition(
		timeline: String = TimelineDef.MAIN_ID,
		transform: (TimelineDef) -> TimelineDef,
	): TimelineResult {
		val events = timeLineRepository.currentEvents()
		var error = TimeLineEventError.NONE
		projectDataRepository.updateData { data ->
			val current = data.timelineDef(timeline)
			if (current == null) {
				error = TimeLineEventError.TIMELINE_NOT_FOUND
				return@updateData data
			}
			val next = revised(current, transform(current))
			val eventLanes = events.filter { it.timelineId == current.id }.mapNotNullTo(HashSet()) { it.lane }
			error = revisionProblem(data, current, next).orElse {
				laneLimitProblem(eventLanes + current.laneOrder, eventLanes + next.laneOrder)
			}
			if (!error.isSuccess || next == current) data else data.withTimeline(next)
		}
		return result(error)
	}

	// region Timelines

	/**
	 * A new timeline named [name], trimmed, which no other live timeline has in any case. Its id is
	 * fresh: never a live one, nor one deleted before.
	 */
	suspend fun createTimeline(
		name: String,
		mode: TimelineMode = TimelineMode.MANUAL,
		calendar: CalendarDef? = null,
	): TimelineDefResult = writeLock.withLock {
		var outcome: TimelineDefResult = TimelineDefResult.Invalid(TimeLineEventError.TIMELINE_NAME_INVALID)
		projectDataRepository.updateData { data ->
			val taken = data.timelines.mapTo(mutableSetOf()) { it.id } + data.deletedTimelines + TimelineDef.MAIN_ID
			var id = nextTimelineId()
			while (id in taken) id = nextTimelineId()

			val def = TimelineDef(id = id, name = name.trim(), mode = mode, calendar = calendar)
			val error = (if (isValidTimelineId(def.id)) TimeLineEventError.NONE else TimeLineEventError.TIMELINE_NOT_FOUND)
				.orElse { nameProblem(data, def.name, def.id) }
				.orElse { definitionProblem(def) }
			if (!error.isSuccess) {
				outcome = TimelineDefResult.Invalid(error)
				data
			} else {
				outcome = TimelineDefResult.Ok(def)
				data.withTimeline(def)
			}
		}
		outcome
	}

	/** Renames live timeline [id] under the rules of [createTimeline]. */
	suspend fun renameTimeline(id: String, name: String): TimelineDefResult {
		val result = updateDefinition(id) { it.copy(name = name) }
		return when (result) {
			is TimelineResult.Invalid -> TimelineDefResult.Invalid(result.error)
			is TimelineResult.Ok -> definition(id)?.let { TimelineDefResult.Ok(it) }
				?: TimelineDefResult.Invalid(TimeLineEventError.TIMELINE_NOT_FOUND)
		}
	}

	/**
	 * Deletes timeline [id] and every event on it; main cannot be deleted. The events go first, each
	 * recorded for sync, so a failure between the steps leaves an empty timeline rather than stragglers.
	 * The id is then kept as deleted so no merge brings the definition back.
	 */
	suspend fun deleteTimeline(id: String): TimelineDefResult = writeLock.withLock {
		if (id == TimelineDef.MAIN_ID) return invalidDef(TimeLineEventError.MAIN_NOT_DELETABLE)
		val def = projectDataRepository.load().data.timelineDef(id)
			?: return invalidDef(TimeLineEventError.TIMELINE_NOT_FOUND)

		val events = timeLineRepository.currentEvents().filter { it.timelineId == id }
		if (events.isNotEmpty()) timeLineRepository.deleteEvents(events)
		projectDataRepository.updateData { data ->
			data.copy(
				timelines = data.timelines.filterNot { it.id == id },
				deletedTimelines = data.deletedTimelines + id,
			)
		}
		TimelineDefResult.Ok(def, deletedEvents = events.size)
	}

	/** Deletes every event whose timeline is not live, returning how many. */
	suspend fun deleteOrphans(): Int = writeLock.withLock {
		val orphans = orphansOf(timeLineRepository.currentEvents())
		if (orphans.isNotEmpty()) timeLineRepository.deleteEvents(orphans)
		orphans.size
	}

	/**
	 * Moves event [eventId] to live timeline [target], appending it there and keeping everything but
	 * its dates and order. A placement whose subject is already on [target] is refused.
	 *
	 * On a DATED target the start, or [start] when given, must be valid on its calendar; an end that no
	 * longer fits is dropped. Without a valid start the move is refused unless [allowUndated], which lands
	 * the event undated. On a MANUAL target the dates are kept where its calendar holds them, and an
	 * event without a label gets its dates on the old calendar, written in [words], as one. A given
	 * [start] replaces both dates with [start] and [end]; [end] alone is ignored.
	 */
	suspend fun moveToTimeline(
		eventId: Int,
		target: String,
		start: StoryDate? = null,
		end: StoryDate? = null,
		allowUndated: Boolean = false,
		words: StoryDateWords = StoryDateWords.English,
	): TimelineResult = writeLock.withLock {
		val events = timeLineRepository.currentEvents()
		val existing = events.firstOrNull { it.id == eventId } ?: return invalid(TimeLineEventError.EVENT_NOT_FOUND)
		val data = projectDataRepository.load().data
		val targetDef = data.timelineDef(target) ?: return invalid(TimeLineEventError.TIMELINE_NOT_FOUND)
		if (existing.timelineId == targetDef.id) return invalid(TimeLineEventError.SAME_TIMELINE)
		val subject = existing.subject
		if (existing.kind.isPlacement && subject != null &&
			timeLineRepository.findPlacement(existing.kind, subject, targetDef.id) != null
		) {
			return invalid(TimeLineEventError.DUPLICATE_PLACEMENT)
		}

		val given = start != null
		val dates = if (given) DateRange(start, end) else carriedDates(existing, targetDef, allowUndated)
			?: return invalid(
				if (existing.start == null) TimeLineEventError.DATE_REQUIRED else TimeLineEventError.DATE_INVALID
			)
		val label = if (targetDef.mode == TimelineMode.MANUAL) {
			labelOrDates(existing, defFor(existing, data).calendar, words)
		} else {
			existing.label
		}
		val moved = existing.copy(
			timeline = normalizeTimeline(targetDef.id),
			order = events.nextOrder(normalizeTimeline(targetDef.id)),
			start = dates.start,
			end = dates.end,
			label = label,
		)
		val undatedAllowed = allowUndated && moved.start == null
		val error = timeLineRepository.validate(moved).orElse {
			if (undatedAllowed) TimeLineEventError.NONE else dateProblem(targetDef, moved.start, moved.end)
		}
		if (!error.isSuccess) return invalid(error)

		timeLineRepository.updateEvent(moved)
		TimelineResult.Ok(findEvent(eventId))
	}

	/**
	 * [event]'s dates as they carry to [target]: a start that does not validate on its calendar is
	 * dropped with the end, and an end that no longer fits is dropped alone. Null when a DATED target is
	 * left without a start and [allowUndated] is not set.
	 */
	private fun carriedDates(event: TimeLineEvent, target: TimelineDef, allowUndated: Boolean): DateRange? {
		val calendar = target.calendar
		val start = event.start
		val carried = when {
			start == null -> DateRange(null, null)
			calendar == null -> DateRange(start, event.end)
			!hasValidStart(event, calendar) -> DateRange(null, null)
			else -> DateRange(start, event.end?.takeIf { StoryCalendar.endFits(calendar, start, it) })
		}
		return if (target.mode == TimelineMode.DATED && carried.start == null && !allowUndated) null else carried
	}

	// endregion

	// region Events

	/** Creates a plain event. Placements go through [placeScene] and [placeEntry]. */
	suspend fun createEvent(draft: TimelineEventDraft): TimelineResult = writeLock.withLock {
		if (draft.kind != TimelineKind.EVENT) return invalid(TimeLineEventError.SUBJECT_NOT_ALLOWED)

		val prepared = draft.copy(lane = normalizeLane(draft.lane), timeline = normalizeTimeline(draft.timeline))
		val candidate = prepared.preview()
		val shapeError = timeLineRepository.validate(candidate)
		if (!shapeError.isSuccess) return invalid(shapeError)
		val def = liveDef(candidate.timelineId) ?: return invalid(TimeLineEventError.TIMELINE_NOT_FOUND)
		val error = dateProblem(def, candidate.start, candidate.end)
			.orElse { laneProblem(candidate, def) }
			.orElse { referenceProblem(candidate.references) }
		if (!error.isSuccess) return invalid(error)

		TimelineResult.Ok(timeLineRepository.createEvent(prepared))
	}

	/** Places a scene; an archived one must be unarchived first. */
	suspend fun placeScene(
		sceneId: Int,
		start: StoryDate?,
		end: StoryDate?,
		lane: String?,
		milestone: Boolean,
		label: String? = null,
		timeline: String = TimelineDef.MAIN_ID,
	): TimelineResult {
		val scene = withContext(dispatcherIo) { sceneRepository.getSceneItemFromIdIncludingArchived(sceneId) }
		val subjectError = when {
			scene == null -> TimeLineEventError.SUBJECT_NOT_FOUND
			scene.type != SceneItem.Type.Scene -> TimeLineEventError.SUBJECT_WRONG_TYPE
			scene.archived -> TimeLineEventError.SUBJECT_ARCHIVED
			else -> TimeLineEventError.NONE
		}
		return place(TimelineKind.SCENE, sceneId, scene?.name, subjectError, start, end, lane, milestone, label, timeline)
	}

	/** Places an encyclopedia entry; only EVENT entries can be placed. */
	suspend fun placeEntry(
		entryId: Int,
		start: StoryDate?,
		end: StoryDate?,
		lane: String?,
		milestone: Boolean,
		label: String? = null,
		timeline: String = TimelineDef.MAIN_ID,
	): TimelineResult {
		val entry = withContext(dispatcherIo) { encyclopediaRepository.findEntryDef(entryId) }
		val subjectError = when {
			entry == null -> TimeLineEventError.SUBJECT_NOT_FOUND
			entry.type != EntryType.EVENT -> TimeLineEventError.SUBJECT_WRONG_TYPE
			else -> TimeLineEventError.NONE
		}
		return place(TimelineKind.ENTRY, entryId, entry?.name, subjectError, start, end, lane, milestone, label, timeline)
	}

	private suspend fun place(
		kind: TimelineKind,
		subject: Int,
		subjectName: String?,
		subjectError: TimeLineEventError,
		start: StoryDate?,
		end: StoryDate?,
		lane: String?,
		milestone: Boolean,
		label: String?,
		timeline: String,
	): TimelineResult = writeLock.withLock {
		if (!subjectError.isSuccess) return invalid(subjectError)
		val def = liveDef(timeline) ?: return invalid(TimeLineEventError.TIMELINE_NOT_FOUND)
		if (timeLineRepository.findPlacement(kind, subject, def.id) != null) {
			return invalid(TimeLineEventError.DUPLICATE_PLACEMENT)
		}

		val draft = TimelineEventDraft(
			kind = kind,
			subject = subject,
			label = label,
			lane = normalizeLane(lane),
			milestone = milestone,
			start = start,
			end = end,
			cachedTitle = subjectName?.take(TimelineLimits.MAX_TITLE),
			timeline = normalizeTimeline(def.id),
		)
		val candidate = draft.preview()
		val error = timeLineRepository.validate(candidate)
			.orElse { dateProblem(def, start, end) }
			.orElse { laneProblem(candidate, def) }
		if (!error.isSuccess) return invalid(error)

		TimelineResult.Ok(timeLineRepository.createEvent(draft))
	}

	/**
	 * Sets the placement fields, which every kind has, leaving the rest alone. A placement's cached
	 * title is refreshed from its subject on the way.
	 */
	suspend fun updatePlacement(
		eventId: Int,
		start: StoryDate?,
		end: StoryDate?,
		lane: String?,
		milestone: Boolean,
	): TimelineResult = writeLock.withLock {
		editPlacement(eventId) { it.copy(start = start, end = end, lane = lane, milestone = milestone) }
	}

	/** [updatePlacement] that also sets the label; null clears it. */
	suspend fun updatePlacement(
		eventId: Int,
		start: StoryDate?,
		end: StoryDate?,
		lane: String?,
		milestone: Boolean,
		label: String?,
	): TimelineResult = writeLock.withLock {
		editPlacement(eventId) {
			it.copy(start = start, end = end, lane = lane, milestone = milestone, label = label)
		}
	}

	/**
	 * Replaces a plain event, fully validated. The kind and subject cannot change; for a placement only
	 * the placement fields, label included, are taken from [event]. References already on the event are
	 * kept even when their target is gone, only new ones must exist.
	 */
	suspend fun updateEvent(event: TimeLineEvent): TimelineResult = writeLock.withLock {
		val existing = findEvent(event.id) ?: return invalid(TimeLineEventError.EVENT_NOT_FOUND)
		if (event.kind != existing.kind || event.subject != existing.subject) {
			return invalid(TimeLineEventError.SUBJECT_NOT_ALLOWED)
		}
		if (existing.kind.isPlacement) {
			return editPlacement(event.id) {
				it.copy(
					start = event.start,
					end = event.end,
					lane = event.lane,
					milestone = event.milestone,
					label = event.label,
				)
			}
		}

		// The timeline is changed only by moveToTimeline, which applies the target's rules.
		val updated = event.copy(lane = normalizeLane(event.lane), timeline = existing.timeline)
		val error = timeLineRepository.validate(updated)
			.orElse { dateProblem(defFor(existing), updated.start, updated.end) }
			.orElse { laneProblem(updated, defFor(existing)) }
			.orElse { referenceProblem(updated.references - existing.references) }
		if (!error.isSuccess) return invalid(error)

		timeLineRepository.updateEvent(updated)
		TimelineResult.Ok(findEvent(event.id))
	}

	suspend fun setDates(eventId: Int, start: StoryDate?, end: StoryDate?): TimelineResult = writeLock.withLock {
		editPlacement(eventId) { it.copy(start = start, end = end) }
	}

	suspend fun setLane(eventId: Int, lane: String?): TimelineResult = writeLock.withLock {
		editPlacement(eventId) { it.copy(lane = lane) }
	}

	suspend fun setMilestone(eventId: Int, milestone: Boolean): TimelineResult = writeLock.withLock {
		editPlacement(eventId) { it.copy(milestone = milestone) }
	}

	/** The free-text "when" a manual timeline shows; any kind has one. */
	suspend fun setLabel(eventId: Int, label: String?): TimelineResult = writeLock.withLock {
		editPlacement(eventId) { it.copy(label = label) }
	}

	/** The caller holds [writeLock]. */
	private suspend fun editPlacement(eventId: Int, edit: (TimeLineEvent) -> TimeLineEvent): TimelineResult {
		val existing = findEvent(eventId) ?: return invalid(TimeLineEventError.EVENT_NOT_FOUND)
		val edited = edit(existing)
		val updated = existing.copy(
			start = edited.start,
			end = edited.end,
			lane = normalizeLane(edited.lane),
			milestone = edited.milestone,
			label = edited.label,
			cachedTitle = subjectName(existing)?.take(TimelineLimits.MAX_TITLE) ?: existing.cachedTitle,
		)
		val def = defFor(existing)
		val error = timeLineRepository.validate(updated)
			.orElse { dateProblem(def, updated.start, updated.end) }
			.orElse { laneProblem(updated, def) }
		if (!error.isSuccess) return invalid(error)

		if (updated != existing) timeLineRepository.updateEvent(updated)
		return TimelineResult.Ok(findEvent(eventId))
	}

	/** Removes the event; a placement's scene or entry is left alone. */
	suspend fun deleteEvent(eventId: Int): TimelineResult = writeLock.withLock {
		val existing = findEvent(eventId) ?: return invalid(TimeLineEventError.EVENT_NOT_FOUND)
		timeLineRepository.deleteEvent(existing)
		TimelineResult.Ok(null)
	}

	/** Drag reordering within the event's own timeline; only a MANUAL timeline has an order of its own. */
	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): TimelineResult = writeLock.withLock {
		val events = timeLineRepository.currentEvents()
		val stored = events.firstOrNull { it.id == event.id } ?: return invalid(TimeLineEventError.EVENT_NOT_FOUND)
		if (defFor(stored).mode == TimelineMode.DATED) return invalid(TimeLineEventError.MOVE_NOT_ALLOWED)
		// Indices are positions among the event's own timeline, which is all the repository moves.
		val timelineSize = events.count { it.timelineId == stored.timelineId }
		if (toIndex !in 0..<timelineSize) return invalid(TimeLineEventError.MOVE_FAILED)
		// With the event present and the index in range, the repository refuses only a move to where it already is.
		timeLineRepository.moveEvent(event, toIndex, after)
		TimelineResult.Ok(findEvent(event.id))
	}

	// endregion

	// region Mode and calendar

	/**
	 * Every event without a valid start under [calendar] gets one from [spacing] over the current
	 * display order, so the dated order matches it; then the timeline becomes DATED. Without
	 * [spacing], events are one of the calendar's coarsest unit apart from zero (year 1 for GREGORIAN).
	 * Only [timeline]'s events are touched.
	 */
	suspend fun switchToDated(
		calendar: CalendarDef,
		spacing: EvenSpacing? = null,
		timeline: String = TimelineDef.MAIN_ID,
	): TimelineResult = writeLock.withLock {
		val def = liveDef(timeline) ?: return invalid(TimeLineEventError.TIMELINE_NOT_FOUND)
		val revisionError = revisionProblem(def) { it.copy(mode = TimelineMode.DATED, calendar = calendar) }
		if (!revisionError.isSuccess) return invalid(revisionError)

		val ordered = currentDisplayOrder(def)
		val undated = ordered.filterNot { hasValidStart(it, calendar) }
		if (undated.isNotEmpty()) {
			val even = spacing ?: defaultSpacing(calendar)
			if (!isValidSpacing(even, calendar)) return invalid(TimeLineEventError.DATE_INVALID)

			val dates = spaceEvenly(ordered, calendar, even)
			val updates = undated.map { event ->
				val start = dates.getValue(event.id)
				if (StoryCalendar.validate(calendar, start) != null) return invalid(TimeLineEventError.DATE_INVALID)
				event.copy(start = start, end = event.end?.takeIf { StoryCalendar.endFits(calendar, start, it) })
			}
			timeLineRepository.updateEvents(updates)
		}

		updateDefinition(def.id) { it.copy(mode = TimelineMode.DATED, calendar = calendar) }
	}

	/**
	 * Stores the current display order as the manual order, then the timeline becomes MANUAL. Dates
	 * stay; a dated event without a label gets its dates, written in [words], as one. Only [timeline]'s
	 * events are touched.
	 */
	suspend fun switchToManual(
		words: StoryDateWords = StoryDateWords.English,
		timeline: String = TimelineDef.MAIN_ID,
	): TimelineResult = writeLock.withLock {
		val def = liveDef(timeline) ?: return invalid(TimeLineEventError.TIMELINE_NOT_FOUND)
		val revisionError = revisionProblem(def) { it.copy(mode = TimelineMode.MANUAL) }
		if (!revisionError.isSuccess) return invalid(revisionError)

		val calendar = def.calendar
		val ordered = currentDisplayOrder(def)
		val frozen = freezeOrder(ordered)
		val updates = ordered.map { event ->
			event.copy(order = frozen.getValue(event.id), label = labelOrDates(event, calendar, words))
		}
		timeLineRepository.updateEvents(updates)
		updateDefinition(def.id) { it.copy(mode = TimelineMode.MANUAL) }
	}

	/** How many of [timeline]'s events [switchPreset] to [calendar] would take the start date from. */
	suspend fun countDatesLostBySwitch(calendar: CalendarDef, timeline: String = TimelineDef.MAIN_ID): Int =
		timeLineRepository.currentEvents().count {
			it.timelineId == timeline && it.start != null && !hasValidStart(it, calendar)
		}

	/**
	 * Moves the timeline to [calendar]. An event whose start does not fit it loses its dates and, when
	 * it has no label, keeps the old start, written in [words], as one. An end that no longer fits is
	 * dropped. Only [timeline]'s events are touched.
	 */
	suspend fun switchPreset(
		calendar: CalendarDef,
		words: StoryDateWords = StoryDateWords.English,
		timeline: String = TimelineDef.MAIN_ID,
	): TimelineResult = writeLock.withLock {
		val def = liveDef(timeline) ?: return invalid(TimeLineEventError.TIMELINE_NOT_FOUND)
		val revisionError = revisionProblem(def) { it.copy(calendar = calendar) }
		if (!revisionError.isSuccess) return invalid(revisionError)

		val previous = def.calendar
		val updates = eventsOf(def.id).mapNotNull { event ->
			val start = event.start
			val end = event.end
			when {
				start != null && !hasValidStart(event, calendar) -> event.copy(
					start = null,
					end = null,
					label = event.label?.takeIf { it.isNotBlank() }
						?: (previous?.let { formatStoryDate(it, start, words) } ?: start.toCompactText())
							.take(TimelineLimits.MAX_LABEL),
				)

				end != null && (start == null || !StoryCalendar.endFits(calendar, start, end)) -> event.copy(end = null)
				else -> null
			}
		}
		if (updates.isNotEmpty()) timeLineRepository.updateEvents(updates)

		updateDefinition(def.id) { it.copy(calendar = calendar) }
	}

	// endregion

	// region Reading

	/**
	 * Null for a plain event; [SubjectSummary.Missing] when the subject is gone, and
	 * [SubjectSummary.Archived] for a scene in the archive.
	 */
	suspend fun resolveSubject(event: TimeLineEvent): SubjectSummary? {
		val subject = event.subject
		return when (event.kind) {
			TimelineKind.EVENT -> null
			TimelineKind.SCENE -> subject?.let { sceneSummary(it) } ?: SubjectSummary.Missing
			TimelineKind.ENTRY -> subject?.let { entrySummary(it) } ?: SubjectSummary.Missing
		}
	}

	/**
	 * Placement [eventId]'s subject as it is when collection starts, then again each time it changes:
	 * the placed scene is renamed, moved, archived or deleted or its cast changes, the placed entry
	 * is edited or deleted, or the placement itself is edited. Signals about other scenes and entries
	 * are dropped before anything is read. A plain event, or one that is gone, never emits.
	 */
	@OptIn(ExperimentalCoroutinesApi::class)
	fun subjectUpdates(eventId: Int): Flow<PlacedSubject> =
		storedTimeline
			.map { timeline -> timeline.events.firstOrNull { it.id == eventId }?.takeIf { it.kind.isPlacement } }
			.distinctUntilChanged()
			.flatMapLatest { placement ->
				if (placement == null) {
					emptyFlow()
				} else {
					subjectChanges(placement).conflate().mapNotNull {
						resolveSubject(placement)?.let { PlacedSubject(it, displayTitle(placement)) }
					}
				}
			}

	/**
	 * Fires once every source is subscribed, then whenever [placement]'s subject may read differently.
	 * Sources are subscribed undispatched so nothing that lands before the first signal is missed.
	 */
	private fun subjectChanges(placement: TimeLineEvent): Flow<Unit> = channelFlow {
		val subjectId = placement.subject
		when {
			subjectId == null -> Unit

			placement.kind == TimelineKind.SCENE -> {
				var branch = sceneBranch(subjectId)
				launch(start = CoroutineStart.UNDISPATCHED) {
					sceneMetadataRepository.metadataUpdateFlow.collect { if (it.sceneId == subjectId) send(Unit) }
				}
				// Tree changes elsewhere leave the branch as it was; the replayed tree matches the baseline.
				launch(start = CoroutineStart.UNDISPATCHED) {
					sceneRepository.sceneTreeUpdates.collect {
						val current = sceneBranch(subjectId)
						if (current != branch) {
							branch = current
							send(Unit)
						}
					}
				}
			}

			else -> launch(start = CoroutineStart.UNDISPATCHED) {
				encyclopediaRepository.entryChangedFlow.collect { if (it == subjectId) send(Unit) }
			}
		}
		send(Unit)
	}

	/** The subject's name, else the title, the content's first line, the cached title, or "". */
	suspend fun displayTitle(event: TimeLineEvent): String = displayTitles(listOf(event)).getValue(event.id)

	/** [displayTitle] for every event, by id, reading the scene tree and the entry list once. */
	suspend fun displayTitles(events: List<TimeLineEvent>): Map<Int, String> =
		glances(events).mapValues { it.value.title }

	/**
	 * [EventGlance] for every event, by id, reading the scene tree, the archive and the entry list
	 * at most once each.
	 */
	suspend fun glances(events: List<TimeLineEvent>): Map<Int, EventGlance> = withContext(dispatcherIo) {
		val tree by lazy { sceneRepository.rawTree }
		var archived: Map<Int, SceneItem>? = null
		var entryNames: Map<Int, String>? = null
		events.associate { event ->
			val subject = event.subject
			val glance = when (event.kind) {
				TimelineKind.EVENT -> EventGlance(titleOf(event, null))
				TimelineKind.SCENE -> {
					val branch = subject?.let { id -> tree.getBranchOrNull(excludeLeaf = false) { it.id == id } }
					if (branch != null) {
						val scene = branch.last().value
						val isScene = scene.type == SceneItem.Type.Scene
						val path = branch.dropLast(1)
							.map { it.value }
							.filter { it.type == SceneItem.Type.Group }
							.joinToString(CHAPTER_SEPARATOR) { it.name }
						EventGlance(
							title = titleOf(event, scene.name.takeIf { isScene }),
							chapterPath = path.ifEmpty { null },
							missing = !isScene,
						)
					} else {
						val inArchive = subject?.let {
							val byId = archived ?: sceneRepository.getArchivedScenes().associateBy { it.id }
								.also { archived = it }
							byId[it]?.takeIf { scene -> scene.type == SceneItem.Type.Scene }
						}
						EventGlance(
							title = titleOf(event, inArchive?.name),
							missing = inArchive == null,
							archived = inArchive != null,
						)
					}
				}

				TimelineKind.ENTRY -> {
					val names = entryNames ?: encyclopediaRepository.readEntryDefs().associate { it.id to it.name }
						.also { entryNames = it }
					val name = subject?.let(names::get)
					EventGlance(titleOf(event, name), missing = name == null)
				}
			}
			event.id to glance
		}
	}

	/** [timeline]'s lane order, then any other lane its events use, alphabetically. */
	suspend fun lanes(timeline: String = TimelineDef.MAIN_ID): List<String> {
		val ordered = definition(timeline)?.laneOrder.orEmpty()
		val others = eventsOf(timeline)
			.mapNotNull { it.lane }
			.filter { it !in ordered }
			.distinct()
			.sortedWith(compareBy<String> { it.lowercase() }.thenBy { it })
		return ordered + others
	}

	// endregion

	private suspend fun sceneSummary(sceneId: Int): SubjectSummary = withContext(dispatcherIo) {
		val scene = sceneRepository.getSceneItemFromIdIncludingArchived(sceneId)
		if (scene == null || scene.type != SceneItem.Type.Scene) return@withContext SubjectSummary.Missing
		if (scene.archived) return@withContext SubjectSummary.Archived(scene)

		val text = sceneRepository.resolveScenePathFromFilesystemIncludingArchived(scene.id)
			?.let { sceneRepository.loadSceneMarkdownRaw(scene, it) }
			.orEmpty()
		val confirmed = sceneMetadataRepository.loadRawMetadata(scene.id)?.confirmedReferences.orEmpty()
		val cast = if (confirmed.isEmpty()) {
			emptyList()
		} else {
			val entries = encyclopediaRepository.readEntryDefs().associateBy { it.id }
			confirmed.mapNotNull { entries[it] }
				.sortedWith(compareBy<EntryDef>({ timelineCastRank(it.type) }, { it.name.lowercase() }, { it.id }))
		}
		SubjectSummary.Scene(scene, chapterPath(scene), markdownExcerpt(text, EXCERPT_CHARS), cast)
	}

	private suspend fun entrySummary(entryId: Int): SubjectSummary = withContext(dispatcherIo) {
		val def = encyclopediaRepository.findEntryDef(entryId) ?: return@withContext SubjectSummary.Missing
		val content = try {
			encyclopediaRepository.loadEntry(def).entry
		} catch (e: EntryLoadError) {
			Napier.w(e) { "Could not read entry $entryId for the timeline" }
			null
		}
		SubjectSummary.Entry(def, content?.images?.firstOrNull(), markdownExcerpt(content?.text.orEmpty(), EXCERPT_CHARS))
	}

	/** Scene [sceneId] and the groups above it in the live tree, or null when it is not there. */
	private fun sceneBranch(sceneId: Int): List<SceneItem>? =
		sceneRepository.rawTree.getBranchOrNull(excludeLeaf = false) { it.id == sceneId }?.map { it.value }

	private fun chapterPath(scene: SceneItem): String {
		if (scene.archived) return ""
		return sceneRepository.rawTree.getBranchOrNull(excludeLeaf = true) { it.id == scene.id }
			.orEmpty()
			.map { it.value }
			.filter { it.type == SceneItem.Type.Group }
			.joinToString(CHAPTER_SEPARATOR) { it.name }
	}

	private suspend fun subjectName(event: TimeLineEvent): String? {
		val subject = event.subject ?: return null
		return withContext(dispatcherIo) {
			when (event.kind) {
				TimelineKind.EVENT -> null
				TimelineKind.SCENE -> sceneRepository.getSceneItemFromIdIncludingArchived(subject)
					?.takeIf { it.type == SceneItem.Type.Scene }?.name

				TimelineKind.ENTRY -> encyclopediaRepository.findEntryDef(subject)?.name
			}
		}
	}

	private suspend fun findEvent(id: Int): TimeLineEvent? =
		timeLineRepository.currentEvents().firstOrNull { it.id == id }

	private suspend fun eventsOf(timeline: String): List<TimeLineEvent> =
		timeLineRepository.currentEvents().filter { it.timelineId == timeline }

	private suspend fun orphansOf(events: List<TimeLineEvent>): List<TimeLineEvent> {
		val live = projectDataRepository.load().data.liveTimelines().mapTo(mutableSetOf()) { it.id }
		return events.filter { it.timelineId !in live }
	}

	private suspend fun liveDef(timeline: String): TimelineDef? = projectDataRepository.load().data.timelineDef(timeline)

	/**
	 * The definition whose rules [event] follows. A straggler, whose timeline is gone, gets a manual
	 * one with no calendar so it can still be edited before it is moved.
	 */
	private suspend fun defFor(event: TimeLineEvent, data: ProjectData? = null): TimelineDef =
		(data ?: projectDataRepository.load().data).timelineDef(event.timelineId)
			?: TimelineDef(id = event.timelineId, name = "")

	private suspend fun currentDisplayOrder(def: TimelineDef): List<TimeLineEvent> =
		displayOrder(eventsOf(def.id), def.mode, def.calendar)

	/**
	 * [TimeLineEventError.TIMELINE_NAME_INVALID] when [name] is blank, too long, or what a live
	 * timeline other than [ownId] is called, in any case.
	 */
	private fun nameProblem(data: ProjectData, name: String, ownId: String): TimeLineEventError =
		if (
			name.isBlank() ||
			name.length > TimelineLimits.MAX_TIMELINE_NAME ||
			data.liveTimelines().any { it.id != ownId && it.name.equals(name, ignoreCase = true) }
		) {
			TimeLineEventError.TIMELINE_NAME_INVALID
		} else {
			TimeLineEventError.NONE
		}

	/** [transformed] as [updateDefinition] would write it over [current]: same id, and a new name trimmed. */
	private fun revised(current: TimelineDef, transformed: TimelineDef): TimelineDef = transformed.copy(
		id = current.id,
		name = if (transformed.name == current.name) current.name else transformed.name.trim(),
	)

	/** Why [updateDefinition] would refuse to replace [current] with [next], which [revised] made. */
	private fun revisionProblem(data: ProjectData, current: TimelineDef, next: TimelineDef): TimeLineEventError =
		definitionProblem(next).orElse {
			if (next.name == current.name) TimeLineEventError.NONE else nameProblem(data, next.name, current.id)
		}

	/** [revisionProblem] for applying [transform] to [current] against the stored project data. */
	private suspend fun revisionProblem(current: TimelineDef, transform: (TimelineDef) -> TimelineDef): TimeLineEventError =
		revisionProblem(projectDataRepository.load().data, current, revised(current, transform(current)))

	/** Every reference names a live scene or an entry; an archived scene does not count. */
	private suspend fun referenceProblem(references: Set<Int>): TimeLineEventError {
		if (references.isEmpty()) return TimeLineEventError.NONE
		return withContext(dispatcherIo) {
			val notScenes = references.filter { id ->
				val scene = sceneRepository.getSceneItemFromIdIncludingArchived(id)
				scene?.type != SceneItem.Type.Scene || scene.archived
			}
			val entryIds = if (notScenes.isEmpty()) emptySet() else encyclopediaRepository.readEntryDefs().map { it.id }
			if (notScenes.all { it in entryIds }) TimeLineEventError.NONE else TimeLineEventError.REFERENCE_NOT_FOUND
		}
	}

	/**
	 * Writing [event], new or replacing the one with its id, must not add a lane past the limit on its own
	 * timeline: [def]'s lane order and the lanes of that timeline's [events], by default the stored ones.
	 */
	private suspend fun laneProblem(
		event: TimeLineEvent,
		def: TimelineDef,
		events: List<TimeLineEvent>? = null,
	): TimeLineEventError {
		val lane = event.lane ?: return TimeLineEventError.NONE
		val onTimeline = (events ?: timeLineRepository.currentEvents()).filter { it.timelineId == event.timelineId }
		val current = def.laneOrder.toSet() + onTimeline.mapNotNull { it.lane }
		val next = def.laneOrder.toSet() + onTimeline.filter { it.id != event.id }.mapNotNull { it.lane } + lane
		return laneLimitProblem(current, next)
	}

	/** The rules on a def's settings; its id and name are checked where they are chosen. */
	private fun definitionProblem(def: TimelineDef): TimeLineEventError {
		val calendar = def.calendar
		return when {
			calendar != null && StoryCalendar.validate(calendar) != null -> TimeLineEventError.DATE_INVALID
			def.mode == TimelineMode.DATED && calendar == null -> TimeLineEventError.DATE_REQUIRED
			def.laneOrder.any { it.isBlank() || it.length > TimelineLimits.MAX_LANE } -> TimeLineEventError.LANE_INVALID
			else -> TimeLineEventError.NONE
		}
	}

	/** Without a calendar the dates are free-form: only their shape and "end requires start" apply. */
	private fun dateProblem(def: TimelineDef, start: StoryDate?, end: StoryDate?): TimeLineEventError {
		val calendar = def.calendar
		return when {
			start == null && (def.mode == TimelineMode.DATED || end != null) -> TimeLineEventError.DATE_REQUIRED
			start == null || calendar == null -> TimeLineEventError.NONE
			else -> when (StoryCalendar.rangeProblem(calendar, start, end)) {
				null -> TimeLineEventError.NONE
				is TimelineDateProblem.Invalid -> TimeLineEventError.DATE_INVALID
				TimelineDateProblem.EndBeforeStart -> TimeLineEventError.END_BEFORE_START
			}
		}
	}

	private companion object {
		const val CHAPTER_SEPARATOR = " › "
		const val EXCERPT_CHARS = 200

		// Never stored: a draft has no id until the repository claims one.
		const val NO_ID = -1

		fun invalid(error: TimeLineEventError) = TimelineResult.Invalid(error)

		fun invalidDef(error: TimeLineEventError) = TimelineDefResult.Invalid(error)

		data class DateRange(val start: StoryDate?, val end: StoryDate?)

		/**
		 * The server checks each entry alone and cannot count lanes, so offline edits on two devices can
		 * sync a timeline past the limit. Such a timeline keeps its lanes; it just cannot gain one.
		 */
		fun laneLimitProblem(current: Set<String>, next: Set<String>): TimeLineEventError =
			if (next.size > TimelineLimits.MAX_LANES && next.size > current.size) {
				TimeLineEventError.TOO_MANY_LANES
			} else {
				TimeLineEventError.NONE
			}

		fun result(error: TimeLineEventError): TimelineResult =
			if (error.isSuccess) TimelineResult.Ok(null) else TimelineResult.Invalid(error)

		inline fun TimeLineEventError.orElse(next: () -> TimeLineEventError): TimeLineEventError =
			if (isSuccess) next() else this

		fun ProjectData.withTimeline(def: TimelineDef): ProjectData {
			val index = timelines.indexOfFirst { it.id == def.id }
			val next = if (index < 0) timelines + def else timelines.toMutableList().also { it[index] = def }
			return copy(timelines = next)
		}

		fun TimelineEventDraft.preview() = toEvent(id ?: NO_ID, order ?: 0)

		/** The event's label, or when it has none, its dates on [calendar] if they fit it. */
		fun labelOrDates(event: TimeLineEvent, calendar: CalendarDef?, words: StoryDateWords): String? {
			if (!event.label.isNullOrBlank()) return event.label
			val start = event.start
			if (calendar == null || start == null || !hasValidStart(event, calendar)) return event.label
			return formatStoryDateRange(calendar, start, event.end, words).take(TimelineLimits.MAX_LABEL)
		}

		fun defaultSpacing(calendar: CalendarDef): EvenSpacing = when (calendar.preset) {
			CalendarPreset.STORY -> {
				val unit = calendar.coarsestUnit ?: StoryCalendar.units(calendar.preset).first().name
				EvenSpacing(unit = unit, origin = StoryDate(mapOf(unit to 0L)))
			}

			CalendarPreset.GREGORIAN ->
				EvenSpacing(unit = StoryCalendar.YEAR, origin = StoryDate(mapOf(StoryCalendar.YEAR to 1L)))
		}

		fun isValidSpacing(spacing: EvenSpacing, calendar: CalendarDef): Boolean =
			spacing.step > 0 &&
				StoryCalendar.units(calendar.preset).any { it.name == spacing.unit } &&
				StoryCalendar.validate(calendar, spacing.origin) == null

		fun titleOf(event: TimeLineEvent, subjectName: String?): String =
			subjectName
				?: event.title?.takeIf { it.isNotBlank() }
				?: markdownTitleLine(event.content).takeIf { it.isNotBlank() }
				?: event.cachedTitle
				?: ""
	}
}
