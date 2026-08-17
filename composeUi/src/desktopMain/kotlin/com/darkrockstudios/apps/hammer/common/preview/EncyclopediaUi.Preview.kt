package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.encyclopedia.CreateEntryUi
import com.darkrockstudios.apps.hammer.common.encyclopedia.EncyclopediaEntryItem
import com.darkrockstudios.apps.hammer.common.encyclopedia.EncyclopediaUi
import com.darkrockstudios.apps.hammer.common.encyclopedia.ViewEntryUi

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
fun EntryDefItemPreview() {
	val scope = rememberCoroutineScope()

	val entry = EntryDef(
		id = 1,
		type = EntryType.PERSON,
		name = "Bob",
		projectDef = fakeProjectDef()
	)

	KoinApplicationPreview {
		SharedTransitionLayout {
			AnimatedVisibility(visible = true) {
				EncyclopediaEntryItem(
					entryDef = entry,
					component = browseEntriesComponent,
					viewEntry = {},
					scope = scope,
					sharedTransitionScope = this@SharedTransitionLayout,
					animatedVisibilityScope = this@AnimatedVisibility,
					filterByType = {}
				)
			}
		}
	}
}

private val browseEntriesComponent: BrowseEntries = object : BrowseEntries {
	override val state: Value<BrowseEntries.State>
		get() = MutableValue(
			BrowseEntries.State(
				entryDefs = entryDefs
			)
		)
	override val filterText: Value<String>
		get() = MutableValue("")
	override val tagIndex: Value<TagIndex>
		get() = MutableValue(TagIndex.EMPTY)

	override fun updateFilter(text: String?, type: EntryType?) {}
	override fun addTagToSearch(tag: String) {}
	override fun getFilteredEntries(): List<EntryDef> = entryDefs

	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent {
		return EntryContent(
			id = 0,
			name = entryDef.name,
			type = entryDef.type,
			text = "test test",
			tags = setOf("one", "two")
		)
	}

	override fun getImagePath(entryDef: EntryDef) = null
	override suspend fun calculateEntryImageHash(entryDef: EntryDef) = null
	override fun clearFilterText() {}
}

@Preview
@Composable
fun ScreenEncyclopediaUiPreview() {
	val component: Encyclopedia = object : Encyclopedia {
		override val backHandler = dummyBackHandler
		override fun onBack() {}

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
	val rootSnackbar = rememberRootSnackbarHostState()
	KoinApplicationPreview {
		EncyclopediaUi(component, rootSnackbar)
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenEncyclopediaUiTabletPreview() {
	val component: Encyclopedia = object : Encyclopedia {
		override val backHandler = dummyBackHandler
		override fun onBack() {}

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
	val rootSnackbar = rememberRootSnackbarHostState()
	KoinApplicationPreview {
		TabletPreviewSurface {
			EncyclopediaUi(component, rootSnackbar)
		}
	}
}

private val fakeCreateEntryComponent: CreateEntry = object : CreateEntry {
	override val state: Value<CreateEntry.State>
		get() = MutableValue(CreateEntry.State(fakeProjectDef()))

	override suspend fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: Set<String>,
		imagePath: String?,
		excludeFromDictionary: Boolean,
	): EntryResult = EntryResult(EntryContainer(fakeEntryContent()), EntryError.NONE)

	override fun confirmClose() {}
	override fun dismissConfirmClose() {}
	override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
}

@Preview
@Composable
fun ScreenCreateEntryPreview() {
	val scope = rememberCoroutineScope()
	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.size(width = 1280.dp, height = 900.dp),
			) {
				CreateEntryUi(
					component = fakeCreateEntryComponent,
					scope = scope,
					rootSnackbar = rootSnackbar,
					modifier = Modifier,
				) {}
			}
		}
	}
}

@Preview
@Composable
fun ScreenCreateEntryNarrowPreview() {
	val scope = rememberCoroutineScope()
	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.size(width = 390.dp, height = 780.dp),
			) {
				CreateEntryUi(
					component = fakeCreateEntryComponent,
					scope = scope,
					rootSnackbar = rootSnackbar,
					modifier = Modifier,
				) {}
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
fun ScreenViewEntryPreview() {

	val scope = rememberCoroutineScope()
	val rootSnackbar = rememberRootSnackbarHostState()

	KoinApplicationPreview {
	Column {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.fillMaxSize()
					.padding(Ui.Padding.XL)
			) {
				SharedTransitionLayout {
					androidx.compose.animation.AnimatedVisibility(visible = true) {
						ViewEntryUi(
							component = fakeViewEntryComponent,
							scope = scope,
							closeEntry = {},
							rootSnackbar = rootSnackbar,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@AnimatedVisibility,
						)
					}
				}
			}
		}

		Spacer(modifier = Modifier.padding(16.dp))

		AppTheme(globalSettingsPreview, true) {
			BoxWithConstraints(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.fillMaxSize()
					.padding(Ui.Padding.XL)
			) {
				SharedTransitionLayout {
					androidx.compose.animation.AnimatedVisibility(visible = true) {
						ViewEntryUi(
							component = fakeViewEntryComponent,
							scope = scope,
							closeEntry = {},
							rootSnackbar = rootSnackbar,
							sharedTransitionScope = this@SharedTransitionLayout,
							animatedVisibilityScope = this@AnimatedVisibility,
						)
					}
				}
			}
		}
	}
	}
}

val fakeViewEntryComponent: ViewEntry = object : ViewEntry {
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
	override suspend fun updateEntry(name: String, text: String, tags: Set<String>) =
		EntryResult(EntryContainer(fakeEntryContent()), EntryError.NONE)

	override suspend fun removeEntryImage() = true
	override suspend fun setImage(path: String) {}
	override fun showDeleteEntryDialog() {}
	override fun closeDeleteEntryDialog() {}
	override fun showDeleteImageDialog() {}
	override fun closeDeleteImageDialog() {}
	override fun showAddImageDialog() {}
	override fun closeAddImageDialog() {}
	override fun startNameEdit() {}
	override fun startTextEdit() {}
	override fun finishNameEdit() {}
	override fun finishTextEdit() {}
	override fun confirmClose() {}
	override fun dismissConfirmClose() {}
	override fun removeTag(tag: String) {}
	override fun showGlobalSearchForTag(tag: String) {}
	override fun startTagAdd() {}
	override suspend fun addTags(tagInput: String) {}
	override fun endTagAdd() {}
	override fun startAliasAdd() {}
	override fun endAliasAdd() {}
	override suspend fun addAlias(alias: String) =
		EntryResult(EntryContainer(fakeEntryContent()), EntryError.NONE)
	override fun removeAlias(alias: String) {}
	override suspend fun setExcludeFromDictionary(exclude: Boolean) =
		EntryResult(EntryContainer(fakeEntryContent()), EntryError.NONE)
	override fun navigateToAppearance(appearance: ViewEntry.Appearance) {}
	override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
}

private fun fakeEntryDef(): EntryDef = EntryDef(
	projectDef = fakeProjectDef(),
	name = "Test",
	id = 0,
	type = EntryType.PLACE
)

fun fakeEntryContent(): EntryContent = EntryContent(
	name = "Test",
	id = 0,
	type = EntryType.PERSON,
	text = "Lots of text text to show how things look and thats pretty cool",
	tags = setOf("one", "two")
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
