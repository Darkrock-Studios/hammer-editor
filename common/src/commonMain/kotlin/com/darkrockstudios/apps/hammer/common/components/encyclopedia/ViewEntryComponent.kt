package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.CleanupReferencesOnEntryDeleteUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tagindex.parseTagInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewEntryComponent(
	componentContext: ComponentContext,
	entryDef: EntryDef,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
	private val removeMenu: (id: String) -> Unit,
	private val closeEntry: () -> Unit,
	private val showScene: (SceneItem) -> Unit,
	private val onShowGlobalSearchForTag: (String) -> Unit,
) : ProjectComponentBase(entryDef.projectDef, componentContext), ViewEntry {

	private val _state = MutableValue(
		ViewEntry.State(
			entryDef = entryDef
		)
	)
	override val state: Value<ViewEntry.State> = _state

	private val encyclopediaService: EncyclopediaService by projectInject()
	private val referenceIndexService: ReferenceIndexService by projectInject()
	private val sceneEditorRepository: SceneEditorService by projectInject()
	private val backfillEntryReferences: BackfillEntryReferencesUseCase by projectInject()
	private val cleanupReferencesOnDelete: CleanupReferencesOnEntryDeleteUseCase by projectInject()

	private val backButtonHandler = BackCallback(isEnabled = false) {
		// Only called when editing - show confirmation before discarding
		confirmClose()
	}

	override fun onCreate() {
		super.onCreate()
		backHandler.register(backButtonHandler)

		// Enable back handler only when editing
		state.subscribe(lifecycle) {
			backButtonHandler.isEnabled = it.editName || it.editText
		}

		watchSpellCheckAllowed { allowed ->
			_state.getAndUpdate { it.copy(spellCheckAllowed = allowed) }
		}

		watchEncyclopediaDictionaryEnabled { enabled ->
			_state.getAndUpdate { it.copy(dictionaryFeatureEnabled = enabled) }
		}

		// Trigger an initial recompute so the index reflects current data
		scope.launch { referenceIndexService.loadIndex() }

		// Subscribe to "appears in" updates from the reference index
		scope.launch {
			referenceIndexService.flowForEntry(state.value.entryDef.id).collect { sceneIds ->
				val resolved = sceneIds
					.mapNotNull { id ->
						sceneEditorRepository.getSceneItemFromIdIncludingArchived(id)?.let { item ->
							ViewEntry.Appearance(
								name = item.name,
								sceneItem = item,
								source = ViewEntry.AppearanceSource.Scene,
							)
						}
					}
					.sortedBy { it.name.lowercase() }
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(appearsIn = resolved) }
				}
			}
		}

		reload()
	}

	private fun reload() {
		scope.launch {
			val entryDef = state.value.entryDef
			val entryImagePath = getImagePath(entryDef)
			val imageHash = encyclopediaService.findEntryImageExtension(entryDef)
				?.let { ext -> encyclopediaService.calculateEntryImageHash(entryDef, ext) }

			val content = loadEntryContent(state.value.entryDef)
			withContext(dispatcherMain) {
				_state.getAndUpdate {
					it.copy(
						entryImagePath = entryImagePath,
						entryImageHash = imageHash,
						content = content
					)
				}
			}
		}
	}

	override fun getImagePath(entryDef: EntryDef): String? {
		return encyclopediaService.findEntryImagePath(entryDef)?.path
	}

	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent {
		val container = encyclopediaService.loadEntry(entryDef)
		return container.entry
	}

	override suspend fun deleteEntry(entryDef: EntryDef): Boolean {
		cleanupReferencesOnDelete(entryDef.id)
		encyclopediaService.deleteEntry(entryDef)
		return true
	}

	override suspend fun removeEntryImage(): Boolean {
		if (encyclopediaService.removeEntryImage(state.value.entryDef)) {
			reload()
		}
		return true
	}

	override suspend fun setImage(path: String) {
		encyclopediaService.setEntryImage(state.value.entryDef, path)
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
	): EntryResult = withContext(dispatcherDefault) {
		val currentAliases = state.value.content?.aliases.orEmpty()
		val previousName = state.value.entryDef.name
		val result = encyclopediaService.updateEntry(
			oldEntryDef = state.value.entryDef,
			name = name,
			text = text,
			tags = tags,
			aliases = currentAliases,
			excludeFromDictionary = state.value.content?.excludeFromDictionary ?: false,
		)
		if (result.instance != null && result.error == EntryError.NONE) {
			_state.getAndUpdate {
				it.copy(
					entryDef = result.instance.toDef(projectDef)
				)
			}

			reload()

			if (previousName != result.instance.entry.name) {
				backfillEntryReferences(result.instance.entry)
			}
		}

		result
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

				encyclopediaService.updateEntry(
					oldEntryDef = state.value.entryDef,
					name = name,
					text = text,
					tags = newTags,
					aliases = aliases,
					excludeFromDictionary = excludeFromDictionary,
				)

				reload()
			}
		}
	}

	override fun showGlobalSearchForTag(tag: String) {
		onShowGlobalSearchForTag(tag)
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

	override suspend fun addTags(tagInput: String) = withContext(dispatcherDefault) {
		val newTags = parseTagInput(tagInput)

		state.value.content?.apply {
			encyclopediaService.updateEntry(
				oldEntryDef = state.value.entryDef,
				name = name,
				text = text,
				tags = tags + newTags,
				aliases = aliases,
				excludeFromDictionary = excludeFromDictionary,
			)
		}

		endTagAdd()
		reload()
	}

	override fun startAliasAdd() {
		_state.getAndUpdate { it.copy(showAliasAdd = true) }
	}

	override fun endAliasAdd() {
		_state.getAndUpdate { it.copy(showAliasAdd = false) }
	}

	override suspend fun addAlias(alias: String): EntryResult = withContext(dispatcherDefault) {
		val current = state.value.content
			?: return@withContext EntryResult(EntryError.NONE)
		val trimmed = alias.trim()
		if (trimmed.isEmpty()) {
			endAliasAdd()
			return@withContext EntryResult(EntryError.NONE)
		}
		val result = encyclopediaService.updateEntry(
			oldEntryDef = state.value.entryDef,
			name = current.name,
			text = current.text,
			tags = current.tags,
			aliases = current.aliases + trimmed,
			excludeFromDictionary = current.excludeFromDictionary,
		)
		if (result.error == EntryError.NONE) {
			endAliasAdd()
			reload()
			result.instance?.entry?.let { backfillEntryReferences(it) }
		}
		result
	}

	override fun removeAlias(alias: String) {
		scope.launch {
			state.value.content?.apply {
				encyclopediaService.updateEntry(
					oldEntryDef = state.value.entryDef,
					name = name,
					text = text,
					tags = tags,
					aliases = aliases.filterNot { it == alias },
					excludeFromDictionary = excludeFromDictionary,
				)

				reload()
			}
		}
	}

	override suspend fun setExcludeFromDictionary(exclude: Boolean): EntryResult =
		withContext(dispatcherDefault) {
			val current = state.value.content
				?: return@withContext EntryResult(EntryError.NONE)
			val result = encyclopediaService.updateEntry(
				oldEntryDef = state.value.entryDef,
				name = current.name,
				text = current.text,
				tags = current.tags,
				aliases = current.aliases,
				excludeFromDictionary = exclude,
			)
			if (result.error == EntryError.NONE) {
				reload()
			}
			result
		}

	override fun navigateToAppearance(appearance: ViewEntry.Appearance) {
		showScene(appearance.sceneItem)
	}

	private fun getMenuId(): String {
		return "view-entry"
	}

	private fun addEntryMenu() {

		val addImage = MenuItemDescriptor(
			"view-entry-add-image",
			Res.string.encyclopedia_entry_menu_add_image,
			"",
		) {
			_state.getAndUpdate { it.copy(showAddImageDialog = true) }
		}

		val removeImage = MenuItemDescriptor(
			"view-entry-remove-image",
			Res.string.encyclopedia_entry_menu_remove_image,
			"",
		) {
			_state.getAndUpdate { it.copy(showDeleteImageDialog = true) }
		}

		val deleteEntry = MenuItemDescriptor(
			"view-entry-delete",
			Res.string.encyclopedia_entry_menu_delete,
			"",
		) {
			_state.getAndUpdate { it.copy(showDeleteEntryDialog = true) }
		}

		val menuItems = setOf(addImage, removeImage, deleteEntry)
		val menu = MenuDescriptor(
			getMenuId(),
			Res.string.encyclopedia_entry_menu_group,
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