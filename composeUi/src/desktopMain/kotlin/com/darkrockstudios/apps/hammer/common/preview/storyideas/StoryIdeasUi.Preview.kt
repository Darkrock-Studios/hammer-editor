package com.darkrockstudios.apps.hammer.common.preview.storyideas

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeas
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.StoryIdeasUi
import kotlin.time.Instant

@Preview
@Composable
fun ScreenStoryIdeasUiPreview() {
	StoryIdeasUi(
		component = fakeComponent(),
		rootSnackbar = rememberRootSnackbarHostState(),
	)
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenStoryIdeasUiTabletPreview() {
	TabletPreviewSurface {
		StoryIdeasUi(
			component = fakeComponent(),
			rootSnackbar = rememberRootSnackbarHostState(),
		)
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenStoryIdeasViewPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			StoryIdeasUi(
				component = fakeComponent(
					editor = StoryIdeas.Editor.Edit(previewIdeas.first()),
				),
				rootSnackbar = rememberRootSnackbarHostState(),
			)
		}
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenStoryIdeasViewTabletPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			StoryIdeasUi(
				component = fakeComponent(
					editor = StoryIdeas.Editor.Edit(previewIdeas.first()),
				),
				rootSnackbar = rememberRootSnackbarHostState(),
			)
		}
	}
}

private val previewIdeas = listOf(
	StoryIdea(
		id = IdeaId("0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c"),
		created = Instant.parse("2026-06-12T14:22:05Z"),
		updated = Instant.parse("2026-06-14T09:31:48Z"),
		title = "The Lighthouse Keeper's Daughter",
		content = "What if the light itself was the inheritance — not the building, the *light*. Passed mother to daughter for six generations, and no one has ever asked where it came from.",
		tags = setOf("gothic", "coastal", "generational"),
		promoted = Instant.parse("2026-07-01T10:00:00Z"),
	),
	StoryIdea(
		id = IdeaId("11111111-2222-3333-4444-555555555555"),
		created = Instant.parse("2026-06-20T08:00:00Z"),
		updated = Instant.parse("2026-06-20T08:00:00Z"),
		content = "A courier who can only deliver letters to people who are dreaming. One letter is addressed to her.",
		tags = setOf("fantasy"),
	),
	StoryIdea(
		id = IdeaId("22222222-3333-4444-5555-666666666666"),
		created = Instant.parse("2026-07-02T19:45:00Z"),
		updated = Instant.parse("2026-07-02T19:45:00Z"),
		title = "Salt in the Walls",
		content = "Every house in the village keeps salt in the walls. The new surveyor wants to know why, and nobody will say the word aloud.",
		tags = setOf("gothic", "folk-horror"),
	),
)

private fun previewDraft(editor: StoryIdeas.Editor?): StoryIdeas.Draft? = when (editor) {
	null -> null
	StoryIdeas.Editor.Create -> StoryIdeas.Draft(isEditing = true)
	is StoryIdeas.Editor.Edit -> StoryIdeas.Draft(
		isEditing = false,
		title = editor.idea.title.orEmpty(),
		content = editor.idea.content,
		tags = editor.idea.tags.toList(),
		savedTitle = editor.idea.title,
		savedContent = editor.idea.content,
		savedTags = editor.idea.tags,
	)
}

private fun fakeComponent(
	editor: StoryIdeas.Editor? = null,
): StoryIdeas = object : StoryIdeas {
	override val state: Value<StoryIdeas.State> = MutableValue(
		StoryIdeas.State(
			ideas = previewIdeas,
			editor = editor,
			draft = previewDraft(editor),
		)
	)

	override fun showCreate() {}
	override fun editIdea(id: IdeaId) {}
	override fun closeEditor() {}
	override fun suggestTags(prefix: String): List<String> = emptyList()
	override fun beginEdit() {}
	override fun discardEdit() {}
	override fun updateTitle(title: String) {}
	override fun updateContent(content: String) {}
	override fun updateTags(tags: List<String>) {}
	override fun updateTagDraft(tagDraft: String) {}
	override suspend fun saveDraft(): StoryIdeas.SaveResult = StoryIdeas.SaveResult.Saved
	override suspend fun deleteIdea(id: IdeaId) {}
	override suspend fun archiveIdea(id: IdeaId) {}
	override suspend fun unarchiveIdea(id: IdeaId) {}
	override suspend fun promoteIdea(id: IdeaId): CResult<ProjectDef> =
		CResult.failure(Exception("preview"))
}
