package com.darkrockstudios.apps.hammer.common.images

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.components.images.ImageGallery
import com.darkrockstudios.apps.hammer.common.components.images.ImageGalleryMode
import com.darkrockstudios.apps.hammer.common.compose.AnimatedFullScreenDialog
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEngravingPlaceholder
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntityId
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPrimaryAction
import com.darkrockstudios.apps.hammer.common.compose.fileDropTarget
import com.darkrockstudios.apps.hammer.common.compose.images.StagedImage
import com.darkrockstudios.apps.hammer.common.compose.images.evictImageFile
import com.darkrockstudios.apps.hammer.common.compose.images.imageAddResultMessage
import com.darkrockstudios.apps.hammer.common.compose.images.pickImageFile
import com.darkrockstudios.apps.hammer.common.compose.images.rememberImageRequest
import com.darkrockstudios.apps.hammer.common.compose.images.stageImage
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsages
import com.darkrockstudios.apps.hammer.images_gallery_add
import com.darkrockstudios.apps.hammer.images_gallery_cancel
import com.darkrockstudios.apps.hammer.images_gallery_close
import com.darkrockstudios.apps.hammer.images_gallery_delete
import com.darkrockstudios.apps.hammer.images_gallery_delete_title
import com.darkrockstudios.apps.hammer.images_gallery_delete_unused
import com.darkrockstudios.apps.hammer.images_gallery_delete_usage_line
import com.darkrockstudios.apps.hammer.images_gallery_delete_used
import com.darkrockstudios.apps.hammer.images_gallery_drop_hint
import com.darkrockstudios.apps.hammer.images_gallery_empty_message
import com.darkrockstudios.apps.hammer.images_gallery_empty_title
import com.darkrockstudios.apps.hammer.images_gallery_empty_unused
import com.darkrockstudios.apps.hammer.images_gallery_filter_all
import com.darkrockstudios.apps.hammer.images_gallery_filter_unused
import com.darkrockstudios.apps.hammer.images_gallery_pick_section
import com.darkrockstudios.apps.hammer.images_gallery_rename
import com.darkrockstudios.apps.hammer.images_gallery_rename_button
import com.darkrockstudios.apps.hammer.images_gallery_rename_label
import com.darkrockstudios.apps.hammer.images_gallery_rename_title
import com.darkrockstudios.apps.hammer.images_gallery_replace
import com.darkrockstudios.apps.hammer.images_gallery_section
import com.darkrockstudios.apps.hammer.images_gallery_unused_badge
import com.darkrockstudios.apps.hammer.images_gallery_use
import com.darkrockstudios.apps.hammer.images_gallery_view
import com.darkrockstudios.apps.hammer.images_toast_name_invalid
import com.darkrockstudios.apps.hammer.images_usage_type_draft
import com.darkrockstudios.apps.hammer.images_usage_type_entry
import com.darkrockstudios.apps.hammer.images_usage_type_event
import com.darkrockstudios.apps.hammer.images_usage_type_image
import com.darkrockstudios.apps.hammer.images_usage_type_note
import com.darkrockstudios.apps.hammer.images_usage_type_scene
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

const val IMAGE_GALLERY_TAG = "image-gallery"
const val IMAGE_GALLERY_ADD_TAG = "image-gallery-add"
const val IMAGE_GALLERY_FILTER_TAG = "image-gallery-filter"
const val IMAGE_GALLERY_CLOSE_TAG = "image-gallery-close"
const val IMAGE_GALLERY_EMPTY_TAG = "image-gallery-empty"
const val IMAGE_GALLERY_DETAIL_TAG = "image-gallery-detail"
const val IMAGE_GALLERY_USE_TAG = "image-gallery-use"
const val IMAGE_GALLERY_VIEW_TAG = "image-gallery-view"
const val IMAGE_GALLERY_RENAME_TAG = "image-gallery-rename"
const val IMAGE_GALLERY_REPLACE_TAG = "image-gallery-replace"
const val IMAGE_GALLERY_DELETE_TAG = "image-gallery-delete"
fun imageGalleryItemTag(id: Int) = "image-gallery-item-$id"

