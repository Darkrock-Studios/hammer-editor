package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewEntryComponent(
	componentContext: ComponentContext,
	entryDef: EntryDef,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val closeEntry: () -> Unit
) : ProjectComponentBase(entryDef.projectDef, componentContext), ViewEntry {

	private val _state = MutableValue(
		ViewEntry.State(
			entryDef = entryDef
		)
	)
	override val state: Value<ViewEntry.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	private val backButtonHandler = BackCallback {
		if (state.value.editText || state.value.editText) {
			confirmClose()
		} else {
			closeEntry()
		}
	}

	init {
		backHandler.register(backButtonHandler)
	}

	override fun onCreate() {
		super.onCreate()
		reload()
	}

	private fun reload() {
		scope.launch {
			val entryImagePath = getImagePath(state.value.entryDef)
			val content = loadEntryContent(state.value.entryDef)
			withContext(dispatcherMain) {
				_state.getAndUpdate {
					it.copy(
						entryImagePath = entryImagePath,
						content = content
					)
				}
			}
		}
	}

	override fun getImagePath(entryDef: EntryDef): String? {
		return if (encyclopediaRepository.hasEntryImage(entryDef, "jpg")) {
			encyclopediaRepository.getEntryImagePath(entryDef, "jpg").path
		} else {
			null
		}
	}

	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent {
		val container = encyclopediaRepository.loadEntry(entryDef)
		return container.entry
	}

	override suspend fun deleteEntry(entryDef: EntryDef): Boolean {
		encyclopediaRepository.deleteEntry(entryDef)
		return true
	}

	override suspend fun removeEntryImage(): Boolean {
		if (encyclopediaRepository.removeEntryImage(state.value.entryDef)) {
			reload()
		}
		return true
	}

	override suspend fun setImage(path: String) {
		encyclopediaRepository.setEntryImage(state.value.entryDef, path)
		reload()
	}

	override fun showDeleteEntryDialog() {
		_state.getAndUpdate {
			it.copy(showDeleteEntryDialog = true)
		}
	}

	override fun closeDeleteEntryDialog() {
		_state.getAndUpdate {
			it.copy(showDeleteEntryDialog = false)
		}
	}

	override fun showDeleteImageDialog() {
		_state.getAndUpdate {
			it.copy(showDeleteImageDialog = true)
		}
	}

	override fun closeDeleteImageDialog() {
		_state.getAndUpdate {
			it.copy(showDeleteImageDialog = false)
		}
	}

	override fun showAddImageDialog() {
		_state.getAndUpdate {
			it.copy(showAddImageDialog = true)
		}
	}

	override fun closeAddImageDialog() {
		_state.getAndUpdate {
			it.copy(showAddImageDialog = false)
		}
	}

	override fun startNameEdit() {
		_state.getAndUpdate {
			it.copy(editName = true)
		}
	}

	override fun startTextEdit() {
		_state.getAndUpdate {
			it.copy(editText = true)
		}
	}

	override fun finishNameEdit() {
		_state.getAndUpdate {
			it.copy(editName = false)
		}
	}

	override fun finishTextEdit() {
		_state.getAndUpdate {
			it.copy(editText = false)
		}
	}

	override suspend fun updateEntry(
		name: String,
		text: String,
		tags: Set<String>
	): EntryResult {
		val result = encyclopediaRepository.updateEntry(state.value.entryDef, name, text, tags)
		if (result.instance != null && result.error == EntryError.NONE) {
			_state.getAndUpdate {
				it.copy(
					entryDef = result.instance.toDef(projectDef)
				)
			}

			reload()
		}

		return result
	}

	override fun confirmClose() {
		_state.getAndUpdate {
			it.copy(
				confirmClose = true
			)
		}
	}

	override fun dismissConfirmClose() {
		_state.getAndUpdate {
			it.copy(
				confirmClose = false
			)
		}
	}

	override fun removeTag(tag: String) {
		scope.launch {
			state.value.content?.apply {
				val newTags = tags.toMutableSet()
				newTags.remove(tag)

				encyclopediaRepository.updateEntry(
					state.value.entryDef,
					name,
					text,
					newTags
				)

				reload()
			}
		}
	}

	override fun startTagAdd() {
		_state.getAndUpdate {
			it.copy(
				showTagAdd = true
			)
		}
	}

	override fun endTagAdd() {
		_state.getAndUpdate {
			it.copy(
				showTagAdd = false
			)
		}
	}

	override suspend fun addTags(tagInput: String) {
		val newTags = tagInput.splitToSequence(" ")
			.filter { it.isNotBlank() }
			.toSet()

		state.value.content?.apply {
			encyclopediaRepository.updateEntry(
				oldEntryDef = state.value.entryDef,
				name = name,
				text = text,
				tags = tags + newTags,
			)
		}

		endTagAdd()
		reload()
	}

	private fun getMenuId(): String {
		return "view-entry"
	}

	private fun addEntryMenu() {

		val addImage = MenuItemDescriptor(
			"view-entry-add-image",
			MR.strings.encyclopedia_entry_menu_add_image,
			"",
		) {
			_state.getAndUpdate { it.copy(showAddImageDialog = true) }
		}

		val removeImage = MenuItemDescriptor(
			"view-entry-remove-image",
			MR.strings.encyclopedia_entry_menu_remove_image,
			"",
		) {
			_state.getAndUpdate { it.copy(showDeleteImageDialog = true) }
		}

		val deleteEntry = MenuItemDescriptor(
			"view-entry-delete",
			MR.strings.encyclopedia_entry_menu_delete,
			"",
		) {
			_state.getAndUpdate { it.copy(showDeleteEntryDialog = true) }
		}

		val menuItems = setOf(addImage, removeImage, deleteEntry)
		val menu = MenuDescriptor(
			getMenuId(),
			MR.strings.encyclopedia_entry_menu_group,
			menuItems.toList()
		)
		addMenu(menu)
		_state.getAndUpdate {
			it.copy(
				menuItems = menuItems
			)
		}
	}

	private fun removeEntryMenu() {
		removeMenu(getMenuId())
		_state.getAndUpdate {
			it.copy(
				menuItems = emptySet()
			)
		}
	}

	override fun onStart() {
		addEntryMenu()
	}

	override fun onStop() {
		removeEntryMenu()
	}
}