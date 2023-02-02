package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.onClick
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.painterResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun EditorAction(
	iconRes: String,
	drawableKlass: Any?,
	active: Boolean,
	onClick: () -> Unit
) {
	/*
	// https://github.com/JetBrains/compose-jb/issues/2569
	IconButton(
		onClick = onClick,
		modifier = Modifier.focusable(false)
	) {
	*/
	Box(
		modifier = Modifier
			.onClick { onClick() }
			.padding(Ui.Padding.L)
	) {
		Icon(
			modifier = Modifier.size(24.dp),
			painter = painterResource(res = iconRes, drawableKlass = drawableKlass),
			tint = if (active) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
			contentDescription = null
		)
	}
}