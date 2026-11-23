package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** The dimmed backdrop a dialog sits on, with [content] centred, for previewing dialog cards. */
@Composable
fun PreviewScrim(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
	Box(
		modifier = modifier
			.fillMaxSize()
			.previewScrim(),
		contentAlignment = Alignment.Center,
		content = content,
	)
}

/** The dimmed dialog backdrop, for previews that lay several cards out on it. */
@Composable
fun Modifier.previewScrim(): Modifier = background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
