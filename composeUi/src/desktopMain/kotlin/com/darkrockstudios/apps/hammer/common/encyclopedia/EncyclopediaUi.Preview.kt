package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.CreateEntry
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef

@Preview
@Composable
private fun EntryDefItemPreview() {
	val scope = rememberCoroutineScope()

	val entry = EntryDef(
		id = 1,
		type = EntryType.PERSON,
		name = "Bob",
		projectDef = fakeProjectDef()
	)

	EncyclopediaEntryItem(
		entryDef = entry,
		component = browseEntriesComponent,
		viewEntry = {},
		scope = scope,
		filterByType = {}
	)
}

private val browseEntriesComponent: BrowseEntries = object : BrowseEntries {
	override val state: Value<BrowseEntries.State>
		get() = MutableValue(
			BrowseEntries.State(
				entryDefs = entryDefs
			)
		)

	override fun updateFilter(text: String?, type: EntryType?) {}
	override fun getFilteredEntries(): List<EntryDef> = entryDefs

	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent {
		return EntryContent(
			id = 0,
			name = entryDef.name,
			type = entryDef.type,
			text = "test test",
			tags = listOf("one", "two")
		)
	}

	override fun getImagePath(entryDef: EntryDef) = null
}

@Preview
@Composable
private fun EncyclopediaUiPreview() {
	val component: Encyclopedia = object : Encyclopedia {
		override val stack: Value<ChildStack<Encyclopedia.Config, Encyclopedia.Destination>>
			get() = MutableValue(
				ChildStack(
					Encyclopedia.Config.BrowseEntriesConfig(
						fakeProjectDef()
					),
					Encyclopedia.Destination.BrowseEntriesDestination(
						browseEntriesComponent
					)
				)
			)

		override fun showBrowse() {}
		override fun showViewEntry(entryDef: EntryDef) {}
		override fun showCreateEntry() {}
		override fun isAtRoot() = true
		override fun shouldConfirmClose() = emptySet<CloseConfirm>()
	}
	EncyclopediaUi(component)
}

@Preview
@Composable
private fun CreateEntryPreview() {
	val component: CreateEntry = object : CreateEntry {
		override val state: Value<CreateEntry.State>
			get() = MutableValue(
				CreateEntry.State(fakeProjectDef())
			)

		override suspend fun createEntry(
			name: String,
			type: EntryType,
			text: String,
			tags: List<String>,
			imagePath: String?
		): EntryResult = EntryResult(EntryContainer(fakeEntryContent()), EntryError.NONE)
	}
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL)) {
		CreateEntryUi(
			component = component,
			scope = scope,
			snackbarHostState = snackbarHostState,
			modifier = Modifier.align(Alignment.Center)
		) {

		}
	}
}

@Preview
@Composable
private fun ViewEntryPreview() {
	val component: ViewEntry = object : ViewEntry {
		override val state: Value<ViewEntry.State>
			get() = MutableValue(
				ViewEntry.State(
					entryDef = fakeEntryDef(),
					content = fakeEntryContent()
				)
			)

		override fun getImagePath(entryDef: EntryDef) = null
		override suspend fun loadEntryContent(entryDef: EntryDef) = fakeEntryContent()
		override suspend fun deleteEntry(entryDef: EntryDef) = true
		override suspend fun updateEntry(name: String, text: String, tags: List<String>) =
			EntryResult(EntryContainer(fakeEntryContent()), EntryError.NONE)

		override suspend fun removeEntryImage() = true
		override suspend fun setImage(path: String) {}
		override fun showDeleteEntryDialog() {}
		override fun closeDeleteEntryDialog() {}
		override fun showDeleteImageDialog() {}
		override fun closeDeleteImageDialog() {}
		override fun showAddImageDialog() {}
		override fun closeAddImageDialog() {}
	}
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	Column {
		AppTheme {
			BoxWithConstraints(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.fillMaxSize()
					.padding(Ui.Padding.XL)
			) {
				ViewEntryUi(
					component = component,
					scope = scope,
					closeEntry = {},
					snackbarHostState = snackbarHostState
				)
			}
		}

		Spacer(modifier = Modifier.padding(16.dp))

		AppTheme(true) {
			BoxWithConstraints(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.fillMaxSize()
					.padding(Ui.Padding.XL)
			) {
				ViewEntryUi(
					component = component,
					scope = scope,
					closeEntry = {},
					snackbarHostState = snackbarHostState
				)
			}
		}
	}
}

private fun fakeEntryDef(): EntryDef = EntryDef(
	projectDef = fakeProjectDef(),
	name = "Test",
	id = 0,
	type = EntryType.PLACE
)

private fun fakeEntryContent(): EntryContent = EntryContent(
	name = "Test",
	id = 0,
	type = EntryType.PERSON,
	text = "Lots of text text to show how things look and thats pretty cool",
	tags = listOf("one", "two")
)

private val entryDefs = listOf(
	EntryDef(
		projectDef = fakeProjectDef(),
		name = "One",
		type = EntryType.PERSON,
		id = 0
	),
	EntryDef(
		projectDef = fakeProjectDef(),
		name = "Two",
		type = EntryType.PLACE,
		id = 1
	),
	EntryDef(
		projectDef = fakeProjectDef(),
		name = "Three",
		type = EntryType.PLACE,
		id = 1
	),
	EntryDef(
		projectDef = fakeProjectDef(),
		name = "Four",
		type = EntryType.PLACE,
		id = 1
	)
)