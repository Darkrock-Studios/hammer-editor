package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
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
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository.Companion.normalizeLane
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
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

/** Whether [event] has a start, and it is a valid date on [calendar]. */
fun hasValidStart(event: TimeLineEvent, calendar: CalendarDef): Boolean =
	event.start?.let { StoryCalendar.validate(calendar, it) == null } == true

/** Up to [chars] of [markdown] as plain prose with whitespace collapsed; blank for blank markdown. */
internal fun markdownExcerpt(markdown: String, chars: Int): String {
	if (markdown.isBlank()) return ""
	val projector = MarkdownProjector(markdown.length)
	projector.project(markdown)
	return projector.collapsedPreview(chars)
}

/** A timeline's settings and its events in display order. */
data class TimelineView(val def: TimelineDef, val events: List<TimeLineEvent>)

sealed interface TimelineResult {
	val isSuccess: Boolean get() = this is Ok

	data class Ok(val event: TimeLineEvent?) : TimelineResult
	data class Invalid(val error: TimeLineEventError) : TimelineResult
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
 * lives here: subjects exist and have the right type, one placement per subject, references exist,
 * dates are valid for the calendar and required in DATED mode. Settings are read and written through
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

	val timelineFlow: Flow<TimelineView> = combine(
		flow {
			// Loads the timeline when nothing has yet, so the repository's flow has a value to replay.
			timeLineRepository.currentEvents()
			emitAll(timeLineRepository.timelineFlow)
		},
		flow {
			projectDataRepository.load()
			emitAll(projectDataRepository.state.filterNotNull())
		},
	) { timeline, stored ->
		val def = stored.data.mainTimeline()
		TimelineView(def, displayOrder(timeline.events, def.mode, def.calendar))
	}.distinctUntilChanged()

	suspend fun definition(): TimelineDef = projectDataRepository.load().data.mainTimeline()

	/** Upserts the result of [transform] by id. Rejects an invalid calendar or a DATED def without one. */
	suspend fun updateDefinition(transform: (TimelineDef) -> TimelineDef): TimelineResult {
		var error = TimeLineEventError.NONE
		projectDataRepository.updateData { data ->
			val current = data.mainTimeline()
			val next = transform(current)
			error = definitionProblem(next)
			if (!error.isSuccess || next == current) data else data.withTimeline(next)
		}
		return result(error)
	}

	// region Events

	/** Creates a plain event. Placements go through [placeScene] and [placeEntry]. */
	suspend fun createEvent(draft: TimelineEventDraft): TimelineResult = writeLock.withLock {
		if (draft.kind != TimelineKind.EVENT) return invalid(TimeLineEventError.SUBJECT_NOT_ALLOWED)

		val prepared = draft.copy(lane = normalizeLane(draft.lane))
		val candidate = prepared.preview()
		val error = timeLineRepository.validate(candidate)
			.orElse { dateProblem(definition(), candidate.start, candidate.end) }
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
	): TimelineResult {
		val scene = withContext(dispatcherIo) { sceneRepository.getSceneItemFromIdIncludingArchived(sceneId) }
		val subjectError = when {
			scene == null -> TimeLineEventError.SUBJECT_NOT_FOUND
			scene.type != SceneItem.Type.Scene -> TimeLineEventError.SUBJECT_WRONG_TYPE
			scene.archived -> TimeLineEventError.SUBJECT_ARCHIVED
			else -> TimeLineEventError.NONE
		}
		return place(TimelineKind.SCENE, sceneId, scene?.name, subjectError, start, end, lane, milestone, label)
	}

	/** Places an encyclopedia entry; only EVENT entries can be placed. */
	suspend fun placeEntry(
		entryId: Int,
		start: StoryDate?,
		end: StoryDate?,
		lane: String?,
		milestone: Boolean,
		label: String? = null,
	): TimelineResult {
		val entry = withContext(dispatcherIo) { encyclopediaRepository.findEntryDef(entryId) }
		val subjectError = when {
			entry == null -> TimeLineEventError.SUBJECT_NOT_FOUND
			entry.type != EntryType.EVENT -> TimeLineEventError.SUBJECT_WRONG_TYPE
			else -> TimeLineEventError.NONE
		}
		return place(TimelineKind.ENTRY, entryId, entry?.name, subjectError, start, end, lane, milestone, label)
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
	): TimelineResult = writeLock.withLock {
		if (!subjectError.isSuccess) return invalid(subjectError)
		if (timeLineRepository.findPlacement(subject) != null) return invalid(TimeLineEventError.DUPLICATE_PLACEMENT)

		val draft = TimelineEventDraft(
			kind = kind,
			subject = subject,
			label = label,
			lane = normalizeLane(lane),
			milestone = milestone,
			start = start,
			end = end,
			cachedTitle = subjectName?.take(TimelineLimits.MAX_TITLE),
		)
		val candidate = draft.preview()
		val error = timeLineRepository.validate(candidate).orElse { dateProblem(definition(), start, end) }
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

		val updated = event.copy(lane = normalizeLane(event.lane))
		val error = timeLineRepository.validate(updated)
			.orElse { dateProblem(definition(), updated.start, updated.end) }
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
		val error = timeLineRepository.validate(updated).orElse { dateProblem(definition(), updated.start, updated.end) }
		if (!error.isSuccess) return invalid(error)

		if (updated != existing) timeLineRepository.updateEvent(updated)
		return TimelineResult.Ok(findEvent(eventId))
	}

