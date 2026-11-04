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
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineView
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
		description = "Place an encyclopedia entry of type event, or a scene, on a project's timeline. Each can be " +
			"placed once, and its name and words come from it. A dated timeline needs a start.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project ->
			val timeline = project.timelineService()
			val start = input.start.toStoryDate()
			val end = input.end.toStoryDate()
			val result = when (input.kind) {
				TimelineKindDto.Event -> invalidInput("Only an entry or a scene is placed; timeline.create adds a plain event")
				TimelineKindDto.Entry -> timeline.placeEntry(input.subject, start, end, input.lane, input.milestone)
				TimelineKindDto.Scene -> timeline.placeScene(input.subject, start, end, input.lane, input.milestone)
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
			"A placement takes only label, lane, milestone, start, and end. This is how a placement is edited.",
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
			val event = project.requireEvent(input.id)
			if (event.kind.isPlacement && "title" in given + clear) invalidInput(PLACEMENT_FIELDS)

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
		description = "A project's timeline settings: name, mode, calendar, and lane order.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		projects.withProject(input.project) { project -> project.timelineSettings() }
	},
	operation<TimelineSettingsWriteInput, TimelineSettings>(
		name = "timeline.settings.write",
		description = "Change a project's timeline settings; fields left out stay as they are. Switching to dated " +
			"gives every event without a valid start one, spaced evenly in the current order; switching to manual " +
			"keeps the date order as the manual order. A new calendar drops dates that do not fit it, keeping the " +
			"start as the label of an event that has none.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		val name = input.name?.trim()?.also { if (it.isEmpty()) invalidInput("A timeline needs a name") }
		val laneOrder = input.laneOrder?.map { it.trim() }?.distinct()
		if (laneOrder != null && laneOrder.any { it.isEmpty() || it.length > TimelineLimits.MAX_LANE }) {
			TimeLineEventError.LANE_INVALID.requireNone()
		}
		val calendar = input.calendar?.toDef()?.also(::requireValidCalendar)

		projects.withProject(input.project) { project ->
			val timeline = project.timelineService()
			val current = timeline.definition() ?: notFound("No such timeline")
			val toDated = input.mode == TimelineModeDto.Dated && current.mode == TimelineMode.MANUAL
			if (input.spacing != null && !toDated) {
				invalidInput("spacing is used only when switching a manual timeline to dated")
			}

			if (toDated) {
				val target = calendar ?: current.calendar ?: invalidInput("A dated timeline needs a calendar")
				timeline.switchToDated(target, input.spacing?.toSpacing(target)).requireOk()
			} else {
				if (input.mode == TimelineModeDto.Manual && current.mode == TimelineMode.DATED) {
					timeline.switchToManual().requireOk()
				}
				if (calendar != null && calendar != current.calendar) timeline.switchPreset(calendar).requireOk()
			}

			if (name != null || laneOrder != null) {
				timeline.updateDefinition { def ->
					def.copy(name = name ?: def.name, laneOrder = laneOrder ?: def.laneOrder)
				}.requireOk()
			}
			project.timelineSettings()
		}
	},
)

internal fun OpenProject.timelineService(): TimelineService = scope.get<TimelineService>()

/** The main timeline's settings and its events in display order. */
internal suspend fun TimelineService.view(): TimelineView = timelineFlow(TimelineDef.MAIN_ID).loaded("Timeline")

internal suspend fun OpenProject.requireEvent(id: Int): TimeLineEvent =
	timelineService().view().events.find { it.id == id } ?: notFound("No timeline event $id")

internal suspend fun OpenProject.timelineEntry(id: Int): TimelineEntry {
	val event = requireEvent(id)
	return TimelineEntry(event, timelineService().displayTitle(event))
}

private suspend fun OpenProject.timelineSettings(): TimelineSettings =
	TimelineSettings(listOf(TimelineDefDto(timelineService().definition() ?: TimelineDef.DEFAULT)))

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
)

/** Whether order is kept by hand or follows dates. */
@Serializable
enum class TimelineModeDto {
	/** Events keep the order they are put in; labels say when. */
	@SerialName("manual") Manual,

	/** Every event has a start date and the order follows it. */
	@SerialName("dated") Dated;

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
	/** The project's timelines; for now always one, the main timeline. */
	val timelines: List<TimelineDefDto>,
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
	val name: String? = null,
	/** Switching to dated needs a calendar, here or already set. */
	val mode: TimelineModeDto? = null,
	val calendar: CalendarDto? = null,
	/** Lanes in the order they are drawn. */
	val laneOrder: List<String>? = null,
	/** Only when switching to dated; left out, events are one of the calendar's coarsest unit apart. */
	val spacing: EvenSpacingDto? = null,
)
