package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDateProblem
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.EvenSpacing
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineView
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.timelineId
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun timelineOperations(): List<Operation<*, *>> = listOf(
	operation<TimelinePlaceInput, TimelineEntry>(
		name = "timeline.place",
		description = "Place an encyclopedia entry of type event, or a scene, on a project's timeline, main unless " +
			"timeline names another. Each can be placed once on each timeline, and its name and words come from it. " +
			"A dated timeline needs a start.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val timeline = project.timelineService()
			val target = project.requireTimeline(input.timeline).id
			val start = input.start.toStoryDate()
			val end = input.end.toStoryDate()
			val result = when (input.kind) {
				TimelineKindDto.Event -> invalidInput("Only an entry or a scene is placed; timeline.create adds a plain event")
				TimelineKindDto.Entry -> timeline.placeEntry(input.subject, start, end, input.lane, input.milestone, timeline = target)
				TimelineKindDto.Scene -> timeline.placeScene(input.subject, start, end, input.lane, input.milestone, timeline = target)
			}
			if (result is TimelineResult.Invalid && result.error == TimeLineEventError.SUBJECT_NOT_FOUND) {
				notFound("No ${input.kind.name.lowercase()} ${input.subject}")
			}
			val event = result.requireOk() ?: error("The timeline did not return the placement")
			project.timelineEntry(event.id)
		}
	},
	operation<TimelineSetInput, TimelineEntry>(
		name = "timeline.set",
		description = "Set single fields of any timeline event, or clear them by name, leaving the rest alone. " +
			"A placement takes only label, lane, milestone, start, and end. This is how a placement is edited. " +
			"A timeline other than the event's own moves it there, with start and end, when given, as its dates on " +
			"that timeline's calendar; a dated timeline refuses the move unless the event's start fits or one is given.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		val clear = input.clear.toSet()
		val unknown = clear - CLEARABLE
		if (unknown.isNotEmpty()) {
			invalidInput("clear takes ${CLEARABLE.joinToString()}; not ${unknown.sorted().joinToString()}")
		}
		val given = mapOf(
			"title" to input.title,
			"label" to input.label,
			"lane" to input.lane,
			"start" to input.start,
			"end" to input.end,
		).filterValues { it != null }.keys
		(given intersect clear).firstOrNull()?.let { invalidInput("$it is both set and cleared") }

		projects.withProject(input.project) { project ->
			val found = project.requireEvent(input.id)
			if (found.kind.isPlacement && "title" in given + clear) invalidInput(PLACEMENT_FIELDS)
			val target = input.timeline?.let { project.requireTimeline(it).id }

			val event = if (target == null || target == found.timelineId) {
				found
			} else {
				project.timelineService()
					.moveToTimeline(input.id, target, start = input.start.toStoryDate(), end = input.end.toStoryDate())
					.requireOk() ?: project.requireEvent(input.id)
			}
			val updated = event.copy(
				title = if ("title" in clear) null else input.title.orKeep(event.title),
				label = if ("label" in clear) null else input.label.orKeep(event.label),
				lane = if ("lane" in clear) null else input.lane.orKeep(event.lane),
				milestone = input.milestone ?: event.milestone,
				start = if ("start" in clear) null else input.start.orKeep(event.start),
				end = if ("end" in clear) null else input.end.orKeep(event.end),
			)
			project.timelineService().updateEvent(updated).requireOk()
			project.timelineEntry(input.id)
		}
	},
	operation<ProjectInput, TimelineSettings>(
		name = "timeline.settings.read",
		description = "A project's timelines, main first, each with its id, name, mode, calendar, and lane order, " +
			"and how many events are left on timelines deleted since (see timeline.list orphaned).",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project -> project.timelineSettings() }
	},
	operation<TimelineSettingsWriteInput, TimelineSettings>(
		name = "timeline.settings.write",
		description = "Change one timeline's settings, main unless timeline names another; fields left out stay as " +
			"they are, and name renames it. Switching to dated " +
			"gives every event without a valid start one, spaced evenly in the current order; switching to manual " +
			"keeps the date order as the manual order. A new calendar drops dates that do not fit it, keeping the " +
			"start as the label of an event that has none.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		val name = input.name?.trim()?.also { if (it.isEmpty()) invalidInput("A timeline needs a name") }
		val laneOrder = input.laneOrder?.toLaneOrder()
		val calendar = input.calendar?.toDef()?.also(::requireValidCalendar)

		projects.withProject(input.project) { project ->
			val timeline = project.timelineService()
			val current = project.requireTimeline(input.timeline)
			val id = current.id
			val toDated = input.mode == TimelineModeDto.Dated && current.mode == TimelineMode.MANUAL
			if (input.spacing != null && !toDated) {
				invalidInput("spacing is used only when switching a manual timeline to dated")
			}

			if (toDated) {
				val target = calendar ?: current.calendar ?: invalidInput("A dated timeline needs a calendar")
				timeline.switchToDated(target, input.spacing?.toSpacing(target), timeline = id).requireOk()
			} else {
				if (input.mode == TimelineModeDto.Manual && current.mode == TimelineMode.DATED) {
					timeline.switchToManual(timeline = id).requireOk()
				}
				if (calendar != null && calendar != current.calendar) {
					timeline.switchPreset(calendar, timeline = id).requireOk()
				}
			}

			if (name != null || laneOrder != null) {
				timeline.updateDefinition(id) { def ->
					def.copy(name = name ?: def.name, laneOrder = laneOrder ?: def.laneOrder)
				}.requireOk()
			}
			project.timelineSettings()
		}
	},
	operation<TimelineDefCreateInput, TimelineDefDto>(
		name = "timeline.def.create",
		description = "Add a timeline to a project, manual unless mode says otherwise; a dated one needs a calendar. " +
			"Its name is trimmed and no other timeline may have it, ignoring case. Returns it with its id, which " +
			"the timeline option of other operations takes.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		val mode = input.mode ?: TimelineModeDto.Manual
		val calendar = input.calendar?.toDef()?.also(::requireValidCalendar)
		if (mode == TimelineModeDto.Dated && calendar == null) invalidInput("A dated timeline needs a calendar")
		val laneOrder = input.laneOrder.toLaneOrder()

		projects.withProject(input.project) { project ->
			val timeline = project.timelineService()
			val created = when (val result = timeline.createTimeline(input.name, mode.toMode(), calendar)) {
				is TimelineDefResult.Invalid -> {
					result.error.requireNone()
					error("Timeline error NONE reported as invalid")
				}

				is TimelineDefResult.Ok -> result.def
			}
			if (laneOrder.isNotEmpty()) {
				timeline.updateDefinition(created.id) { it.copy(laneOrder = laneOrder) }.requireOk()
			}
			TimelineDefDto(timeline.definition(created.id) ?: created)
		}
	},
	operation<TimelineDefDeleteInput, TimelineDefDeleted>(
		name = "timeline.def.delete",
		description = "Delete a timeline and every event on it for good; entries and scenes placed there are left " +
			"alone. The main timeline cannot be deleted.",
		access = Access.Destructive,
		scope = OperationScope.Content,
	) { input ->
		val id = timelineId(input.timeline)
		if (id == TimelineDef.MAIN_ID) TimeLineEventError.MAIN_NOT_DELETABLE.requireNone()

		projects.withProject(input.project) { project ->
			when (val result = project.timelineService().deleteTimeline(id)) {
				is TimelineDefResult.Ok -> TimelineDefDeleted(id, result.deletedEvents)
				is TimelineDefResult.Invalid ->
					if (result.error == TimeLineEventError.TIMELINE_NOT_FOUND) {
						notFound("No timeline $id")
					} else {
						result.error.requireNone()
						error("Timeline error NONE reported as invalid")
					}
			}
		}
	},
)

