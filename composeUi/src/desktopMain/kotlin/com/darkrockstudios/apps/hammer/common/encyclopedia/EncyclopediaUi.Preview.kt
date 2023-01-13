package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
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
		entry = entry,
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

private fun fakeProjectDef(): ProjectDef = ProjectDef(
	name = "Test",
	path = HPath(
		name = "Test",
		path = "/",
		isAbsolute = true
	)
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
					)
				)
			)
		)

	override fun createEntry(name: String, type: EntryType, text: String, tags: List<String>) =
		EntryResult(EntryError.NONE)
}