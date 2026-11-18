package com.darkrockstudios.apps.hammer.common.preview.encyclopedia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityRef
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.encyclopedia.BrowseEntriesUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
fun ScreenBrowseEntriesUiPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		SharedTransitionLayout {
			AnimatedVisibility(visible = true) {
				BrowseEntriesUi(
					component = component,
					scope = scope,
					viewEntry = {},
					sharedTransitionScope = this@SharedTransitionLayout,
					animatedVisibilityScope = this@AnimatedVisibility,
				)
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenBrowseEntriesUiTabletPreview() {
	val scope = rememberCoroutineScope()

	KoinApplicationPreview {
		TabletPreviewSurface {
			SharedTransitionLayout {
				AnimatedVisibility(visible = true) {
					BrowseEntriesUi(
						component = component,
						scope = scope,
						viewEntry = {},
						sharedTransitionScope = this@SharedTransitionLayout,
						animatedVisibilityScope = this@AnimatedVisibility,
					)
				}
			}
		}
	}
}

private val previewEntries = listOf(
	EntryContent(
		id = 1,
		name = "Elias Varr",
		type = EntryType.PERSON,
		text = "The new keeper. Counts things when he is nervous, and is nervous most of the time.",
		tags = setOf("keeper", "gothic"),
		aliases = listOf("The Keeper"),
	),
	EntryContent(
		id = 2,
		name = "Maren Holt",
		type = EntryType.PERSON,
		text = "Runs the chandlery. Knew the last keeper better than she lets on.",
		tags = setOf("harbour"),
	),
	EntryContent(
		id = 3,
		name = "The Lighthouse",
		type = EntryType.PLACE,
		text = "One hundred and nine steps to the lamp room. The town can see its light from every window.",
		tags = setOf("coastal", "gothic"),
	),
	EntryContent(
		id = 4,
		name = "Saltmarsh Harbour",
		type = EntryType.PLACE,
		text = "A fishing town that lights its windows one by one at dusk, as if answering the tower.",
		tags = setOf("coastal", "harbour"),
	),
	EntryContent(
		id = 5,
		name = "The Keeper's Cup",
		type = EntryType.THING,
		text = "A chipped enamel cup left beside the chair in the lamp room, a dry brown ring inside.",
		tags = setOf("keeper"),
	),
	EntryContent(
		id = 6,
		name = "The Long Silence",
		type = EntryType.EVENT,
		text = "The night the lamp went dark and a ship ran aground on the rocks below the cliff.",
		tags = setOf("gothic"),
	),
	EntryContent(
		id = 7,
		name = "Salt in the Walls",
		type = EntryType.IDEA,
		text = "Every house in the village keeps salt in the walls. Nobody will say why.",
		tags = setOf("gothic", "harbour"),
	),
)

private val previewEntryDefs = previewEntries.map { it.toDef(fakeProjectDef()) }

private val component = object : BrowseEntries {
	override val state: Value<BrowseEntries.State> = MutableValue(
		BrowseEntries.State(entryDefs = previewEntryDefs)
	)
	override val filterText = MutableValue("")
	override val tagIndex: Value<TagIndex> = MutableValue(
		previewEntries
			.flatMap { entry -> entry.tags.map { it to entry.id } }
			.groupBy({ it.first }, { it.second })
			.let { byTag ->
				TagIndex(
					tagToEntities = byTag.mapValues { (_, ids) ->
						ids.map { TaggedEntityRef(TaggedEntityType.Encyclopedia, it) }.toSet()
					},
					countsByType = mapOf(TaggedEntityType.Encyclopedia to byTag.mapValues { it.value.size }),
				)
			}
	)

	override fun updateFilter(text: String?, type: EntryType?) {}
	override fun addTagToSearch(tag: String) {}
	override fun getFilteredEntries(): List<EntryDef> = previewEntryDefs
	override suspend fun loadEntryContent(entryDef: EntryDef) = previewEntries.first { it.id == entryDef.id }

	override suspend fun loadEntryImage(entry: EntryContent) = null
	override fun clearFilterText() {}
}