internal fun OpenProject.timelineService(): TimelineService = scope.get<TimelineService>()

/** The id [timeline] names as input: null and "main" are the main timeline. */
internal fun timelineId(timeline: String?): String = timeline?.trim() ?: TimelineDef.MAIN_ID

/** The live timeline [timeline] names as input. */
internal suspend fun OpenProject.requireTimeline(timeline: String?): TimelineDef {
	val id = timelineId(timeline)
	return timelineService().definition(id) ?: notFound("No timeline $id")
}

/** Live timeline [timeline]'s settings and its events in display order. */
internal suspend fun OpenProject.view(timeline: String?): TimelineView =
	timelineService().timelineFlow(requireTimeline(timeline).id).loaded("Timeline")

/** Event [id], on whichever timeline holds it, a deleted one included. */
internal suspend fun OpenProject.requireEvent(id: Int): TimeLineEvent =
	timelineService().event(id) ?: notFound("No timeline event $id")

internal suspend fun OpenProject.timelineEntry(id: Int): TimelineEntry {
	val event = requireEvent(id)
	return TimelineEntry(event, timelineService().displayTitle(event))
}

private suspend fun OpenProject.timelineSettings(): TimelineSettings {
	val timeline = timelineService()
	return TimelineSettings(
		timelines = timeline.timelines.loaded("Timelines").map(::TimelineDefDto),
		orphaned = timeline.orphansFlow.loaded("Timeline").size,
	)
}

