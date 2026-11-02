package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError

interface CreateTimeLineEvent : TagSuggesting {
	val state: Value<State>
	val contentText: Value<String>

	/**
	 * Creates the drafted event, or places the drafted subject, with [label] as its "when".
	 * [content] and [tags] only go on a plain event. On a DATED timeline the draft's start is
	 * required and its start and end are stored; on a MANUAL one they are left off.
	 */
	suspend fun create(
		label: String?,
		content: String,
		tags: Set<String> = emptySet(),
	): TimeLineEventError

	/** Changing kind drops a subject picked for the old one. */
	fun setKind(kind: TimelineKind)
	fun setSubject(item: CatalogItem?)
	fun setLane(lane: String)
	fun setMilestone(milestone: Boolean)
	fun setTitle(title: String)

	/** Null while the date field is empty or invalid. */
	fun setStart(start: StoryDate?)
	fun setEnd(end: StoryDate?)
	fun addReference(item: CatalogItem)
	fun removeReference(id: Int)

	/** Lanes in use that start with, then contain, [prefix], leaving out an exact match. */
	fun suggestLanes(prefix: String): List<String>

	fun closeCreation()

	/** Drops the whole draft, then closes. */
	fun discardAndClose()
	fun confirmDiscard()
	fun cancelDiscard()
	fun onContentChanged(newText: String)
	fun clearContent()

	data class State(
		val projectDef: ProjectDef,
		val kind: TimelineKind = TimelineKind.EVENT,
		val subject: CatalogItem? = null,
		val title: String = "",
		val lane: String = "",
		val milestone: Boolean = false,
		val references: List<CatalogItem> = emptyList(),
		val start: StoryDate? = null,
		val end: StoryDate? = null,
		val mode: TimelineMode = TimelineMode.MANUAL,
		/** The timeline's calendar; null until a timeline has had one. */
		val calendar: CalendarDef? = null,
		/** Scenes and entries, to pick a subject or references from. */
		val catalog: List<CatalogItem> = emptyList(),
		/** Every lane in use, in lane order; a lane's position is its colour index. */
		val lanes: List<String> = emptyList(),
		val confirmDiscard: Boolean = false,
		val spellCheckAllowed: Boolean = true,
	) {
		val isDated: Boolean get() = mode == TimelineMode.DATED

		/** A placement needs its subject, and a dated timeline a start. */
		val canCreate: Boolean
			get() = (kind == TimelineKind.EVENT || subject != null) && (!isDated || start != null)
	}
}
