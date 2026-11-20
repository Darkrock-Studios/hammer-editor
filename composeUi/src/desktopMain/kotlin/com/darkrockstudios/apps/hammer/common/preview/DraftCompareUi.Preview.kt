package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.diff.ProseDiff
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftCompare
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.storyeditor.drafts.DraftCompareUi
import kotlin.time.Instant

private const val DRAFT_TEXT =
	"The keeper steps off the ferry into a town that knows his name.\n\n" +
		"Nobody meets him at the quay. The lamp on the point is dark."

private const val CURRENT_TEXT =
	"The keeper steps off the ferry into a town that already knows his name.\n\n" +
		"Nobody meets him at the quay. Up on the point, the great lamp is dark."

private class PreviewDraftCompare : DraftCompare {
	override val sceneItem = fakeSceneItem()
	override val draftDef = DraftDef(
		id = 2,
		sceneId = 0,
		draftTimestamp = Instant.parse("2026-11-09T21:40:00Z"),
		draftName = "First pass",
	)
	override val state = MutableValue(
		DraftCompare.State(
			sceneItem = sceneItem,
			draftDef = draftDef,
			sceneContent = SceneContent(sceneItem, CURRENT_TEXT),
			draftContent = SceneContent(sceneItem, DRAFT_TEXT),
			diffResult = ProseDiff.diffPlain(DRAFT_TEXT, CURRENT_TEXT),
		)
	)

	private var draftText = DRAFT_TEXT
	private var currentText = CURRENT_TEXT

	override fun loadContents() {}
	override fun onMergedContentChanged(richText: PlatformRichText) {}

	override fun submitDraftText(text: String) {
		draftText = text
		state.update { it.copy(diffResult = ProseDiff.diffPlain(draftText, currentText)) }
	}

	override fun onCurrentTextChanged(text: String) {
		currentText = text
		state.update { it.copy(diffResult = ProseDiff.diffPlain(draftText, currentText)) }
	}

	override fun setShowDiff(show: Boolean) {
		state.update { it.copy(showDiff = show) }
	}

	override fun pickDraft() {}
	override fun pickMerged() {}
	override fun cancel() {}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenDraftCompareUiTabletPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			DraftCompareUi(PreviewDraftCompare())
		}
	}
}
