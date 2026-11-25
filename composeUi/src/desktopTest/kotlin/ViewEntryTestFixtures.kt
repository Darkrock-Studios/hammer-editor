import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelinePlacementLink
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult

internal class FakeViewEntry(initial: ViewEntry.State) : ViewEntry {
	private val _state = MutableValue(initial)
	override val state: Value<ViewEntry.State> = _state
	override val renameSlot: Value<ChildSlot<*, RenameEntry>> = MutableValue(ChildSlot<Any, RenameEntry>())

	val shown = mutableListOf<TimelinePlacementLink>()
	var confirmCloseCalls = 0
		private set

	override fun showTimelinePlacement(placement: TimelinePlacementLink) {
		shown += placement
	}

	private val saved get() = EntryResult(EntryContainer(state.value.content!!), EntryError.NONE)
	override suspend fun loadEntryContent(entryDef: EntryDef) = state.value.content!!
	override suspend fun deleteEntry(entryDef: EntryDef) = true
	override suspend fun updateEntry(name: String, text: String, tags: Set<String>) = saved
	override suspend fun importImage(path: String) = ImageAddResult.Ok
	override fun addImageFromLibrary() {}
	override suspend fun removeImage(id: Int) = true
	override suspend fun setPrimaryImage(id: Int) = true
	override fun showImageAt(index: Int) {}
	override fun showDeleteEntryDialog() {}
	override fun closeDeleteEntryDialog() {}
	override fun showRemoveImageConfirm(id: Int) {}
	override fun dismissRemoveImageConfirm() {}
	override fun showAddImageDialog() {}
	override fun closeAddImageDialog() {}
	override fun startNameEdit() = _state.update { it.copy(editName = true) }
	override fun startTextEdit() = _state.update { it.copy(editText = true) }
	override fun finishNameEdit() = _state.update { it.copy(editName = false) }
	override fun finishTextEdit() = _state.update { it.copy(editText = false) }
	override fun discardEdit() = _state.update { it.copy(editName = false, editText = false, confirmClose = false) }
	override fun confirmClose() {
		confirmCloseCalls++
	}

	override fun dismissConfirmClose() {}
	override fun removeTag(tag: String) {}
	override fun showGlobalSearchForTag(tag: String) {}
	override fun startTagAdd() {}
	override suspend fun addTags(tagInput: String) {}
	override fun endTagAdd() {}
	override fun startAliasAdd() {}
	override fun endAliasAdd() {}
	override suspend fun addAlias(alias: String) = saved
	override fun removeAlias(alias: String) {}
	override suspend fun setExcludeFromDictionary(exclude: Boolean) = saved
	override fun navigateToAppearance(appearance: ViewEntry.Appearance) {}
	override fun showRename() {}
	override fun dismissRename() {}
	override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
}
