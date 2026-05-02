package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun EditorAction(
	icon: ImageVector,
	active: Boolean,
	onClick: () -> Unit
) {

	val painter = rememberVectorPainter(icon)

	IconButton(onClick = onClick) {
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
	IconButton(onClick = onClick) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			color = if (active) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
