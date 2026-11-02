package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError

interface ViewTimeLineEvent : TagSuggesting {
	val eventId: Int
	val state: Value<State>

	fun onEventTextChanged(text: String)
	fun onDateTextChanged(text: String)
	fun onTagsChanged(newTags: Set<String>)
	fun onTitleChanged(text: String)
	fun onLaneChanged(text: String)
	fun onMilestoneChanged(milestone: Boolean)
	fun addReference(item: CatalogItem)
	fun removeReference(id: Int)

	/** Lanes in use that start with, then contain, [prefix], leaving out an exact match. */
	fun suggestLanes(prefix: String): List<String>

	suspend fun removeTag(tag: String)
	fun showGlobalSearchForTag(tag: String)

	/** Stores the edit in progress and leaves editing; a placement stores only its placement fields and "when". */
	suspend fun saveEdit(): TimeLineEventError
	fun startDeleteEvent()
	fun endDeleteEvent()
	suspend fun deleteEvent()

	/** Opens the placed scene or entry in its own tab. */
	fun openSubject()

	/** Opens a referenced scene or entry in its own tab. */
	fun openReference(item: CatalogItem)

	fun confirmDiscard()
	fun cancelDiscard()

	data class State(
		val event: TimeLineEvent? = null,
		val tags: Set<String> = emptySet(),
		val menuItems: Set<MenuItemDescriptor> = emptySet(),
		val confirmDelete: Boolean = false,
		val confirmClose: Boolean = false,
		val confirmDiscard: Boolean = false,
		val isEditing: Boolean = false,
		val spellCheckAllowed: Boolean = true,
		val kind: TimelineKind = TimelineKind.EVENT,
		/** The subject's name for a placement, else the event's own title or first line. */
		val displayTitle: String = "",
		/** Null for a plain event. */
		val subject: SubjectSummary? = null,
		/** A placed entry's primary image. */
		val subjectImage: SubjectImage? = null,
		val title: String = "",
		val lane: String = "",
		val milestone: Boolean = false,
		val referenceIds: Set<Int> = emptySet(),
		/** An event's references (the draft's while editing), or a placed scene's cast. */
		val references: List<CatalogItem> = emptyList(),
		/** Position of the stored lane in [lanes]; null when it has none. */
		val laneIndex: Int? = null,
		/** Every lane in use, in lane order; a lane's position is its colour index. */
		val lanes: List<String> = emptyList(),
		/** Scenes and entries to add as references; only loaded for a plain event. */
		val catalog: List<CatalogItem> = emptyList(),
	)

	/** [version] changes whenever the image's bytes do. */
	data class SubjectImage(val name: String, val path: String, val version: String?)

	val dateText: Value<String>
	val contentText: Value<String>
	fun isEditingAndDirty(): Boolean
	fun discardEdit()
	fun beginEdit()
	fun confirmClose()
	fun cancelClose()
	fun closeEvent()

	/** Opens the gallery to pick an image to embed in the text; null when the pick was cancelled. */
	suspend fun pickImageForText(): ImageDef?
}