private fun List<String>.toLaneOrder(): List<String> {
	val lanes = map { it.trim() }.distinct()
	if (lanes.any { it.isEmpty() || it.length > TimelineLimits.MAX_LANE }) TimeLineEventError.LANE_INVALID.requireNone()
	return lanes
}

private val CLEARABLE = setOf("title", "label", "lane", "start", "end")

private fun requireValidCalendar(calendar: CalendarDef) {
	when (val problem = StoryCalendar.validate(calendar)) {
		null -> Unit
		StoryDateProblem.CoarsestUnitNotSupported -> invalidInput("coarsestUnit applies only to the story calendar")
		is StoryDateProblem.UnknownCoarsestUnit -> invalidInput(
			"coarsestUnit '${problem.unit}' is not one of ${unitNames(calendar.preset)}"
		)

		else -> invalidInput("The calendar is not valid: $problem")
	}
}

private fun EvenSpacingDto.toSpacing(calendar: CalendarDef): EvenSpacing {
	if (step <= 0) invalidInput("spacing.step must be at least 1")
	if (StoryCalendar.units(calendar.preset).none { it.name == unit }) {
		invalidInput("spacing.unit '$unit' is not one of ${unitNames(calendar.preset)}")
	}
	val start = origin.toStoryDate() ?: invalidInput("spacing.origin needs at least one unit")
	if (StoryCalendar.validate(calendar, start) != null) invalidInput("spacing.origin does not fit the calendar")
	return EvenSpacing(unit = unit, step = step, origin = start)
}

private fun unitNames(preset: CalendarPreset): String = StoryCalendar.units(preset).joinToString { it.name }

/** What a timeline entry is. */
@Serializable
enum class TimelineKindDto {
	/** Authored on the timeline, with its own title, text, tags, and references. */
	@SerialName("event") Event,

	/** An encyclopedia entry of type event, placed on the timeline. */
	@SerialName("entry") Entry,

	/** A scene, placed on the timeline. */
	@SerialName("scene") Scene;

	companion object {
		fun of(kind: TimelineKind): TimelineKindDto = when (kind) {
			TimelineKind.EVENT -> Event
			TimelineKind.ENTRY -> Entry
			TimelineKind.SCENE -> Scene
		}
	}
}

@Serializable
data class TimelinePlaceInput(
	val project: String,
	/** entry or scene. */
	val kind: TimelineKindDto,
	/** The entry or scene id. */
	val subject: Int,
	/** When it starts on the timeline's calendar, such as {"year": 97, "day": 210}; required on a dated timeline. */
	val start: Map<String, Long>? = null,
	/** When it ends, in the same form as start; needs a start. */
	val end: Map<String, Long>? = null,
	/** The lane it runs in. */
	val lane: String? = null,
	/** A turning point, marked across every lane. */
	val milestone: Boolean = false,
	/** The timeline's id, from timeline.settings.read; left out, or "main", the main timeline. */
	val timeline: String? = null,
)

/** Fields left out stay as they are. */
@Serializable
data class TimelineSetInput(
	val project: String,
	/** The timeline event. */
	val id: Int,
	/** A plain event's own title. */
	val title: String? = null,
	/** The free-text "when", shown on a manual timeline. */
	val label: String? = null,
	/** The lane it runs in. */
	val lane: String? = null,
	/** A turning point, marked across every lane. */
	val milestone: Boolean? = null,
	/** When it starts on the timeline's calendar, such as {"year": 97, "day": 210}. */
	val start: Map<String, Long>? = null,
	/** When it ends, in the same form as start. */
	val end: Map<String, Long>? = null,
	/** Fields to clear, from title, label, lane, start, and end. */
	val clear: List<String> = emptyList(),
	/** Moves the event to this timeline, by id, "main" for the main one. */
	val timeline: String? = null,
)

/** Whether order is kept by hand or follows dates. */
@Serializable
enum class TimelineModeDto {
	/** Events keep the order they are put in; labels say when. */
	@SerialName("manual") Manual,

	/** Every event has a start date and the order follows it. */
	@SerialName("dated") Dated;