	/** Drag reordering; only a MANUAL timeline has an order of its own. */
	suspend fun moveEvent(event: TimeLineEvent, toIndex: Int, after: Boolean): TimelineResult = writeLock.withLock {
		if (definition().mode == TimelineMode.DATED) return invalid(TimeLineEventError.MOVE_NOT_ALLOWED)
		val events = timeLineRepository.currentEvents()
		if (events.none { it.id == event.id }) return invalid(TimeLineEventError.EVENT_NOT_FOUND)
		if (toIndex !in events.indices) return invalid(TimeLineEventError.MOVE_FAILED)
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
	 */
	suspend fun switchToDated(calendar: CalendarDef, spacing: EvenSpacing? = null): TimelineResult = writeLock.withLock {
		if (StoryCalendar.validate(calendar) != null) return invalid(TimeLineEventError.DATE_INVALID)

		val ordered = currentDisplayOrder()
		val undated = ordered.filterNot { hasValidStart(it, calendar) }
		if (undated.isNotEmpty()) {
			val even = spacing ?: defaultSpacing(calendar)
			if (!isValidSpacing(even, calendar)) return invalid(TimeLineEventError.DATE_INVALID)

			val dates = spaceEvenly(ordered, calendar, even)
			val updates = undated.map { event ->
				val start = dates.getValue(event.id)
				if (StoryCalendar.validate(calendar, start) != null) return invalid(TimeLineEventError.DATE_INVALID)
				event.copy(start = start, end = event.end?.takeIf { endFits(calendar, start, it) })
			}
			timeLineRepository.updateEvents(updates)
		}

		updateDefinition { it.copy(mode = TimelineMode.DATED, calendar = calendar) }
	}

	/**
	 * Stores the current display order as the manual order, then the timeline becomes MANUAL. Dates
	 * stay; a dated event without a label gets its dates, written in [words], as one.
	 */
	suspend fun switchToManual(words: StoryDateWords = StoryDateWords.English): TimelineResult = writeLock.withLock {
		val calendar = definition().calendar
		val ordered = currentDisplayOrder()
		val frozen = freezeOrder(ordered)
		val updates = ordered.map { event ->
			event.copy(order = frozen.getValue(event.id), label = labelOrDates(event, calendar, words))
		}
		timeLineRepository.updateEvents(updates)
		updateDefinition { it.copy(mode = TimelineMode.MANUAL) }
	}

	/** How many events [switchPreset] to [calendar] would take the start date from. */
	suspend fun countDatesLostBySwitch(calendar: CalendarDef): Int =
		timeLineRepository.currentEvents().count { it.start != null && !hasValidStart(it, calendar) }

	/**
	 * Moves the timeline to [calendar]. An event whose start does not fit it loses its dates and, when
	 * it has no label, keeps the old start, written in [words], as one. An end that no longer fits is
	 * dropped.
	 */
	suspend fun switchPreset(
		calendar: CalendarDef,
		words: StoryDateWords = StoryDateWords.English,
	): TimelineResult = writeLock.withLock {
		if (StoryCalendar.validate(calendar) != null) return invalid(TimeLineEventError.DATE_INVALID)

		val previous = definition().calendar
		val updates = timeLineRepository.currentEvents().mapNotNull { event ->
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

				end != null && (start == null || !endFits(calendar, start, end)) -> event.copy(end = null)
				else -> null
			}
		}
		if (updates.isNotEmpty()) timeLineRepository.updateEvents(updates)

		updateDefinition { it.copy(calendar = calendar) }
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

	/** The definition's lane order, then any other lane in use, alphabetically. */
	suspend fun lanes(): List<String> {
		val ordered = definition().laneOrder
		val others = timeLineRepository.currentEvents()
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

	private suspend fun currentDisplayOrder(): List<TimeLineEvent> {
		val def = definition()
		return displayOrder(timeLineRepository.currentEvents(), def.mode, def.calendar)
	}

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
			StoryCalendar.validate(calendar, start) != null -> TimeLineEventError.DATE_INVALID
			end == null -> TimeLineEventError.NONE
			StoryCalendar.validate(calendar, end) != null -> TimeLineEventError.DATE_INVALID
			!endFits(calendar, start, end) -> TimeLineEventError.END_BEFORE_START
			else -> TimeLineEventError.NONE
		}
	}

	private companion object {
		const val CHAPTER_SEPARATOR = " › "
		const val EXCERPT_CHARS = 200

		// Never stored: a draft has no id until the repository claims one.
		const val NO_ID = -1

		fun invalid(error: TimeLineEventError) = TimelineResult.Invalid(error)

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

		/** [end] is valid and its span reaches [start]: a day-precise end may close an hour-precise start. */
		fun endFits(calendar: CalendarDef, start: StoryDate, end: StoryDate): Boolean {
			val startAt = StoryCalendar.position(calendar, start) ?: return false
			val endSpan = StoryCalendar.span(calendar, end) ?: return false
			return endSpan.last >= startAt
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
