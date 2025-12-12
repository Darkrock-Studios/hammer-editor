package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.onClick
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun EditorAction(
	icon: ImageVector,
	active: Boolean,
	onClick: () -> Unit
) {
	val painter = rememberVectorPainter(icon)

	Box(
		modifier = Modifier
			.onClick { onClick() }
			.padding(Ui.Padding.L)
	) {
		Icon(
			modifier = Modifier.size(24.dp),
			painter = painter,
			tint = if (active) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
			contentDescription = null
		)
	}
}
