package com.darkrockstudios.apps.hammer.common.preview.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.LaneCount
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary

internal val previewLanes = listOf("Keeper", "Village", "The Sea")

internal val previewLaneCounts = listOf(
	LaneCount("Keeper", 2),
	LaneCount("Village", 1),
	LaneCount("The Sea", 1),
)

internal val previewCatalog = listOf(
	CatalogItem(CatalogKind.Scene, 11, "The Keeper Arrives", detail = "Part One › Spring"),
	CatalogItem(CatalogKind.Scene, 12, "Lamp Out", detail = "Part One › Summer"),
	CatalogItem(CatalogKind.Entry, 21, "Maren", entryType = EntryType.PERSON),
	CatalogItem(CatalogKind.Entry, 22, "Old Tom", entryType = EntryType.PERSON),
	CatalogItem(CatalogKind.Entry, 23, "The Lighthouse", entryType = EntryType.PLACE),
	CatalogItem(CatalogKind.Entry, 24, "The Wreck of the Alba", entryType = EntryType.EVENT),
)

internal val previewRows = listOf(
	TimelineRow(
		event = TimeLineEvent(
			id = 1,
			order = 0,
			kind = TimelineKind.SCENE,
			subject = 11,
			label = "Spring, Year One",
			lane = "Keeper",
		),
		title = "The Keeper Arrives",
		subtitle = "Part One › Spring",
		whenText = "Spring, Year One",
		laneIndex = 0,
	),
	TimelineRow(
		event = TimeLineEvent(
			id = 2,
			order = 1,
			title = "The lamp goes dark",
			label = "Midsummer",
			content = "The lamp goes dark for the first time and a ship runs aground.",
			tags = setOf("act-one", "turning-point"),
			lane = "The Sea",
			milestone = true,
		),
		title = "The lamp goes dark",
		subtitle = null,
		whenText = "Midsummer",
		laneIndex = 2,
	),
	TimelineRow(
		event = TimeLineEvent(id = 3, order = 2, kind = TimelineKind.ENTRY, subject = 24, lane = "Village"),
		title = "The Wreck of the Alba",
		subtitle = null,
		whenText = "",
		laneIndex = 1,
	),
	TimelineRow(
		event = TimeLineEvent(
			id = 4,
			order = 3,
			content = "An undated rumour spreads through the harbour town.",
		),
		title = "An undated rumour spreads through the harbour town.",
		subtitle = null,
		whenText = "",
		laneIndex = null,
	),
	TimelineRow(
		event = TimeLineEvent(
			id = 5,
			order = 4,
			kind = TimelineKind.SCENE,
			subject = 99,
			cachedTitle = "A Scene Since Lost",
			lane = "Keeper",
		),
		title = "A Scene Since Lost",
		subtitle = null,
		whenText = "Autumn",
		laneIndex = 0,
		subjectMissing = true,
	),
)

internal val previewCalendar = CalendarDef(epochName = "The Landing")

private fun yearDay(year: Long, day: Long) = StoryDate(linkedMapOf("year" to year, "day" to day))

/** [previewRows] placed on [previewCalendar]; the rumour stays undated. */
internal val previewDatedRows = previewRows.map { row ->
	val (start, end) = when (row.event.id) {
		1 -> yearDay(1, 12) to null
		2 -> yearDay(1, 180) to null
		3 -> yearDay(1, 182) to yearDay(1, 200)
		5 -> yearDay(1, 250) to yearDay(1, 262)
		else -> return@map row
	}
	row.copy(start = start, end = end, calendar = previewCalendar)
}

internal val previewMainOnly = listOf(TimelineSummary(TimelineDef.DEFAULT, previewRows.size))

/** Main, a dated backstory, and a long-named scratch timeline that ellipsizes in a phone's header. */
internal val previewThreeTimelines = listOf(
	TimelineSummary(TimelineDef.DEFAULT, previewRows.size),
	TimelineSummary(
		TimelineDef(id = "backstory", name = "Before the evening", mode = TimelineMode.DATED, calendar = previewCalendar),
		13,
	),
	TimelineSummary(TimelineDef(id = "scratch", name = "Scratch: what the keeper remembers of the wreck"), 2),
)
