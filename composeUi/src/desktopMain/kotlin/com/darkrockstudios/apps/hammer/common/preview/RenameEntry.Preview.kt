package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry
import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.rename.*
import com.darkrockstudios.apps.hammer.common.encyclopedia.rename.RenameEntryCard

private class PreviewRenameEntry(state: RenameEntry.State) : RenameEntry {
	override val state = MutableValue(state)
	override fun updateName(text: String) {}
	override fun updateAlias(index: Int, text: String) {}
	override fun setAliasRemoved(index: Int, removed: Boolean) {}
	override fun addAlias() {}
	override fun updateAddedAlias(index: Int, text: String) {}
	override fun removeAddedAlias(index: Int) {}
	override fun setKeepOldNames(keep: Boolean) {}
	override fun findReferences() {}
	override fun backToNames() {}
	override fun setItemExcluded(key: RenameItemKey, excluded: Boolean) {}
	override fun setUnsureIncluded(key: RenameMatchKey, included: Boolean) {}
	override fun apply() {}
	override fun close() {}
}

private val namesState = RenameEntry.State(
	step = RenameEntry.Step.Names,
	name = RenameEntry.FormEdit(from = "Elias Varr", text = "Elias Marr"),
	aliases = listOf(
		RenameEntry.FormEdit(from = "Varr", text = "Marr"),
		RenameEntry.FormEdit(from = "the keeper"),
	),
	check = RenameCheck(
		newName = "Elias Marr",
		newAliases = listOf("Marr", "the keeper"),
		entryError = EntryError.NONE,
		hasBlankForm = false,
		collisions = emptyList(),
		hasChanges = true,
	),
)

private fun summaryState(): RenameEntry.State {
	fun match(item: RenameItemKey, offset: Int, text: String, to: String, reason: UnsureReason?, snippet: String) =
		RenameMatch(
			key = RenameMatchKey(item, offset, text),
			replacement = to,
			reason = reason,
			snippet = AnnotatedSnippet(snippet, snippet.indexOf(text), snippet.indexOf(text) + text.length),
		)

	val harbour = RenameItemKey(RenamePlace.SCENE_TEXT, 1)
	val lampRoom = RenameItemKey(RenamePlace.SCENE_TEXT, 2)
	val note = RenameItemKey(RenamePlace.NOTE, 7)
	val preview = RenamePreview(
		request = RenameRequest(
			entryId = 1,
			name = FormMapping("Elias Varr", FormAction.Rename("Elias Marr")),
			aliases = listOf(FormMapping("Varr", FormAction.Rename("Marr"))),
		),
		oldName = "Elias Varr",
		newName = "Elias Marr",
		newAliases = listOf("Marr", "the keeper"),
		items = listOf(
			RenameItem(
				harbour, "The Harbour", listOf(
					match(harbour, 4, "Elias Varr", "Elias Marr", null, "…Elias Varr stepped off the ferry…"),
					match(harbour, 90, "Varr", "Marr", null, "…said Varr, not looking up…"),
				)
			),
			RenameItem(
				lampRoom, "The Lamp Room", listOf(
					match(lampRoom, 12, "Varrs", "Marrs", UnsureReason.Plural, "…the Varrs had kept the light…"),
				)
			),
			RenameItem(
				note, "Lighthouse mechanics", listOf(
					match(note, 3, "Varr", "Marr", null, "…Varr winds the clockwork…"),
				)
			),
		),
		backupSupported = true,
	)
	return namesState.copy(step = RenameEntry.Step.Summary(preview))
}

@Composable
private fun RenameScrim(state: RenameEntry.State) {
	PreviewScrim {
		RenameEntryCard(
			state = state,
			component = PreviewRenameEntry(state),
			isCompact = false,
			onClose = {},
		)
	}
}

/** Rename everywhere: choosing the new names. */
@Composable
internal fun RenameEntryNamesScrimPreview() = RenameScrim(namesState)

/** Rename everywhere: reviewing what will change. */
@Composable
internal fun RenameEntrySummaryScrimPreview() = RenameScrim(summaryState())
