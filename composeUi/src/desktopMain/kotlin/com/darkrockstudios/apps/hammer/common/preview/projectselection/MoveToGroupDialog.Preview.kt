package com.darkrockstudios.apps.hammer.common.preview.projectselection

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.components.projectselection.MoveTarget
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import com.darkrockstudios.apps.hammer.common.projectselection.MoveToGroupContent

@Preview(widthDp = 600, heightDp = 600)
@Composable
fun MoveToGroupDialogPreview() {
	val fantasy = previewGroup("Fantasy")
	val groups = listOf(fantasy, previewGroup("Trilogy", parent = fantasy), previewGroup("Short stories"))
	PreviewScrim {
		MoveToGroupContent(
			target = MoveTarget.Project(fakeProjectData().definition),
			groups = groups,
			placement = emptyMap(),
			onMove = {},
			onCancel = {},
		)
	}
}
