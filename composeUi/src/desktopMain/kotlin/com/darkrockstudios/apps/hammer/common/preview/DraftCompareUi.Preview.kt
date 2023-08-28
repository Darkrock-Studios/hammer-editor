package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.projecteditor.drafts.DraftCompare
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.projecteditor.drafts.DraftCompareUi
import kotlinx.datetime.Clock

@Preview
@Composable
private fun DraftCompareUiPreview() = koinForPreview {
	val draftDef = DraftDef(
		id = 2,
		sceneId = 1,
		draftTimestamp = Clock.System.now(),
		draftName = "Test Draft"
	)
	val sceneItem = fakeSceneItem()

	val comp = object : DraftCompare {
		override val sceneItem = sceneItem
		override val draftDef = draftDef
		override val state = MutableValue(
			DraftCompare.State(
				sceneItem = sceneItem,
				draftDef = draftDef,
				sceneContent = null,
				draftContent = null
			)
		)

		override fun loadContents() {}
		override fun pickDraft() {}
		override fun pickMerged() {}
		override fun cancel() {}
	}
	DraftCompareUi(comp)
}