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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
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
import me.saket.telephoto.zoomable.HardwareShortcutDetector
import me.saket.telephoto.zoomable.HardwareShortcutsSpec
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import kotlin.math.abs

const val IMAGE_VIEWER_TAG = "image-viewer"
const val IMAGE_VIEWER_CLOSE_TAG = "image-viewer-close"
const val IMAGE_VIEWER_COUNTER_TAG = "image-viewer-counter"
const val IMAGE_VIEWER_LABEL_TAG = "image-viewer-label"
fun imageViewerPageTag(index: Int) = "image-viewer-page-$index"

private val ViewerBackground = Color(0xFF0D0D0D)
private val ViewerForeground = Color(0xFFEDEDED)
private const val MAX_ZOOM = 5f

/** Telephoto's shortcuts, with the wheel zooming only while Ctrl or Cmd is held: a plain wheel pages. */
private object WheelZoomsWithModifier : HardwareShortcutDetector {
	override fun detectKey(event: KeyEvent): HardwareShortcutDetector.ShortcutEvent? =
		HardwareShortcutDetector.Default.detectKey(event)

	override fun detectScroll(event: PointerEvent): HardwareShortcutDetector.ShortcutEvent? =
		if (event.keyboardModifiers.zoomsWithWheel) HardwareShortcutDetector.Default.detectScroll(event) else null
}

private val PointerKeyboardModifiers.zoomsWithWheel: Boolean get() = isCtrlPressed || isMetaPressed

/**
 * Turns a plain wheel notch into a page turn, ahead of the zoomable image below, which gets a
 * wheel only with a modifier held.
 */
private fun Modifier.wheelPaging(onPage: (forward: Boolean) -> Unit): Modifier = pointerInput(onPage) {
	awaitPointerEventScope {
		while (true) {
			val event = awaitPointerEvent(PointerEventPass.Initial)
			if (event.type != PointerEventType.Scroll || event.keyboardModifiers.zoomsWithWheel) continue
			val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
			val along = if (abs(delta.x) > abs(delta.y)) delta.x else delta.y
			if (along != 0f) {
				onPage(along > 0f)
				event.changes.forEach { it.consume() }
			}
		}
	}
}

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
 * Full-screen lightbox over [items], opening at [initialIndex]: pinch, Ctrl and the wheel, or a
 * double-click to zoom; swipe, the wheel or the arrow keys to page. Local composable state rather
 * than a navigation slot, so it can open over other modals.
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
				}
				.wheelPaging(
					onPage = { forward ->
						if (!pagerState.isScrollInProgress) {
							showPage(pagerState.currentPage + if (forward) 1 else -1)
						}
					},
				),
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
	val zoomState = rememberZoomableState(
		zoomSpec = ZoomSpec(maxZoomFactor = MAX_ZOOM),
		hardwareShortcutsSpec = HardwareShortcutsSpec(shortcutDetector = WheelZoomsWithModifier),
	)

	val loaded = painterState as? AsyncImagePainter.State.Success
	LaunchedEffect(loaded) {
		val size = loaded?.painter?.intrinsicSize ?: return@LaunchedEffect
		// The location has to say how the Image draws its painter, Fit here: told the content is
		// scaled inside instead, the zoom treated a small image as drawn at its own size and
		// enlarged what was already fitted.
		zoomState.setContentLocation(ZoomableContentLocation.scaledToFitAndCenterAligned(size))
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