private val WideGalleryThreshold = 720.dp
private val DetailPaneWidth = 320.dp
private val ThumbnailMinSize = 140.dp

/** The gallery as the project root's modal: full screen, closing through the exit animation. */
@Composable
fun ImageGalleryModalContent(component: ImageGallery) {
	// A pick is delivered once the exit animation has played, through the same path as a close.
	val picked = remember(component) { mutableStateOf<Int?>(null) }
	AnimatedFullScreenDialog(
		onDismissed = {
			val id = picked.value
			if (id != null) component.pick(id) else component.dismiss()
		},
	) {
		val animatedComponent = remember(component, this) {
			object : ImageGallery by component {
				override fun dismiss() = requestDismiss()
				override fun pick(id: Int) {
					picked.value = id
					requestDismiss()
				}
			}
		}
		ImageGalleryUi(animatedComponent)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageGalleryUi(component: ImageGallery, modifier: Modifier = Modifier) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val dispatcherIo = rememberIoDispatcher()
	val snackbar = remember { SnackbarHostState() }
	val imageLoader = SingletonImageLoader.get(LocalPlatformContext.current)
	val pickMode = state.mode as? ImageGalleryMode.Pick

	var viewer by remember { mutableStateOf<Pair<List<ImageViewerItem>, Int>?>(null) }
	var dragActive by remember { mutableStateOf(false) }

	fun openViewer(image: ImageGallery.GalleryImage) {
		val shown = state.visibleImages
		val index = shown.indexOfFirst { it.def.id == image.def.id }.coerceAtLeast(0)
		viewer = shown.map { ImageViewerItem.ofFile(it.path, it.cacheToken, it.def.name) } to index
	}

	suspend fun report(result: ImageAddResult) {
		imageAddResultMessage(result, strRes)?.let { snackbar.showSnackbar(it) }
	}

	/** The picked [file] copied where the image service can read it, or null after reporting why not. */
	suspend fun stage(file: PlatformFile): String? = when (val staged = file.stageImage(dispatcherIo)) {
		is StagedImage.Staged -> staged.path
		is StagedImage.Failed -> {
			report(staged.result)
			null
		}
	}

	suspend fun addFile(file: PlatformFile) {
		val path = stage(file) ?: return
		report(component.addImage(path))
	}

	val onAdd: () -> Unit = { scope.launch { pickImageFile(::report)?.let { addFile(it) } } }
	val onReplace: (ImageGallery.GalleryImage) -> Unit = { image ->
		scope.launch {
			val file = pickImageFile(::report) ?: return@launch
			val path = stage(file) ?: return@launch
			val result = component.replaceImage(image.def.id, path)
			if (result == ImageAddResult.Ok) imageLoader.evictImageFile(image.path)
			report(result)
		}
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
			.testTag(IMAGE_GALLERY_TAG),
	) {
		Column(modifier = Modifier.fillMaxSize()) {
			HdMasthead(
				section = if (pickMode != null) {
					Res.string.images_gallery_pick_section.get()
				} else {
					Res.string.images_gallery_section.get()
				},
				leadingMeta = listOf(state.images.size.toString()),
				trailing = {
					HdMastheadAction(
						label = if (state.filterUnused) {
							Res.string.images_gallery_filter_all.get()
						} else {
							Res.string.images_gallery_filter_unused.get()
						},
						onClick = component::toggleFilterUnused,
						modifier = Modifier.testTag(IMAGE_GALLERY_FILTER_TAG),
					)
					HdMastheadAction(
						label = Res.string.images_gallery_add.get(),
						onClick = onAdd,
						modifier = Modifier.testTag(IMAGE_GALLERY_ADD_TAG),
					)
					HdMastheadAction(
						label = Res.string.images_gallery_close.get(),
						onClick = component::dismiss,
						modifier = Modifier.testTag(IMAGE_GALLERY_CLOSE_TAG),
					)
				},
			)
			HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			if (state.busy) {
				LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
			}

			BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
				val wide = maxWidth >= WideGalleryThreshold
				val grid: @Composable (Modifier) -> Unit = { gridModifier ->
					GalleryGrid(
						state = state,
						dragActive = dragActive,
						onSelect = { image ->
							if (state.selectedId == image.def.id && pickMode == null) {
								openViewer(image)
							} else {
								component.select(image.def.id)
							}
						},
						onDoubleClick = { image ->
							if (pickMode != null) component.pick(image.def.id) else openViewer(image)
						},
						onAdd = onAdd,
						modifier = gridModifier.fileDropTarget(
							enabled = true,
							extensions = ImageFormat.ALLOWED_EXTENSIONS,
							onDragChange = { dragActive = it },
							onFilesDropped = { files -> scope.launch { files.forEach { addFile(it) } } },
						),
					)
				}
				val selected = state.selected
				val detail: @Composable (Modifier) -> Unit = { detailModifier ->
					if (selected != null) {
						ImageDetail(
							image = selected,
							pickMode = pickMode != null,
							onView = { openViewer(selected) },
							onRename = { component.startRename(selected.def.id) },
							onReplace = { onReplace(selected) },
							onDelete = { component.requestDelete(selected.def.id) },
							onUse = { component.pick(selected.def.id) },
							modifier = detailModifier,
						)
					}
				}

				if (wide) {
					Row(modifier = Modifier.fillMaxSize()) {
						grid(Modifier.weight(1f).fillMaxHeight())
						if (selected != null) {
							VerticalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
							detail(Modifier.width(DetailPaneWidth).fillMaxHeight())
						}
					}
				} else {
					Column(modifier = Modifier.fillMaxSize()) {
						grid(Modifier.fillMaxWidth().weight(1f))
						if (selected != null) {
							HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
							detail(Modifier.fillMaxWidth().heightIn(max = 360.dp))
						}
					}
				}
			}
		}

		SnackbarHost(
			hostState = snackbar,
			modifier = Modifier.align(Alignment.BottomCenter),
		)
	}

	RenameDialog(
		state = state,
		onRename = { id, name ->
			scope.launch {
				when (val result = component.rename(id, name)) {
					ImageAddResult.Ok -> Unit
					else -> report(result)
				}
			}
		},
		onClose = component::endRename,
	)

	DeleteDialog(
		state = state,
		onConfirm = { request ->
			val path = state.images.find { it.def.id == request.id }?.path
			scope.launch {
				component.confirmDelete()
				if (path != null) imageLoader.evictImageFile(path)
			}
		},
		onCancel = component::cancelDelete,
	)

	viewer?.let { (items, index) ->
		ImageViewerDialog(
			items = items,
			initialIndex = index,
			onDismissed = { viewer = null },
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGrid(
	state: ImageGallery.State,
	dragActive: Boolean,
	onSelect: (ImageGallery.GalleryImage) -> Unit,
	onDoubleClick: (ImageGallery.GalleryImage) -> Unit,
	onAdd: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier.border(
			width = if (dragActive) 1.dp else 0.dp,
			color = if (dragActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
			shape = RectangleShape,
		),
	) {
		val shown = state.visibleImages
		when {
			!state.loaded -> Unit
			state.images.isEmpty() -> EmptyGallery(onAdd = onAdd)
			shown.isEmpty() -> EmptyMessage(Res.string.images_gallery_empty_unused.get())
			else -> LazyVerticalGrid(
				columns = GridCells.Adaptive(ThumbnailMinSize),
				contentPadding = PaddingValues(Ui.Padding.XL),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				modifier = Modifier.fillMaxSize(),
			) {
				items(shown, key = { it.def.id }) { image ->
					Thumbnail(
						image = image,
						selected = image.def.id == state.selectedId,
						modifier = Modifier.combinedClickable(
							onClick = { onSelect(image) },
							onDoubleClick = { onDoubleClick(image) },
						),
					)
				}
			}
		}
		if (dragActive) {
			HdMonoLabel(
				text = Res.string.images_gallery_drop_hint.get(),
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(Ui.Padding.L),
			)
		}
	}
}

@Composable
private fun Thumbnail(
	image: ImageGallery.GalleryImage,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	Column(
		modifier = Modifier
			.testTag(imageGalleryItemTag(image.def.id))
			.border(
				width = if (selected) 1.dp else Dp.Hairline,
				color = if (selected) MaterialTheme.colorScheme.primary else ruleColor,
				shape = RectangleShape,
			)
			.then(modifier),
	) {
		AsyncImage(
			model = rememberImageRequest(path = image.path, version = image.cacheToken),
			contentDescription = image.def.name,
			contentScale = ContentScale.Fit,
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(1f)
				.background(MaterialTheme.colorScheme.surfaceContainerLow),
		)
		HorizontalDivider(thickness = Dp.Hairline, color = ruleColor)
		Column(
			modifier = Modifier.padding(horizontal = Ui.Padding.M, vertical = Ui.Padding.S),
		) {
			Text(
				text = image.def.name,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				HdMonoLabel(text = image.def.fileExtension, maxLines = 1)
				if (image.inUse == false) {
					HdMonoLabel(
						text = Res.string.images_gallery_unused_badge.get(),
						color = MaterialTheme.colorScheme.tertiary,
						maxLines = 1,
					)
				}
			}
		}
	}
}

@Composable
private fun EmptyGallery(onAdd: () -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(Ui.Padding.XL)
			.testTag(IMAGE_GALLERY_EMPTY_TAG),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L, Alignment.CenterVertically),
	) {
		HdEngravingPlaceholder(
			label = Res.string.images_gallery_empty_title.get().uppercase(),
			modifier = Modifier
				.widthIn(max = 360.dp)
				.fillMaxWidth()
				.aspectRatio(4f / 3f)
				.clickable(onClick = onAdd),
		)
		Text(
			text = Res.string.images_gallery_empty_message.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun EmptyMessage(text: String) {
	Box(modifier = Modifier.fillMaxSize().padding(Ui.Padding.XL), contentAlignment = Alignment.Center) {
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageDetail(
	image: ImageGallery.GalleryImage,
	pickMode: Boolean,
	onView: () -> Unit,
	onRename: () -> Unit,
	onReplace: () -> Unit,
	onDelete: () -> Unit,
	onUse: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.testTag(IMAGE_GALLERY_DETAIL_TAG)
			.verticalScroll(rememberScrollState())
			.padding(Ui.Padding.XL),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		AsyncImage(
			model = rememberImageRequest(path = image.path, version = image.cacheToken, placeholderFromPath = true),
			contentDescription = image.def.name,
			contentScale = ContentScale.Fit,
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(max = 200.dp)
				.aspectRatio(4f / 3f)
				.background(MaterialTheme.colorScheme.surfaceContainerLow)
				.clickable(onClick = onView),
		)
		Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
			Text(
				text = image.def.name,
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Row(
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				verticalAlignment = Alignment.CenterVertically,
			) {
				HdEntityId(prefix = "IMG", id = image.def.id)
				HdMonoLabel(text = image.def.fileExtension)
				if (image.inUse == false) {
					HdMonoLabel(
						text = Res.string.images_gallery_unused_badge.get(),
						color = MaterialTheme.colorScheme.tertiary,
					)
				}
			}
		}
		if (pickMode) {
			HdPrimaryAction(
				prefix = Res.string.images_gallery_use.get(),
				label = image.def.name,
				onClick = onUse,
				modifier = Modifier.testTag(IMAGE_GALLERY_USE_TAG),
			)
		}
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			HdHairlineButton(
				label = Res.string.images_gallery_view.get(),
				onClick = onView,
				modifier = Modifier.testTag(IMAGE_GALLERY_VIEW_TAG),
			)
			HdHairlineButton(
				label = Res.string.images_gallery_rename.get(),
				onClick = onRename,
				modifier = Modifier.testTag(IMAGE_GALLERY_RENAME_TAG),
			)
			HdHairlineButton(
				label = Res.string.images_gallery_replace.get(),
				onClick = onReplace,
				modifier = Modifier.testTag(IMAGE_GALLERY_REPLACE_TAG),
			)
			HdHairlineButton(
				label = Res.string.images_gallery_delete.get(),
				onClick = onDelete,
				danger = true,
				modifier = Modifier.testTag(IMAGE_GALLERY_DELETE_TAG),
			)
		}
	}
}

@Composable
private fun RenameDialog(
	state: ImageGallery.State,
	onRename: (id: Int, name: String) -> Unit,
	onClose: () -> Unit,
) {
	val renaming = state.renaming?.let { id -> state.images.find { it.def.id == id } }
	val image = retainedWhileClosing(renaming) ?: return

	SimpleDialog(
		title = Res.string.images_gallery_rename_title.get(),
		visible = renaming != null,
		onCloseRequest = onClose,
	) {
		var name by rememberSaveable(image.def.id) { mutableStateOf(image.def.name) }
		Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
			HdHairlineField(
				label = Res.string.images_gallery_rename_label.get(),
				value = name,
				onValueChange = { name = it },
				error = if (name.isNotBlank() && !ImageFormat.isValidName(name.trim())) {
					Res.string.images_toast_name_invalid.get()
				} else {
					null
				},
			)
			HdHairlineButton(
				label = Res.string.images_gallery_rename_button.get(),
				emphasised = true,
				enabled = !state.busy && ImageFormat.isValidName(name.trim()),
				onClick = { onRename(image.def.id, name) },
			)
		}
	}
}

@Composable
private fun DeleteDialog(
	state: ImageGallery.State,
	onConfirm: (ImageGallery.DeleteRequest) -> Unit,
	onCancel: () -> Unit,
) {
	val request = state.confirmDelete
	val showing = retainedWhileClosing(request) ?: return
	val name = state.images.find { it.def.id == showing.id }?.def?.name.orEmpty()

	ConfirmationDialog(
		visible = request != null,
		title = Res.string.images_gallery_delete_title.get(name),
		message = usageMessage(showing.usages),
		confirmLabel = Res.string.images_gallery_delete.get(),
		cancelLabel = Res.string.images_gallery_cancel.get(),
		onConfirm = { onConfirm(showing) },
		onDismiss = onCancel,
		destructive = true,
		kind = "DELETE",
	)
}

/** [value], or the last non-null one while a dialog showing it animates out. */
@Composable
private fun <T : Any> retainedWhileClosing(value: T?): T? {
	val last = remember { LastValue<T>() }
	if (value != null) last.value = value
	return value ?: last.value
}

private class LastValue<T : Any> {
	var value: T? = null
}

/** What deleting the image would take it out of, one line per kind of entity. */
@Composable
private fun usageMessage(usages: ImageUsages): String {
	if (usages.isUnused) return Res.string.images_gallery_delete_unused.get()
	val header = Res.string.images_gallery_delete_used.get(usages.usages.size)
	val lines = usages.usages
		.groupBy { it.entity.type }
		.entries
		.sortedBy { it.key.ordinal }
		.map { (type, uses) ->
			Res.string.images_gallery_delete_usage_line.get(
				usageTypeLabel(type).get(),
				uses.size,
				uses.joinToString { it.name },
			)
		}
	return (listOf(header) + lines).joinToString("\n")
}

private fun usageTypeLabel(type: EntityType): StringResource = when (type) {
	EntityType.Scene -> Res.string.images_usage_type_scene
	EntityType.Note -> Res.string.images_usage_type_note
	EntityType.TimelineEvent -> Res.string.images_usage_type_event
	EntityType.EncyclopediaEntry -> Res.string.images_usage_type_entry
	EntityType.SceneDraft -> Res.string.images_usage_type_draft
	EntityType.Image -> Res.string.images_usage_type_image
}
