package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting

interface ViewEntry : TagSuggesting {

	val state: Value<State>
	val renameSlot: Value<ChildSlot<*, RenameEntry>>

	data class State(
		val entryDef: EntryDef,
		/** The entry's images that exist in the project, primary first. */
		val images: List<EntryImage> = emptyList(),
		/** The index in [images] of the image on show. */
		val currentImageIndex: Int = 0,
		val content: EntryContent? = null,
		val showAddImageDialog: Boolean = false,
		/** The gallery is open to pick an image from the project for this entry. */
		val pendingLibraryPick: Boolean = false,
		/** The image waiting on confirmation before it is removed from the entry. */
		val removeImageConfirm: Int? = null,
		val showDeleteEntryDialog: Boolean = false,
		val editText: Boolean = false,
		val editName: Boolean = false,
		val confirmClose: Boolean = false,
		val showTagAdd: Boolean = false,
		val showAliasAdd: Boolean = false,
		val menuItems: Set<MenuItemDescriptor> = emptySet(),
		val appearsIn: List<Appearance> = emptyList(),
		val spellCheckAllowed: Boolean = true,
		val dictionaryFeatureEnabled: Boolean = true,
	)

	/** [hash] changes whenever the image's bytes do, so it keys image caches. */
	data class EntryImage(
		val id: Int,
		val name: String,
		val path: String,
		val hash: String?,
	)

	data class Appearance(
		val name: String,
		val sceneItem: SceneItem,
		val source: AppearanceSource = AppearanceSource.Scene,
	)

	enum class AppearanceSource {
		Scene,
	}

	suspend fun loadEntryContent(entryDef: EntryDef): EntryContent
	suspend fun deleteEntry(entryDef: EntryDef): Boolean
	suspend fun updateEntry(name: String, text: String, tags: Set<String>): EntryResult

	/** Imports the file at [path] as a new Image and adds it after the entry's other images. */
	suspend fun importImage(path: String): ImageAddResult

	/** Opens the image gallery to pick an image already in the project, and adds it after the entry's other images. */
	fun addImageFromLibrary()

	/** Detaches image [id] from the entry; the Image stays in the project. */
	suspend fun removeImage(id: Int): Boolean
	suspend fun setPrimaryImage(id: Int): Boolean
	fun showImageAt(index: Int)

	fun showDeleteEntryDialog()
	fun closeDeleteEntryDialog()
	fun showRemoveImageConfirm(id: Int)
	fun dismissRemoveImageConfirm()
	fun showAddImageDialog()
	fun closeAddImageDialog()

	fun startNameEdit()
	fun startTextEdit()
	fun finishNameEdit()
	fun finishTextEdit()

	/** Leaves name and text edit mode without saving; the UI reseeds its fields from [State.content]. */
	fun discardEdit()
	fun confirmClose()
	fun dismissConfirmClose()
	fun removeTag(tag: String)
	fun showGlobalSearchForTag(tag: String)
	fun startTagAdd()
	suspend fun addTags(tagInput: String)
	fun endTagAdd()
	fun startAliasAdd()
	fun endAliasAdd()
	suspend fun addAlias(alias: String): EntryResult
	fun removeAlias(alias: String)
	suspend fun setExcludeFromDictionary(exclude: Boolean): EntryResult
	fun navigateToAppearance(appearance: Appearance)
	fun showRename()
	fun dismissRename()
}