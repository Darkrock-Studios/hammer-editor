package com.darkrockstudios.apps.hammer.common.components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.subscribe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntryComponent
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.components.images.pickImage
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryLoadError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.CleanupReferencesOnEntryDeleteUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tagindex.parseTagInput
import io.github.aakira.napier.Napier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import okio.IOException

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
	private val imagePickRequests: ImagePickRequests by projectInject()
	private val imagesService: ImagesService by projectInject()

	private val reloadMutex = Mutex()

	private val renameNavigation = SlotNavigation<RenameConfig>()

	override val renameSlot: Value<ChildSlot<RenameConfig, RenameEntry>> =
		componentContext.childSlot(
			source = renameNavigation,
			serializer = RenameConfig.serializer(),
			key = "RenameSlot",
			// The dialog decides when it may close: never while a rename is being written.
			handleBackButton = false,
			childFactory = { config, childContext ->
				RenameEntryComponent(
					componentContext = childContext,
					projectDef = projectDef,
					entryId = config.entryId,
					onRenamed = ::onRenamed,
					onClose = ::dismissRename,
				)
			}
		)

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
		watchEntry()
		watchImages()
		restoreLibraryPick()
	}

	// The gallery comes back with the project root after process death; wait for its answer again.
	private fun restoreLibraryPick() {
		val pending = stateKeeper.consume(LIBRARY_PICK_KEY, Boolean.serializer()) == true
		stateKeeper.register(LIBRARY_PICK_KEY, Boolean.serializer()) { state.value.pendingLibraryPick }
		if (pending) {
			val target = libraryPickTarget()
			awaitLibraryPick { imagePickRequests.awaitResult(target) }
		}
	}

	private fun libraryPickTarget() = ImagePickTarget.EntryImage(state.value.entryDef.id)

	override fun addImageFromLibrary() {
		if (state.value.pendingLibraryPick) return
		val target = libraryPickTarget()
		awaitLibraryPick { imagePickRequests.pick(target) }
	}

	override suspend fun pickImageForText(): ImageDef? =
		imagePickRequests.pickImage(ImagePickTarget.EntryText(state.value.entryDef.id), imagesService)

	private fun awaitLibraryPick(pick: suspend () -> Int?) {
		_state.getAndUpdate { it.copy(pendingLibraryPick = true) }
		scope.launch {
			try {
				val imageId = pick() ?: return@launch
				// Already listed, it is not added twice, but it is still the one shown.
				encyclopediaService.addEntryImage(state.value.entryDef, imageId)
				loadEntryState(showImageId = imageId)
			} catch (e: EntryLoadError) {
				Napier.w("Failed to add a library image to entry ${state.value.entryDef.id}", e)
			} catch (e: IOException) {
				Napier.w("Failed to add a library image to entry ${state.value.entryDef.id}", e)
			} finally {
				withContext(NonCancellable + dispatcherMain) {
					_state.getAndUpdate { it.copy(pendingLibraryPick = false) }
				}
			}
		}
	}

	// Follows writes made elsewhere (sync), including a rename that moves the entry's file.
	private fun watchEntry() {
		scope.launch {
			val entryId = state.value.entryDef.id
			encyclopediaService.entryChangedFlow.filter { it == entryId }.collect {
				// The entry's files can be mid-move while a sync is writing them.
				try {
					val currentDef = encyclopediaService.findEntryDef(entryId)
						?: return@collect
					withContext(dispatcherMain) {
						_state.getAndUpdate { it.copy(entryDef = currentDef) }
					}
					loadEntryState()
				} catch (e: EntryLoadError) {
					Napier.w("Failed to refresh entry $entryId", e)
				} catch (e: IOException) {
					Napier.w("Failed to refresh entry $entryId", e)
				}
			}
		}
	}

	// Follows an image of this entry being replaced, renamed or deleted, by sync or elsewhere.
	private fun watchImages() {
		scope.launch {
			encyclopediaService.imageChangedFlow
				.filter { id -> state.value.content?.images?.contains(id) == true }
				.collect {
					try {
						loadEntryState()
					} catch (e: EntryLoadError) {
						Napier.w("Failed to refresh the images of entry ${state.value.entryDef.id}", e)
					} catch (e: IOException) {
						Napier.w("Failed to refresh the images of entry ${state.value.entryDef.id}", e)
					}
				}
		}
	}

	private fun reload() {
		scope.launch { loadEntryState() }
	}

	/**
	 * Reloads the entry; with [showImageId], the image on show becomes that one when it is listed.
	 * Serialized so overlapping reloads can't land out of order.
	 */
	private suspend fun loadEntryState(showImageId: Int? = null) = reloadMutex.withLock {
		val content = loadEntryContent(state.value.entryDef)
		val images = encyclopediaService.entryImageFiles(content).map { file ->
			ViewEntry.EntryImage(
				id = file.def.id,
				name = file.def.name,
				path = file.path.path,
				hash = file.contentHash,
			)
		}
		withContext(dispatcherMain) {
			_state.getAndUpdate {
				val shown = images.indexOfFirst { image -> image.id == showImageId }
				val index = if (shown >= 0) shown else it.currentImageIndex
				it.copy(
					images = images,
					currentImageIndex = index.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
					content = content
				)
			}
		}
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

	override suspend fun importImage(path: String): ImageAddResult = withContext(dispatcherDefault) {
		val outcome = encyclopediaService.importEntryImage(state.value.entryDef, path)
		outcome.image?.let { loadEntryState(showImageId = it.id) }
		outcome.result
	}

	override suspend fun removeImage(id: Int): Boolean = withContext(dispatcherDefault) {
		val removed = encyclopediaService.removeEntryImage(state.value.entryDef, id)
		if (removed) loadEntryState()
		removed
	}

	override suspend fun setPrimaryImage(id: Int): Boolean = withContext(dispatcherDefault) {
		val moved = encyclopediaService.setPrimaryImage(state.value.entryDef, id)
		if (moved) loadEntryState(showImageId = id)
		moved
	}

	override fun showImageAt(index: Int) {
		_state.getAndUpdate { it.copy(currentImageIndex = index) }
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

	override fun showRemoveImageConfirm(id: Int) {
		_state.getAndUpdate { it.copy(removeImageConfirm = id) }
	}

	override fun dismissRemoveImageConfirm() {
		_state.getAndUpdate { it.copy(removeImageConfirm = null) }
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

	override fun discardEdit() {
		_state.getAndUpdate {
			it.copy(editName = false, editText = false, confirmClose = false)
		}
	}

	/**
	 * Saves the entry as [transform] of its current content, sourcing unchanged fields from
	 * the loaded state or, if the async load hasn't landed yet, straight from disk - so a
	 * save can never revert a field to its default.
	 */
	private suspend fun saveEntry(transform: (EntryContent) -> EntryContent): EntryResult {
		val current = state.value.content ?: loadEntryContent(state.value.entryDef)
		val updated = transform(current)
		return encyclopediaService.updateEntry(
			oldEntryDef = state.value.entryDef,
			name = updated.name,
			text = updated.text,
			tags = updated.tags,
			aliases = updated.aliases,
			excludeFromDictionary = updated.excludeFromDictionary,
			images = updated.images,
		)
	}

	override suspend fun updateEntry(
		name: String,
		text: String,
		tags: Set<String>
	): EntryResult = withContext(dispatcherDefault) {
		val previousName = state.value.entryDef.name
		val result = saveEntry { it.copy(name = name, text = text, tags = tags) }
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
			saveEntry { it.copy(tags = it.tags - tag) }
			reload()
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

		saveEntry { it.copy(tags = it.tags + newTags) }

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
		val trimmed = alias.trim()
		if (trimmed.isEmpty()) {
			endAliasAdd()
			return@withContext EntryResult(EntryError.NONE)
		}
		val result = saveEntry { it.copy(aliases = it.aliases + trimmed) }
		if (result.error == EntryError.NONE) {
			endAliasAdd()
			reload()
			result.instance?.entry?.let { backfillEntryReferences(it) }
		}
		result
	}

	override fun removeAlias(alias: String) {
		scope.launch {
			saveEntry { it.copy(aliases = it.aliases.filterNot { a -> a == alias }) }
			reload()
		}
	}

	override suspend fun setExcludeFromDictionary(exclude: Boolean): EntryResult =
		withContext(dispatcherDefault) {
			val result = saveEntry { it.copy(excludeFromDictionary = exclude) }
			if (result.error == EntryError.NONE) {
				reload()
			}
			result
		}

	override fun navigateToAppearance(appearance: ViewEntry.Appearance) {
		showScene(appearance.sceneItem)
	}

	override fun showRename() {
		renameNavigation.activate(RenameConfig(state.value.entryDef.id))
	}

	override fun dismissRename() {
		renameNavigation.dismiss()
	}

	private fun onRenamed(entryDef: EntryDef) {
		_state.getAndUpdate { it.copy(entryDef = entryDef) }
		reload()
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
			_state.getAndUpdate { it.copy(removeImageConfirm = it.images.getOrNull(it.currentImageIndex)?.id) }
		}

		val rename = MenuItemDescriptor(
			"view-entry-rename",
			Res.string.encyclopedia_entry_menu_rename,
			"",
		) {
			showRename()
		}

		val deleteEntry = MenuItemDescriptor(
			"view-entry-delete",
			Res.string.encyclopedia_entry_menu_delete,
			"",
		) {
			_state.getAndUpdate { it.copy(showDeleteEntryDialog = true) }
		}

		val menuItems = setOf(rename, addImage, removeImage, deleteEntry)
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

@Serializable
data class RenameConfig(val entryId: Int)

private const val LIBRARY_PICK_KEY = "view_entry_library_pick"
