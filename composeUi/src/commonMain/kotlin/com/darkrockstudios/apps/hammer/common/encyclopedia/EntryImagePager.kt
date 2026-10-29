package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEngravingPlaceholder
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.fileDropTarget
import com.darkrockstudios.apps.hammer.common.compose.mouseDragPaging
import com.darkrockstudios.apps.hammer.common.compose.images.rememberImageRequest
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.encyclopedia_entry_figure_label
import com.darkrockstudios.apps.hammer.encyclopedia_entry_image_actions
import com.darkrockstudios.apps.hammer.encyclopedia_entry_image_counter
import com.darkrockstudios.apps.hammer.encyclopedia_entry_image_page_description
import com.darkrockstudios.apps.hammer.encyclopedia_entry_image_remove
import com.darkrockstudios.apps.hammer.encyclopedia_entry_image_set_primary
import com.darkrockstudios.apps.hammer.images_entry_from_library
import com.darkrockstudios.apps.hammer.images_entry_import_file
import com.darkrockstudios.apps.hammer.images_entry_menu_from_library
import com.darkrockstudios.apps.hammer.images_entry_menu_import_file
import io.github.vinceglb.filekit.PlatformFile

const val ENTRY_IMAGE_PAGER_TAG = "entry-image-pager"
const val ENTRY_IMAGE_ADD_TAG = "entry-image-add"
const val ENTRY_IMAGE_LIBRARY_TAG = "entry-image-library"
const val ENTRY_IMAGE_REMOVE_TAG = "entry-image-remove"
const val ENTRY_IMAGE_SET_PRIMARY_TAG = "entry-image-set-primary"
const val ENTRY_IMAGE_COUNTER_TAG = "entry-image-counter"
const val ENTRY_IMAGE_MENU_TAG = "entry-image-menu"
fun entryImagePageTag(id: Int) = "entry-image-page-$id"

/**
 * An entry's images, one page each, inside the inset figure frame. [currentIndex] is the image on
 * show and drives the bar's counter and actions; the pager scrolls to follow it, and a swipe
 * reports the new page through [onShowIndex]. [onAdd] imports a file; [onAddFromLibrary] picks an
 * image already in the project; tapping a page calls [onOpenImage] with its index.
 * [primaryImageModifier] goes on the first image only, for the shared element from the browse card.
 */
@Composable
internal fun EntryImagePager(
	entryName: String,
	images: List<ViewEntry.EntryImage>,
	currentIndex: Int,
	onShowIndex: (Int) -> Unit,
	onAdd: () -> Unit,
	onAddFromLibrary: () -> Unit,
	onFilesDropped: (List<PlatformFile>) -> Unit,
	onSetPrimary: (imageId: Int) -> Unit,
	onRemove: (imageId: Int) -> Unit,
	modifier: Modifier = Modifier,
	onOpenImage: (index: Int) -> Unit = {},
	primaryImageModifier: Modifier = Modifier,
) {
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	var dragActive by remember { mutableStateOf(false) }

	Box(
		modifier = modifier
			.border(
				width = if (dragActive) 1.dp else Dp.Hairline,
				color = if (dragActive) MaterialTheme.colorScheme.primary else ruleColor,
				shape = RectangleShape,
			)
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.fileDropTarget(
				enabled = true,
				extensions = ImageFormat.ALLOWED_EXTENSIONS,
				onDragChange = { dragActive = it },
				onFilesDropped = onFilesDropped,
			),
	) {
		if (images.isEmpty()) {
			EmptyFigure(entryName = entryName, onAdd = onAdd, onAddFromLibrary = onAddFromLibrary)
		} else {
			Column(modifier = Modifier.fillMaxSize()) {
				val shownIndex = currentIndex.coerceIn(0, images.size - 1)
				val pagerState = rememberPagerState(
					initialPage = shownIndex,
					pageCount = { images.size },
				)
				val latestOnShowIndex by rememberUpdatedState(onShowIndex)
				LaunchedEffect(currentIndex, images.size) {
					if (currentIndex in images.indices && pagerState.currentPage != currentIndex) {
						pagerState.scrollToPage(currentIndex)
					}
				}
				LaunchedEffect(pagerState) {
					snapshotFlow { pagerState.currentPage }.collect { latestOnShowIndex(it) }
				}

				HorizontalPager(
					state = pagerState,
					key = { page -> images.getOrNull(page)?.id ?: "missing-$page" },
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f)
						.mouseDragPaging(pagerState)
						.testTag(ENTRY_IMAGE_PAGER_TAG),
				) { page ->
					val image = images.getOrNull(page) ?: return@HorizontalPager
					AsyncImage(
						model = rememberImageRequest(
							path = image.path,
							version = image.hash,
							placeholderFromPath = true,
							crossfadeMillis = 0,
						),
						contentDescription = Res.string.encyclopedia_entry_image_page_description.get(
							page + 1,
							images.size,
							image.name,
						),
						modifier = Modifier
							.fillMaxSize()
							.testTag(entryImagePageTag(image.id))
							.clickable { onOpenImage(page) }
							.then(if (page == 0) primaryImageModifier else Modifier),
						contentScale = ContentScale.Fit,
					)
				}

				HorizontalDivider(thickness = Dp.Hairline, color = ruleColor)
				FigureBar(
					images = images,
					shownIndex = shownIndex,
					onShowIndex = onShowIndex,
					onAdd = onAdd,
					onAddFromLibrary = onAddFromLibrary,
					onSetPrimary = onSetPrimary,
					onRemove = onRemove,
				)
			}
		}
	}
}

