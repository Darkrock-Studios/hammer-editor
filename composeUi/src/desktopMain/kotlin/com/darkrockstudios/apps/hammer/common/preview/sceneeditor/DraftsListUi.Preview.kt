package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.projecteditor.drafts.DraftsList
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.preview.fakeSceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.drafts.DraftsListUi
import kotlinx.datetime.Clock

@Preview
@Composable
fun DraftsListUiPreview() {
	val component = object : DraftsList {
		override val state = MutableValue(
			DraftsList.State(
				sceneItem = fakeSceneItem(),
				drafts = listOf(
					DraftDef(
						id = 3,
						sceneId = 0,
						draftTimestamp = Clock.System.now(),
						draftName = "Test Draft"
					),
					DraftDef(
						id = 4,
						sceneId = 1,
						draftTimestamp = Clock.System.now(),
						draftName = "Test Draft 2"
					),
					DraftDef(
						id = 5,
						sceneId = 2,
						draftTimestamp = Clock.System.now(),
						draftName = "Another Test Draft"
					),
				)
			)
		)

		override fun loadDrafts() {}
		override fun selectDraft(draftDef: DraftDef) {}
		override fun cancel() {}
	}


	Column {
		AppTheme {
			DraftsListUi(component)
		}
		Spacer(modifier = Modifier.size(16.dp))
		AppTheme(true) {
			DraftsListUi(component)
		}
	}
}