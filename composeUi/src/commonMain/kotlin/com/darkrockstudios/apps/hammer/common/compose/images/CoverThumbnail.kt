package com.darkrockstudios.apps.hammer.common.compose.images

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/** Width over height of a book cover thumbnail. */
const val COVER_ASPECT_RATIO = 2f / 3f

/**
 * A book cover cropped to the frame [modifier] gives it, with a hairline border. [version] changes
 * whenever the file's bytes do (see [rememberImageRequest]).
 */
@Composable
fun CoverThumbnail(
	path: String,
	version: String?,
	contentDescription: String?,
	modifier: Modifier = Modifier,
) {
	AsyncImage(
		model = rememberImageRequest(path = path, version = version),
		contentDescription = contentDescription,
		contentScale = ContentScale.Crop,
		modifier = modifier
			.background(MaterialTheme.colorScheme.surfaceContainerHigh, RectangleShape)
			.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
	)
}
