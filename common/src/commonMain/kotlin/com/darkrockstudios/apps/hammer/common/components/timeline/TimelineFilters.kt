package com.darkrockstudios.apps.hammer.common.components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.search.markdownContains
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

/** An event matches on its body read as prose, or on its plain-text date. */
fun eventMatchesQuery(event: TimeLineEvent, query: String): Boolean =
	markdownContains(event.content, query) ||
		event.label?.contains(query, ignoreCase = true) == true

/** A row also matches on the title it shows, which for a placement is its subject's name. */
fun rowMatchesQuery(row: TimelineRow, query: String): Boolean =
	eventMatchesQuery(row.event, query) || row.title.contains(query, ignoreCase = true)

/** What the overview's search, tag filter and lane filter leave of a timeline's rows. */
data class FilteredRows(
	/**
	 * The rows the list shows, in display order: those with every filtered tag, in a filtered lane, and
	 * on a MANUAL timeline matching the search.
	 */
	val listRows: List<TimelineRow> = emptyList(),
	/**
	 * On a DATED timeline, the rows the drawing is given: those with every filtered tag. The drawing
	 * applies the lane filter itself and rings [searchMatches] instead of dropping the rest.
	 */
	val drawingRows: List<TimelineRow> = emptyList(),
	/** On a DATED timeline, the drawn [drawingRows] that pass the lane filter and match the search, in display order. */
	val searchMatches: List<Int> = emptyList(),
	/**
	 * Whether the list can be reordered by dragging. Reordering maps display indices to stored order,
	 * which only holds on a MANUAL timeline with nothing searched or filtered out.
	 */
	val reorderable: Boolean = false,
)

/** A blank [query] searches nothing; any other is matched trimmed. */
fun filterRows(
	rows: List<TimelineRow>,
	mode: TimelineMode,
	query: String,
	tagFilter: Set<String>,
	laneFilter: Set<String>,
): FilteredRows {
	val dated = mode == TimelineMode.DATED
	val search = query.trim().takeIf { it.isNotEmpty() }
	val tagged = if (tagFilter.isEmpty()) rows else rows.filter { row -> tagFilter.all { it in row.event.tags } }
	val searched = if (dated || search == null) tagged else tagged.filter { rowMatchesQuery(it, search) }
	val listRows = if (laneFilter.isEmpty()) searched else searched.filter { it.event.lane in laneFilter }
	val matches = if (dated && search != null) {
		tagged.filter { row -> row.isDrawn && row.passesLaneFilter(laneFilter) && rowMatchesQuery(row, search) }
			.map { it.event.id }
	} else {
		emptyList()
	}
	return FilteredRows(
		listRows = listRows,
		drawingRows = if (dated) tagged else emptyList(),
		searchMatches = matches,
		reorderable = !dated && search == null && tagFilter.isEmpty() && laneFilter.isEmpty(),
	)
}

/** This state with [TimeLineOverview.State.filtered] worked out again from its rows, mode, search and filters. */
fun TimeLineOverview.State.refiltered(): TimeLineOverview.State =
	copy(filtered = filterRows(rows.orEmpty(), mode, searchQuery, tagFilter, laneFilter))
