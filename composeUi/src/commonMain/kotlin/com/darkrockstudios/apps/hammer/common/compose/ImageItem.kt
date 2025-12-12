package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun ImageItem(
	path: String?,
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Fit,
	contentDescription: String? = null
) {
	Box(modifier, Alignment.Center) {
		AsyncImage(
			model = path,
			contentDescription = contentDescription,
			contentScale = contentScale,
			modifier = Modifier,
		)
	}
}
