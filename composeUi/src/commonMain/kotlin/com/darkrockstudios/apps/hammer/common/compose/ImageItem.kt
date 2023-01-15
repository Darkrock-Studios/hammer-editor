package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.eygraber.uri.Uri
import com.seiko.imageloader.ImageRequestState
import com.seiko.imageloader.rememberAsyncImagePainter
import com.seiko.imageloader.request.ImageRequest
import com.seiko.imageloader.request.ImageRequestBuilder


@Composable
fun ImageItem(
	path: String?,
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Fit,
	contentDescription: String? = null
) {
	Box(modifier.aspectRatio(1f), Alignment.Center) {

		val request = if (path != null) {
			val uri = Uri.parse(path)
			ImageRequestBuilder()
				.data(uri)
				.build()
		} else {
			ImageRequestBuilder()
				.data(null)
				.build()
		}

		ImageItem(
			request = request,
			contentScale = contentScale,
			contentDescription = contentDescription
		)
	}
}

@Composable
fun ImageItem(
	modifier: Modifier = Modifier,
	request: ImageRequest,
	contentScale: ContentScale = ContentScale.Fit,
	contentDescription: String? = null
) {
	Box(modifier.aspectRatio(1f), Alignment.Center) {
		val painter = rememberAsyncImagePainter(request)
		Image(
			painter = painter,
			contentDescription = contentDescription,
			contentScale = contentScale,
			modifier = Modifier.fillMaxSize(),
		)
		when (val requestState = painter.requestState) {
			ImageRequestState.Loading -> {
				CircularProgressIndicator()
			}

			is ImageRequestState.Failure -> {
				Text(requestState.error.message ?: "Image Error")
			}

			ImageRequestState.Success -> Unit
		}
	}
}