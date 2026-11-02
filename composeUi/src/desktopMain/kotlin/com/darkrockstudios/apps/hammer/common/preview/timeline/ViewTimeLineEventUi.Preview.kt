package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.timeline.ViewTimeLineEventUi

@Preview
@Composable
fun ScreenViewTimeLineEventUiPreview() {
	ViewPreview(eventState)
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenViewTimeLineEventUiTabletPreview() {
	ViewPreview(eventState, tablet = true)
}

@Preview
@Composable
fun ScreenViewTimeLineEventUiScenePreview() {
	ViewPreview(sceneState)
}

@Preview
@Composable
fun ScreenViewTimeLineEventUiEntryPreview() {
	ViewPreview(entryState)
}

@Preview
@Composable
fun ScreenViewTimeLineEventUiMissingPreview() {
	ViewPreview(missingState)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ViewPreview(state: ViewTimeLineEvent.State, tablet: Boolean = false) {
	val scope = rememberCoroutineScope()
	val component = PreviewViewTimeLineEvent(state)

	KoinApplicationPreview {
		val content: @Composable () -> Unit = {
			SharedTransitionLayout {
				AnimatedVisibility(visible = true) {
					ViewTimeLineEventUi(
						component = component,
						modifier = Modifier,
						scope = scope,
						rootSnackbar = rememberRootSnackbarHostState(),
						sharedTransitionScope = this@SharedTransitionLayout,
						animatedVisibilityScope = this@AnimatedVisibility,
					)
				}
			}
		}
		if (tablet) TabletPreviewSurface { content() } else content()
	}
}

private val event = TimeLineEvent(
	id = 1,
	order = 1,
	title = "The lamp goes dark",
	label = "Midsummer",
	content = "The lamp goes dark for the first time and a ship runs aground on the rocks below the cliff.",
	tags = setOf("act-one", "turning-point"),
	references = setOf(21, 23, 12),
	lane = "The Sea",
	milestone = true,
)

private val eventState = ViewTimeLineEvent.State(
	event = event,
	tags = event.tags,
	displayTitle = "The lamp goes dark",
	title = event.title.orEmpty(),
	lane = event.lane.orEmpty(),
	milestone = event.milestone,
	referenceIds = event.references,
	references = previewCatalog.filter { it.id in event.references },
	laneIndex = 2,
	lanes = previewLanes,
	catalog = previewCatalog,
)

private val sceneEvent = TimeLineEvent(
	id = 2,
	order = 0,
	kind = TimelineKind.SCENE,
	subject = 11,
	label = "Spring, Year One",
	lane = "Keeper",
)

private val sceneState = ViewTimeLineEvent.State(
	event = sceneEvent,
	kind = TimelineKind.SCENE,
	displayTitle = "The Keeper Arrives",
	subject = SubjectSummary.Scene(
		scene = SceneItem(fakeProjectDef(), SceneItem.Type.Scene, 11, "The Keeper Arrives", 0),
		chapterPath = "Part One › Spring",
		excerpt = "Maren climbs the hundred and twelve steps with her trunk on her back, and Old Tom does not offer to help.",
		cast = emptyList(),
	),
	lane = "Keeper",
	laneIndex = 0,
	lanes = previewLanes,
	references = listOf(
		CatalogItem(CatalogKind.Entry, 21, "Maren", entryType = EntryType.PERSON),
		CatalogItem(CatalogKind.Entry, 22, "Old Tom", entryType = EntryType.PERSON),
		CatalogItem(CatalogKind.Entry, 23, "The Lighthouse", entryType = EntryType.PLACE),
	),
)

private val entryEvent = TimeLineEvent(id = 3, order = 2, kind = TimelineKind.ENTRY, subject = 24, lane = "Village")

private val entryState = ViewTimeLineEvent.State(
	event = entryEvent,
	kind = TimelineKind.ENTRY,
	displayTitle = "The Wreck of the Alba",
	subject = SubjectSummary.Entry(
		entry = EntryDef(fakeProjectDef(), 24, EntryType.EVENT, "The Wreck of the Alba"),
		primaryImageId = null,
		excerpt = "The Alba runs onto the Teeth on the first dark night; the village wakes to her bell.",
	),
	lane = "Village",
	laneIndex = 1,
	lanes = previewLanes,
)

private val missingEvent = TimeLineEvent(
	id = 5,
	order = 4,
	kind = TimelineKind.SCENE,
	subject = 99,
	cachedTitle = "A Scene Since Lost",
)

private val missingState = ViewTimeLineEvent.State(
	event = missingEvent,
	kind = TimelineKind.SCENE,
	displayTitle = "A Scene Since Lost",
	subject = SubjectSummary.Missing,
	lanes = previewLanes,
)

private class PreviewViewTimeLineEvent(initial: ViewTimeLineEvent.State) : ViewTimeLineEvent {
	override val eventId: Int = initial.event?.id ?: 0
	override val state: Value<ViewTimeLineEvent.State> = MutableValue(initial)
	override val dateText: Value<String> = MutableValue(initial.event?.label.orEmpty())
	override val contentText: Value<String> = MutableValue(initial.event?.content.orEmpty())

	override fun onEventTextChanged(text: String) {}
	override fun onDateTextChanged(text: String) {}
	override fun onTagsChanged(newTags: Set<String>) {}
	override fun onTitleChanged(text: String) {}
	override fun onLaneChanged(text: String) {}
	override fun onMilestoneChanged(milestone: Boolean) {}
	override fun addReference(item: CatalogItem) {}
	override fun removeReference(id: Int) {}
	override fun suggestLanes(prefix: String): List<String> = emptyList()
	override suspend fun removeTag(tag: String) {}
	override fun showGlobalSearchForTag(tag: String) {}
	override suspend fun saveEdit(): TimeLineEventError = TimeLineEventError.NONE
	override fun startDeleteEvent() {}
	override fun endDeleteEvent() {}
	override suspend fun deleteEvent() {}
	override fun openSubject() {}
	override fun openReference(item: CatalogItem) {}
	override fun confirmDiscard() {}
	override fun cancelDiscard() {}
	override fun isEditingAndDirty(): Boolean = false
	override fun discardEdit() {}
	override fun beginEdit() {}
	override fun confirmClose() {}
	override fun cancelClose() {}
	override fun closeEvent() {}
	override suspend fun pickImageForText(): ImageDef? = null
	override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
}
