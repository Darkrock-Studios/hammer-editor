package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.runtime.Composable

@Composable
internal expect fun EditorAction(
	iconRes: String,
	drawableKlass: Any? = null,
	active: Boolean,
	onClick: () -> Unit,
)