package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
internal actual fun EditorAction(
	icon: ImageVector,
	active: Boolean,
	onClick: () -> Unit
) {
	val painter = rememberVectorPainter(icon)

	Box(
		modifier = Modifier
			.clickable { onClick() }
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

@Composable
internal actual fun EditorTextAction(
	label: String,
	active: Boolean,
	onClick: () -> Unit,
) {
	Box(
		modifier = Modifier
			.clickable { onClick() }
			.padding(Ui.Padding.L)
			.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			color = if (active) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