@Composable
private fun FigureBar(
	images: List<ViewEntry.EntryImage>,
	shownIndex: Int,
	onShowIndex: (Int) -> Unit,
	onAdd: () -> Unit,
	onAddFromLibrary: () -> Unit,
	onSetPrimary: (imageId: Int) -> Unit,
	onRemove: (imageId: Int) -> Unit,
) {
	val shown = images[shownIndex]
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = Ui.Padding.L),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (images.size > 1) {
			HdMonoLabel(
				text = Res.string.encyclopedia_entry_image_counter.get(shownIndex + 1, images.size),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.testTag(ENTRY_IMAGE_COUNTER_TAG),
			)
			PageDots(count = images.size, selected = shownIndex, onSelect = onShowIndex)
		}
		Spacer(modifier = Modifier.weight(1f))
		FigureMenu(
			canSetPrimary = shownIndex > 0,
			onAddFromLibrary = onAddFromLibrary,
			onAdd = onAdd,
			onSetPrimary = { onSetPrimary(shown.id) },
			onRemove = { onRemove(shown.id) },
		)
	}
}

@Composable
private fun FigureMenu(
	canSetPrimary: Boolean,
	onAddFromLibrary: () -> Unit,
	onAdd: () -> Unit,
	onSetPrimary: () -> Unit,
	onRemove: () -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		IconButton(
			onClick = { expanded = true },
			modifier = Modifier.testTag(ENTRY_IMAGE_MENU_TAG),
		) {
			Icon(
				imageVector = Icons.Filled.MoreVert,
				contentDescription = Res.string.encyclopedia_entry_image_actions.get(),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			fun closing(action: () -> Unit): () -> Unit = {
				expanded = false
				action()
			}

			FigureMenuItem(
				label = Res.string.images_entry_menu_from_library.get(),
				tag = ENTRY_IMAGE_LIBRARY_TAG,
				onClick = closing(onAddFromLibrary),
			)
			FigureMenuItem(
				label = Res.string.images_entry_menu_import_file.get(),
				tag = ENTRY_IMAGE_ADD_TAG,
				onClick = closing(onAdd),
			)
			if (canSetPrimary) {
				FigureMenuItem(
					label = Res.string.encyclopedia_entry_image_set_primary.get(),
					tag = ENTRY_IMAGE_SET_PRIMARY_TAG,
					onClick = closing(onSetPrimary),
				)
			}
			FigureMenuItem(
				label = Res.string.encyclopedia_entry_image_remove.get(),
				tag = ENTRY_IMAGE_REMOVE_TAG,
				onClick = closing(onRemove),
			)
		}
	}
}

@Composable
private fun FigureMenuItem(label: String, tag: String, onClick: () -> Unit) {
	DropdownMenuItem(
		text = {
			Text(
				text = label,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		},
		onClick = onClick,
		modifier = Modifier.testTag(tag),
	)
}

@Composable
private fun PageDots(count: Int, selected: Int, onSelect: (Int) -> Unit) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
	) {
		repeat(count) { index ->
			Box(
				modifier = Modifier
					.clickable { onSelect(index) }
					.padding(2.dp)
					.size(6.dp)
					.background(
						color = if (index == selected) {
							MaterialTheme.colorScheme.primary
						} else {
							MaterialTheme.colorScheme.outlineVariant
						},
						shape = CircleShape,
					),
			)
		}
	}
}

@Composable
private fun FigureAction(label: String, onClick: () -> Unit, tag: String) {
	HdMonoLabel(
		text = label,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		maxLines = 1,
		softWrap = false,
		modifier = Modifier
			.testTag(tag)
			.clickable(onClick = onClick)
			.padding(vertical = 2.dp),
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyFigure(entryName: String, onAdd: () -> Unit, onAddFromLibrary: () -> Unit) {
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	Column(modifier = Modifier.fillMaxSize()) {
		HdEngravingPlaceholder(
			label = entryName.uppercase(),
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.clickable(onClick = onAdd),
		)
		HorizontalDivider(thickness = Dp.Hairline, color = ruleColor)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.M),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(
				text = Res.string.encyclopedia_entry_figure_label.get(),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L, Alignment.End),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.S),
			) {
				FigureAction(
					label = Res.string.images_entry_from_library.get(),
					onClick = onAddFromLibrary,
					tag = ENTRY_IMAGE_LIBRARY_TAG,
				)
				FigureAction(
					label = Res.string.images_entry_import_file.get(),
					onClick = onAdd,
					tag = ENTRY_IMAGE_ADD_TAG,
				)
			}
		}
	}
}
