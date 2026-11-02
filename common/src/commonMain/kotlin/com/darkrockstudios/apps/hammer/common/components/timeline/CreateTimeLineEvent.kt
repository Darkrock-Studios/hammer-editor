package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError

interface CreateTimeLineEvent : TagSuggesting {
	val state: Value<State>
	val contentText: Value<String>

	/**
	 * Creates the drafted event, or places the drafted subject, with [label] as its "when".
	 * [content] and [tags] only go on a plain event.
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
		/** Scenes and entries, to pick a subject or references from. */
		val catalog: List<CatalogItem> = emptyList(),
		/** Every lane in use, in lane order; a lane's position is its colour index. */
		val lanes: List<String> = emptyList(),
		val confirmDiscard: Boolean = false,
		val spellCheckAllowed: Boolean = true,
	)
}
