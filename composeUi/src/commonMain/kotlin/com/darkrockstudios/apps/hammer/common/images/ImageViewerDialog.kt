package com.darkrockstudios.apps.hammer.common.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.AnimatedFullScreenDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.images.VIEWER_CACHE_KEY_PREFIX
import com.darkrockstudios.apps.hammer.common.compose.matchesShortcut
import com.darkrockstudios.apps.hammer.common.compose.mouseDragPaging
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.images_viewer_close
import com.darkrockstudios.apps.hammer.images_viewer_counter
import com.darkrockstudios.apps.hammer.images_viewer_gif_badge
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

const val IMAGE_VIEWER_TAG = "image-viewer"
const val IMAGE_VIEWER_CLOSE_TAG = "image-viewer-close"
const val IMAGE_VIEWER_COUNTER_TAG = "image-viewer-counter"
const val IMAGE_VIEWER_LABEL_TAG = "image-viewer-label"
fun imageViewerPageTag(index: Int) = "image-viewer-page-$index"

private val ViewerBackground = Color(0xFF0D0D0D)
private val ViewerForeground = Color(0xFFEDEDED)
private const val MAX_ZOOM = 5f

/**
 * One image in [ImageViewerDialog]. [model] is a file path or the image's bytes; [cacheKey] names
 * this version of it in the memory cache, so it must change whenever the bytes do.
 */
data class ImageViewerItem(
	val model: Any,
	val cacheKey: String,
	val label: String,
	val isGif: Boolean = false,
) {
	companion object {
		/** The image file at [path]; [version] changes whenever its bytes do, such as its content hash. */
		fun ofFile(path: String, version: String?, label: String) = ImageViewerItem(
			model = path,
			cacheKey = "$path#$version",
			label = label,
			isGif = path.substringAfterLast('.', "").equals("gif", ignoreCase = true),
		)
	}
}

/**
 * Full-screen lightbox over [items], opening at [initialIndex]: pinch, scroll or double-click to
 * zoom, swipe or the arrow keys to page. Local composable state rather than a navigation slot, so
 * it can open over other modals.
 */
@Composable
fun ImageViewerDialog(
	items: List<ImageViewerItem>,
	initialIndex: Int,
	onDismissed: () -> Unit,
) {
	if (items.isEmpty()) return

	AnimatedFullScreenDialog(
		onDismissed = onDismissed,
		backgroundColor = ViewerBackground,
	) {
		val scope = rememberCoroutineScope()
		val pagerState = rememberPagerState(
			initialPage = initialIndex.coerceIn(0, items.size - 1),
			pageCount = { items.size },
		)
		val focusRequester = remember { FocusRequester() }
		LaunchedEffect(Unit) { focusRequester.requestFocus() }

		fun showPage(page: Int) {
			if (page in items.indices) scope.launch { pagerState.animateScrollToPage(page) }
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.testTag(IMAGE_VIEWER_TAG)
				.focusRequester(focusRequester)
				.focusable()
				.onPreviewKeyEvent { event ->
					when {
						event.matchesShortcut(Key.DirectionLeft) -> {
							showPage(pagerState.currentPage - 1)
							true
						}

						event.matchesShortcut(Key.DirectionRight) -> {
							showPage(pagerState.currentPage + 1)
							true
						}

						else -> false
					}
				},
		) {
			HorizontalPager(
				state = pagerState,
				beyondViewportPageCount = 1,
				key = { page -> items.getOrNull(page)?.cacheKey ?: "missing-$page" },
				modifier = Modifier
					.fillMaxSize()
					.mouseDragPaging(pagerState),
			) { page ->
				val item = items.getOrNull(page) ?: return@HorizontalPager
				ViewerPage(
					item = item,
					isCurrent = pagerState.currentPage == page,
					modifier = Modifier.testTag(imageViewerPageTag(page)),
				)
			}

			IconButton(
				onClick = { requestDismiss() },
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(Ui.Padding.L)
					.testTag(IMAGE_VIEWER_CLOSE_TAG),
			) {
				Icon(
					imageVector = Icons.Default.Close,
					contentDescription = Res.string.images_viewer_close.get(),
					tint = ViewerForeground,
				)
			}

			val current = items[pagerState.currentPage.coerceIn(0, items.size - 1)]
			Footer(
				item = current,
				index = pagerState.currentPage,
				count = items.size,
				modifier = Modifier.align(Alignment.BottomCenter),
			)
		}
	}
}

@Composable
private fun ViewerPage(item: ImageViewerItem, isCurrent: Boolean, modifier: Modifier = Modifier) {
	val context = LocalPlatformContext.current
	val request = remember(context, item.cacheKey) {
		ImageRequest.Builder(context)
			.data(item.model)
			// A full-screen decode, kept apart from the thumbnail entries for the same file.
			.memoryCacheKey(VIEWER_CACHE_KEY_PREFIX + item.cacheKey)
			.build()
	}
	val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Fit)
	val painterState by painter.state.collectAsState()
	val zoomState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MAX_ZOOM))

	val loaded = painterState as? AsyncImagePainter.State.Success
	LaunchedEffect(loaded) {
		val size = loaded?.painter?.intrinsicSize ?: return@LaunchedEffect
		zoomState.setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(size))
	}
	// A page is laid out before it is shown, and a zoom from then or from a mouse wheel over
	// a neighbouring page would otherwise carry over: each image opens fitted inside.
	LaunchedEffect(isCurrent, loaded) {
		if (isCurrent && loaded != null) zoomState.resetZoom(withAnimation = false)
	}

	Image(
		painter = painter,
		contentDescription = item.label,
		contentScale = ContentScale.Fit,
		modifier = modifier
			.fillMaxSize()
			.zoomable(zoomState),
	)
}

@Composable
private fun Footer(item: ImageViewerItem, index: Int, count: Int, modifier: Modifier = Modifier) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.background(ViewerBackground.copy(alpha = 0.7f))
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Text(
			text = item.label,
			style = MaterialTheme.typography.bodyMedium,
			color = ViewerForeground,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier
				.weight(1f)
				.testTag(IMAGE_VIEWER_LABEL_TAG),
		)
		if (item.isGif) {
			HdMonoLabel(
				text = Res.string.images_viewer_gif_badge.get(),
				color = ViewerForeground,
				modifier = Modifier
					.border(Dp.Hairline, ViewerForeground.copy(alpha = 0.6f), RectangleShape)
					.padding(horizontal = Ui.Padding.S),
			)
		}
		if (count > 1) {
			HdMonoLabel(
				text = Res.string.images_viewer_counter.get(index + 1, count),
				color = ViewerForeground.copy(alpha = 0.8f),
				modifier = Modifier.testTag(IMAGE_VIEWER_COUNTER_TAG),
			)
		}
	}
}
