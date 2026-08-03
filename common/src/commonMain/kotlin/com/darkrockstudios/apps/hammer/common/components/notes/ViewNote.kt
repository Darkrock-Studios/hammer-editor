package com.darkrockstudios.apps.hammer.common.components.notes

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting

interface ViewNote : TagSuggesting {
	val state: Value<State>
	val noteText: Value<String>

	data class State(
		val projectDef: ProjectDef,
		val note: NoteContent? = null,
		val tags: Set<String> = emptySet(),
		val confirmDiscard: Boolean = false,
		val confirmClose: Boolean = false,
		val confirmDelete: Boolean = false,
		val isEditing: Boolean = false,
		val menuItems: Set<MenuItemDescriptor> = emptySet(),
		val spellCheckAllowed: Boolean = true,
	)

	fun discardEdit()
	fun onContentChanged(newContent: String)
	fun onTagsChanged(newTags: Set<String>)
	suspend fun removeTag(tag: String)
	fun showGlobalSearchForTag(tag: String)
	suspend fun deleteNote(id: Int)
	fun confirmDelete()
	fun dismissConfirmDelete()
	suspend fun storeNoteUpdate()
	fun closeNote()
	fun beginEdit()
	fun isEditingAndDirty(): Boolean
	fun confirmDiscard()
	fun cancelDiscard()
	fun confirmClose()
	fun cancelClose()
}