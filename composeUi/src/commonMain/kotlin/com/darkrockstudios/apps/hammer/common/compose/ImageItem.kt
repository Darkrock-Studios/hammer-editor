package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.eygraber.uri.Uri
import com.seiko.imageloader.ImageRequestState
import com.seiko.imageloader.model.ImageRequest
import com.seiko.imageloader.rememberAsyncImagePainter

@Composable
fun ImageItem(
	path: String?,
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Fit,
	contentDescription: String? = null
) {
	val request = if (path != null) {
		val uri = Uri.parse(path)
		ImageRequest {
			data(uri)
		}
	} else {
		ImageRequest {}
	}

	ImageItem(
		modifier = modifier,
		request = request,
		contentScale = contentScale,
		contentDescription = contentDescription
	)
}

@Composable
fun ImageItem(
	modifier: Modifier = Modifier,
	request: ImageRequest,
	contentScale: ContentScale = ContentScale.Fit,
	contentDescription: String? = null
) {
	Box(modifier, Alignment.Center) {
		val painter = rememberAsyncImagePainter(request)
		Image(
			painter = painter,
			contentDescription = contentDescription,
			contentScale = contentScale,
			modifier = Modifier,
		)
		when (val requestState = painter.requestState) {
//			is ImageRequestState.Loading -> {
//				CircularProgressIndicator()
//			}

			is ImageRequestState.Failure -> {
				Text(requestState.error.message ?: MR.strings.image_failed_to_load.get())
			}

			is ImageRequestState.Loading, is ImageRequestState.Success -> {
				Image(
					painter = painter,
					contentDescription = contentDescription,
					contentScale = contentScale,
					modifier = Modifier,
				)
			}
		}
	}
}