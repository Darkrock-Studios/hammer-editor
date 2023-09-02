package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef

interface ViewEntry {

	val state: Value<State>

	data class State(
		val entryDef: EntryDef,
		val entryImagePath: String? = null,
		val content: EntryContent? = null,
		val showAddImageDialog: Boolean = false,
		val showDeleteImageDialog: Boolean = false,
		val showDeleteEntryDialog: Boolean = false,
		val editText: Boolean = false,
		val editName: Boolean = false,
		val confirmClose: Boolean = false,
		val showTagAdd: Boolean = false,
		val menuItems: Set<MenuItemDescriptor> = emptySet(),
	)

	fun getImagePath(entryDef: EntryDef): String?
	suspend fun loadEntryContent(entryDef: EntryDef): EntryContent
	suspend fun deleteEntry(entryDef: EntryDef): Boolean
	suspend fun updateEntry(name: String, text: String, tags: Set<String>): EntryResult
	suspend fun removeEntryImage(): Boolean
	suspend fun setImage(path: String)

	fun showDeleteEntryDialog()
	fun closeDeleteEntryDialog()
	fun showDeleteImageDialog()
	fun closeDeleteImageDialog()
	fun showAddImageDialog()
	fun closeAddImageDialog()

	fun startNameEdit()
	fun startTextEdit()
	fun finishNameEdit()
	fun finishTextEdit()
	fun confirmClose()
	fun dismissConfirmClose()
	fun removeTag(tag: String)
	fun startTagAdd()
	suspend fun addTags(tagInput: String)
	fun endTagAdd()
}