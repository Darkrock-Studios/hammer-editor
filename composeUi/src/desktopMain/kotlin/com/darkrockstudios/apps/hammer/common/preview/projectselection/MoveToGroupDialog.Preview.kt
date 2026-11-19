package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.components.projectselection.MoveTarget
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import com.darkrockstudios.apps.hammer.common.projectselection.MoveToGroupContent

@Preview(widthDp = 600, heightDp = 600)
@Composable
fun MoveToGroupDialogPreview() {
	val fantasy = previewGroup("Fantasy")
	val groups = listOf(fantasy, previewGroup("Trilogy", parent = fantasy), previewGroup("Short stories"))
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		MoveToGroupContent(
			target = MoveTarget.Project(fakeProjectData().definition),
			groups = groups,
			placement = emptyMap(),
			onMove = {},
			onCancel = {},
		)
	}
}
