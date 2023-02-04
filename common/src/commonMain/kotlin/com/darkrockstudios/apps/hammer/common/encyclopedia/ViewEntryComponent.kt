package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.projectInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewEntryComponent(
	componentContext: ComponentContext,
	entryDef: EntryDef,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(entryDef.projectDef, componentContext), ViewEntry {

	private val _state = MutableValue(ViewEntry.State(entryDef = entryDef))
	override val state: Value<ViewEntry.State> = _state

	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	init {
		reload()
	}

	private fun reload() {
		scope.launch {
			val entryImagePath = getImagePath(state.value.entryDef)
			val content = loadEntryContent(state.value.entryDef)
            withContext(dispatcherMain) {
                _state.reduce {
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
		_state.reduce {
			it.copy(showDeleteEntryDialog = true)
		}
	}

	override fun closeDeleteEntryDialog() {
		_state.reduce {
			it.copy(showDeleteEntryDialog = false)
		}
	}

	override fun showDeleteImageDialog() {
		_state.reduce {
			it.copy(showDeleteImageDialog = true)
		}
	}

	override fun closeDeleteImageDialog() {
		_state.reduce {
			it.copy(showDeleteImageDialog = false)
		}
	}

	override fun showAddImageDialog() {
		_state.reduce {
			it.copy(showAddImageDialog = true)
		}
	}

	override fun closeAddImageDialog() {
		_state.reduce {
			it.copy(showAddImageDialog = false)
		}
	}

	override suspend fun updateEntry(
		name: String,
		text: String,
		tags: List<String>
	): EntryResult {
		val result = encyclopediaRepository.updateEntry(state.value.entryDef, name, text, tags)
		if (result.instance != null && result.error == EntryError.NONE) {
			_state.reduce {
				it.copy(
					entryDef = result.instance.toDef(projectDef)
				)
			}

			reload()
		}

		return result
	}

	private fun getMenuId(): String {
		return "view-entry"
	}

	private fun addEntryMenu() {

		val addImage = MenuItemDescriptor(
			"view-entry-add-image",
			"Add Image",
			"",
		) {
			_state.reduce { it.copy(showAddImageDialog = true) }
		}

		val removeImage = MenuItemDescriptor(
			"view-entry-remove-image",
			"Remove Image",
			"",
		) {
			_state.reduce { it.copy(showDeleteImageDialog = true) }
		}

		val deleteEntry = MenuItemDescriptor(
			"view-entry-delete",
			"Delete Entry",
			"",
		) {
			_state.reduce { it.copy(showDeleteEntryDialog = true) }
		}

		val menu = MenuDescriptor(
			getMenuId(),
			"Entry",
			listOf(addImage, removeImage, deleteEntry)
		)
		addMenu(menu)
	}

	private fun removeEntryMenu() {
		removeMenu(getMenuId())
	}

	override fun onStart() {
		addEntryMenu()
	}

	override fun onStop() {
		removeEntryMenu()
	}
}