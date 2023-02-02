package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.painterResource

@Composable
internal actual fun EditorAction(
	iconRes: String,
	drawableKlass: Any?,
	active: Boolean,
	onClick: () -> Unit
) {
	IconButton(onClick = onClick) {
		Icon(
			modifier = Modifier.size(24.dp),
			painter = painterResource(res = iconRes, drawableKlass = drawableKlass),
			tint = if (active) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
			contentDescription = null
		)
	}
}