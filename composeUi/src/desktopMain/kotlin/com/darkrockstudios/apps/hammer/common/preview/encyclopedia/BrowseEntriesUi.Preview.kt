package com.darkrockstudios.apps.hammer.common.preview.encyclopedia

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.encyclopedia.BrowseEntriesUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview

@Preview
@Composable
fun BrowseEntriesUiPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		BrowseEntriesUi(
			component = component,
			scope = scope,
			viewEntry = {},
		)
	}
}

private val component = object : BrowseEntries {
	override val state: Value<BrowseEntries.State> = MutableValue(
		BrowseEntries.State()
	)
	override val filterText = MutableValue("asd")

	override fun updateFilter(text: String?, type: EntryType?) {}
	override fun getFilteredEntries(): List<EntryDef> = emptyList()
	override suspend fun loadEntryContent(entryDef: EntryDef) = EntryContent(
		id = 1,
		name = "asd",
		type = EntryType.PERSON,
		tags = emptySet(),
		text = ""
	)

	override fun getImagePath(entryDef: EntryDef) = null
	override fun addTagToSearch(tag: String) {}
	override fun clearFilterText() {}
}