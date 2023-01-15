package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.fileio.HPath

@Preview
@Composable
private fun EntryDefItemPreview() {
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	val entry = EntryDef(
		id = 1,
		type = EntryType.PERSON,
		name = "Bob",
		projectDef = fakeProjectDef()
	)
	val component: Encyclopedia = fakeComponent()

	EntryDefItem(
		entryDef = entry,
		component = component,
		snackbarHostState = snackbarHostState,
		scope = scope,
	)
}

@Preview
@Composable
private fun EncyclopediaUiPreview() {
	val component: Encyclopedia = fakeComponent()
	EncyclopediaUi(component)
}

@Preview
@Composable
private fun CreateEntryPreview() {
	val component: Encyclopedia = fakeComponent()
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(Ui.PADDING)) {
		CreateEntry(
			component = component,
			scope = scope,
			snackbarHostState = snackbarHostState,
			modifier = Modifier.align(Alignment.Center)
		) {

		}
	}
}

private fun fakeProjectDef(): ProjectDef = ProjectDef(
	name = "Test",
	path = HPath(
		name = "Test",
		path = "/",
		isAbsolute = true
	)
)

private fun fakeEntryContent(): EntryContent = EntryContent(
	name = "Test",
	id = 0,
	type = EntryType.PERSON,
	text = "Lots of text text to show how things look and thats pretty cool",
	tags = listOf("one", "two")
)


private fun fakeComponent(): Encyclopedia = object : Encyclopedia {
	override val state: Value<Encyclopedia.State>
		get() = MutableValue(
			Encyclopedia.State(
				projectDef = fakeProjectDef(),
				entryDefs = listOf(
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
			)
		)

	override fun updateFilter(text: String?, type: EntryType?) {}
	override fun createEntry(name: String, type: EntryType, text: String, tags: List<String>) =
		EntryResult(EntryError.NONE)

	override fun getFilteredEntries() = state.value.entryDefs
	override suspend fun loadEntryContent(entryDef: EntryDef) = fakeEntryContent()
	override fun showCreate(show: Boolean) {}
}