	fun toMode(): TimelineMode = when (this) {
		Manual -> TimelineMode.MANUAL
		Dated -> TimelineMode.DATED
	}

	companion object {
		fun of(mode: TimelineMode): TimelineModeDto = when (mode) {
			TimelineMode.MANUAL -> Manual
			TimelineMode.DATED -> Dated
		}
	}
}

@Serializable
enum class CalendarPresetDto {
	/** year, day, hour, minute from a named epoch; negative years fall before it. */
	@SerialName("story") Story,

	/** Proleptic Gregorian year, month, day, hour, minute; year 0 is 1 BCE. */
	@SerialName("gregorian") Gregorian;

	fun toPreset(): CalendarPreset = when (this) {
		Story -> CalendarPreset.STORY
		Gregorian -> CalendarPreset.GREGORIAN
	}

	companion object {
		fun of(preset: CalendarPreset): CalendarPresetDto = when (preset) {
			CalendarPreset.STORY -> Story
			CalendarPreset.GREGORIAN -> Gregorian
		}
	}
}

@Serializable
data class CalendarDto(
	val preset: CalendarPresetDto,
	/** What year 0 of a story calendar is called, such as "The Fall". */
	val epochName: String? = null,
	/** Story calendar only: the coarsest unit dates use, so a one-day story can run in hours. */
	val coarsestUnit: String? = null,
) {
	constructor(calendar: CalendarDef) : this(
		preset = CalendarPresetDto.of(calendar.preset),
		epochName = calendar.epochName,
		coarsestUnit = calendar.coarsestUnit,
	)

	fun toDef(): CalendarDef = CalendarDef(
		preset = preset.toPreset(),
		epochName = epochName?.trim()?.takeIf { it.isNotEmpty() },
		coarsestUnit = coarsestUnit?.trim()?.takeIf { it.isNotEmpty() },
	)
}

@Serializable
data class TimelineDefDto(
	/** Stable id; the main timeline is "main". */
	val id: String,
	val name: String,
	val mode: TimelineModeDto,
	/** The calendar dates are on; null until one is chosen. */
	val calendar: CalendarDto?,
	/** Lanes in the order they are drawn; lanes in use but not listed follow alphabetically. */
	val laneOrder: List<String>,
) {
	constructor(def: TimelineDef) : this(
		id = def.id,
		name = def.name,
		mode = TimelineModeDto.of(def.mode),
		calendar = def.calendar?.let(::CalendarDto),
		laneOrder = def.laneOrder,
	)
}

@Serializable
data class TimelineSettings(
	/** The project's timelines: main first, then the others in the order they were made. */
	val timelines: List<TimelineDefDto>,
	/** Events whose timeline was deleted, such as one made offline before a sync; timeline.list orphaned lists them. */
	val orphaned: Int = 0,
)

/** Dates for undated events when switching to dated: the n-th event in order is n × step units after origin. */
@Serializable
data class EvenSpacingDto(
	/** The calendar unit to step by, such as "day". */
	val unit: String,
	val step: Long = 1,
	/** The first event's date, such as {"year": 97}. */
	val origin: Map<String, Long>,
)

/** Fields left out stay as they are. */
@Serializable
data class TimelineSettingsWriteInput(
	val project: String,
	/** A new name, which renames the timeline. */
	val name: String? = null,
	/** Switching to dated needs a calendar, here or already set. */
	val mode: TimelineModeDto? = null,
	val calendar: CalendarDto? = null,
	/** Lanes in the order they are drawn. */
	val laneOrder: List<String>? = null,
	/** Only when switching to dated; left out, events are one of the calendar's coarsest unit apart. */
	val spacing: EvenSpacingDto? = null,
	/** The timeline's id; left out, or "main", the main timeline. */
	val timeline: String? = null,
)

@Serializable
data class TimelineDefCreateInput(
	val project: String,
	val name: String,
	/** Left out, manual. */
	val mode: TimelineModeDto? = null,
	/** Required for a dated timeline. */
	val calendar: CalendarDto? = null,
	/** Lanes in the order they are drawn. */
	val laneOrder: List<String> = emptyList(),
)

@Serializable
data class TimelineDefDeleteInput(
	val project: String,
	/** The timeline's id; main cannot be deleted. */
	val timeline: String,
)

@Serializable
data class TimelineDefDeleted(
	/** The deleted timeline's id. */
	val timeline: String,
	/** How many events were on it, deleted with it. */
	val deletedEvents: Int,
)
