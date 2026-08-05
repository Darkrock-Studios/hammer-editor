package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.tagindex.replaceTagPrefix
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val InsetFigureWidth: Dp = 240.dp
private val InsetFigureHeight: Dp = 320.dp
private val StampRowCompactThreshold = 480.dp

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ViewEntryUi(
	component: ViewEntry,
	scope: CoroutineScope,
	modifier: Modifier = Modifier,
	rootSnackbar: RootSnackbarHostState,
	closeEntry: () -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val imageLoader = rememberKoinInject<ImageLoader>()
	val strRes = rememberStrRes()
	val dispatcherMain = rememberMainDispatcher()
	val dispatcherDefault = rememberDefaultDispatcher()
	val dispatcherIo = rememberIoDispatcher()
	val state by component.state.subscribeAsState()

	var entryNameText by rememberSaveable { mutableStateOf(state.content?.name ?: "") }
	var entryText by rememberSaveable { mutableStateOf(state.content?.text ?: "") }
	var discardConfirm by rememberSaveable { mutableStateOf(false) }

	val screen = LocalScreenCharacteristic.current
	val isCompact = screen.windowWidthClass == WindowWidthSizeClass.Compact

	LaunchedEffect(state.content) {
		state.content?.let {
			entryNameText = it.name
			entryText = it.text
		}
	}

	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	val ruleSoft = ruleColor.copy(alpha = 0.5f)
	val ruleStrong = MaterialTheme.colorScheme.outline
	val content = state.content
	val editing = state.editName || state.editText

	val saveChanges: () -> Unit = saveAction@{
		if (content == null) return@saveAction
		scope.launch {
			val result = component.updateEntry(
				name = entryNameText,
				text = entryText,
				tags = content.tags,
			)
			if (result.error == EntryError.NONE) {
				withContext(dispatcherMain) {
					component.finishNameEdit()
					component.finishTextEdit()
				}
			}
			reportSaveResult(result, rootSnackbar, scope, strRes)
		}
	}

	val discardChanges: () -> Unit = discard@{
		if (content == null) return@discard
		entryNameText = content.name
		entryText = content.text
		component.finishNameEdit()
		component.finishTextEdit()
	}

	val outerScrollState = rememberScrollState()
	val outerModifier = if (editing) {
		Modifier.fillMaxSize()
	} else {
		Modifier.fillMaxSize().verticalScroll(outerScrollState)
	}
	Box {
		Column(
			modifier = outerModifier,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			with(sharedTransitionScope) {
				Column(
					modifier = modifier
						.padding(
							start = Ui.Padding.M,
							end = Ui.Padding.M,
						)
						.widthIn(max = Ui.MaxWidth.CatalogueCard)
						.fillMaxWidth()
						.then(if (editing) Modifier.fillMaxHeight() else Modifier)
						.background(MaterialTheme.colorScheme.surface)
						.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape)
						.sharedElement(
							sharedContentState = rememberSharedContentState(
								key = "encyclopedia-card-${state.entryDef.id}",
							),
							animatedVisibilityScope = animatedVisibilityScope,
						),
				) {
					CollapseWhileTyping(enabled = editing) {
						Column {
							CrumbRow(
								title = entryNameText.ifBlank { state.entryDef.name }.uppercase(),
								menuSlot = { DetailViewDropdownMenu(menuItems = state.menuItems) },
								onClose = {
									val isDirty = content != null &&
										(entryNameText != content.name || entryText != content.text)
									when {
										editing && isDirty -> component.confirmClose()
										editing -> {
											component.finishNameEdit()
											component.finishTextEdit()
											closeEntry()
										}

										else -> closeEntry()
									}
								},
							)

							HorizontalDivider(thickness = Dp.Hairline, color = ruleColor)
						}
					}

					StampRow(
						entryDef = state.entryDef,
						editing = editing,
						onEdit = {
							component.startNameEdit()
							component.startTextEdit()
						},
						onSave = saveChanges,
						onCancel = {
							val isDirty = content != null &&
								(entryNameText != content.name || entryText != content.text)
							if (isDirty) {
								discardConfirm = true
							} else {
								discardChanges()
							}
						},
					)

					HorizontalDivider(thickness = 2.dp, color = ruleStrong)

					// The big name collapses while the body editor has focus; kept visible while the name
					// field itself holds focus so it stays reachable.
					var nameFocused by remember { mutableStateOf(false) }
					CollapseWhileTyping(keepVisible = nameFocused) {
						NameZone(
							entryNameText = entryNameText,
							onNameChange = { entryNameText = it },
							editName = state.editName,
							onStartEdit = component::startNameEdit,
							onFocusChanged = { nameFocused = it },
							compact = isCompact,
							sharedKey = "encyclopedia-title-${state.entryDef.id}",
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						)
					}

					val bodyOuterModifier = if (editing) Modifier.weight(1f) else Modifier
					if (isCompact) {
						CompactBody(
							state = state,
							entryText = entryText,
							setEntryText = { entryText = it },
							onStartTextEdit = component::startTextEdit,
							onShowDeleteImage = component::showDeleteImageDialog,
							onAddImage = component::showAddImageDialog,
							outerModifier = bodyOuterModifier,
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						)
					} else {
						WideBody(
							state = state,
							entryText = entryText,
							setEntryText = { entryText = it },
							onStartTextEdit = component::startTextEdit,
							onShowDeleteImage = component::showDeleteImageDialog,
							onAddImage = component::showAddImageDialog,
							outerModifier = bodyOuterModifier,
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						)
					}

					if (!editing) {
						if (content != null) {
							ParticularsLedger(
								state = state,
								modifier = Modifier
									.padding(horizontal = Ui.Padding.XXL)
									.padding(top = 28.dp),
							)
						}

						TagsAndAliasesZone(
							state = state,
							component = component,
							compact = isCompact,
						)

						AppearsInZone(
							state = state,
							component = component,
						)

						FooterColophon(
							compact = isCompact,
							ruleSoft = ruleSoft,
						)
					}
				}
			}
		}

		// Editing swaps in a non-scrolling layout, leaving outerScrollState detached but still
		// reporting its last measured extent, so only the screen knows the bar is stale.
		if (!editing) {
			MpScrollBarColumn(
				modifier = scrollBarOverlay(),
				state = outerScrollState,
			)
		}
	}

	if (discardConfirm) {
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_discard_title.get(),
			message = Res.string.encyclopedia_entry_discard_message.get(),
			onDismiss = { discardConfirm = false },
			onConfirm = {
				discardChanges()
				discardConfirm = false
			},
		)
	}

	LaunchedEffect(state.showAddImageDialog) {
		if (state.showAddImageDialog) {
			val file = retryingFileDialog {
				FileKit.openFilePicker(type = FileKitType.File(EncyclopediaDatasource.IMAGE_EXTENSIONS))
			}
			if (file != null) {
				if (file.size() > EncyclopediaDatasource.MAX_IMAGE_SIZE_BYTES) {
					rootSnackbar.showSnackbar(
						strRes.get(
							Res.string.encyclopedia_create_entry_image_too_large,
							EncyclopediaDatasource.MAX_IMAGE_SIZE_MB,
						)
					)
				} else {
					val localCopy = withContext(dispatcherIo) { file.stageIntoCache() }
					if (localCopy != null) {
						scope.launch { component.setImage(localCopy.path) }
					} else {
						rootSnackbar.showSnackbar(
							strRes.get(Res.string.encyclopedia_create_entry_image_load_failed)
						)
					}
				}
			}
			component.closeAddImageDialog()
		}
	}

	if (state.showDeleteImageDialog) {
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_delete_image_title.get(),
			message = Res.string.encyclopedia_entry_delete_image_message.get(),
			onDismiss = { component.closeDeleteImageDialog() },
			onConfirm = {
				scope.launch {
					component.removeEntryImage()
					state.entryImagePath?.let { path ->
						imageLoader.diskCache?.remove(path)
						imageLoader.memoryCache?.remove(MemoryCache.Key(path))
					}
				}
				component.closeDeleteImageDialog()
			},
		)
	}

	if (state.showDeleteEntryDialog) {
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_delete_title.get(),
			message = Res.string.encyclopedia_entry_delete_message.get(),
			onDismiss = { component.closeDeleteEntryDialog() },
			onConfirm = {
				scope.launch(dispatcherDefault) {
					if (component.deleteEntry(state.entryDef)) {
						withContext(dispatcherMain) { closeEntry() }
						rootSnackbar.showSnackbar(
							strRes.get(Res.string.encyclopedia_entry_delete_toast),
						)
					}
				}
				component.closeDeleteEntryDialog()
			},
		)
	}

	if (state.confirmClose) {
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_discard_title.get(),
			message = Res.string.encyclopedia_entry_discard_message.get(),
			onDismiss = { component.dismissConfirmClose() },
			onConfirm = {
				component.dismissConfirmClose()
				component.finishNameEdit()
				component.finishTextEdit()
				closeEntry()
			},
		)
	}

	TagAddDialog(state = state, component = component, scope = scope)
	AliasAddDialog(
		state = state,
		component = component,
		scope = scope,
		rootSnackbar = rootSnackbar,
		strRes = strRes,
	)
}

@Composable
private fun CrumbRow(
	title: String,
	menuSlot: @Composable () -> Unit,
	onClose: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(Ui.TOP_BAR_HEIGHT)
			.padding(horizontal = Ui.Padding.XL),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdCrumbBackLink(
				label = Res.string.encyclopedia_entry_crumb_root.get(),
				onClick = onClose,
				onClickLabel = Res.string.encyclopedia_entry_close_button.get(),
			)
			HdMonoLabel(
				text = "/",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Text(
			text = title,
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)
		menuSlot()
	}
}

@Composable
private fun StampRow(
	entryDef: EntryDef,
	editing: Boolean,
	onEdit: () -> Unit,
	onSave: () -> Unit,
	onCancel: () -> Unit,
) {
	val folioMeta: (@Composable RowScope.() -> Unit)? = if (editing) {
		null
	} else {
		{
			HdMonoLabel(
				text = Res.string.encyclopedia_entry_folio_format.get(
					folioInitials(entryDef),
					folioId(entryDef),
				),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}

	HdDetailStampRow(
		stackActionsWhenNarrow = editing,
		compactThreshold = StampRowCompactThreshold,
		contentPadding = PaddingValues(horizontal = Ui.Padding.XXL, vertical = Ui.Padding.L),
		leading = { FolioStamp(entryDef = entryDef) },
		meta = folioMeta,
		actions = {
			if (editing) {
				HdHairlineButton(
					label = Res.string.encyclopedia_entry_edit_save_button.get(),
					onClick = onSave,
					emphasised = true,
				)
				HdHairlineButton(
					label = Res.string.encyclopedia_entry_edit_cancel_button.get(),
					onClick = onCancel,
				)
			} else {
				HdHairlineButton(
					label = Res.string.encyclopedia_entry_edit_button.get(),
					onClick = onEdit,
				)
			}
		},
	)
}

@Composable
private fun FolioStamp(
	entryDef: EntryDef,
) {
	val typeColor = LocalHammerColors.current.colorFor(entryDef.type)
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	Row(
		modifier = Modifier
			.height(36.dp)
			.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.width(36.dp)
				.height(36.dp)
				.background(typeColor),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = entryDef.type.glyph(),
				style = MaterialTheme.typography.titleMedium,
				color = Color.Black,
				fontWeight = FontWeight.Medium,
			)
		}
		Box(
			modifier = Modifier
				.width(Dp.Hairline)
				.height(36.dp)
				.background(ruleColor),
		)
		HdMonoLabel(
			text = entryDef.type.toStringResource().get(),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(horizontal = 14.dp),
		)
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NameZone(
	entryNameText: String,
	onNameChange: (String) -> Unit,
	editName: Boolean,
	onStartEdit: () -> Unit,
	onFocusChanged: (Boolean) -> Unit,
	compact: Boolean,
	sharedKey: String,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val onSurface = MaterialTheme.colorScheme.onSurface
	val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
	// Smaller ceiling on phones; the view text autosizes down from here so long names fit.
	val maxNameSize = if (compact) 40.sp else 88.sp
	val minNameSize = if (compact) 22.sp else 40.sp
	val titleStyle = TextStyle(
		fontSize = maxNameSize,
		lineHeight = if (compact) 44.sp else 88.sp,
		letterSpacing = (-2).sp,
		fontWeight = FontWeight.ExtraLight,
		color = onSurface,
	)

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XXL, vertical = Ui.Padding.L),
	) {
		if (editName) {
			BasicTextField(
				value = entryNameText,
				onValueChange = onNameChange,
				modifier = Modifier
					.fillMaxWidth()
					.onFocusChanged { onFocusChanged(it.isFocused) },
				textStyle = titleStyle,
				cursorBrush = SolidColor(onSurface),
				singleLine = false,
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
			)
			if (entryNameText.isEmpty()) {
				Text(
					text = Res.string.encyclopedia_entry_name_hint.get(),
					style = titleStyle.copy(color = mutedColor),
				)
			}
		} else {
			with(sharedTransitionScope) {
				BasicText(
					text = entryNameText,
					style = titleStyle,
					maxLines = 3,
					autoSize = TextAutoSize.StepBased(
						minFontSize = minNameSize,
						maxFontSize = maxNameSize,
						stepSize = 2.sp,
					),
					modifier = Modifier
						.fillMaxWidth()
						.sharedElement(
							sharedContentState = rememberSharedContentState(key = sharedKey),
							animatedVisibilityScope = animatedVisibilityScope,
						)
						.clickable(onClick = onStartEdit),
				)
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WideBody(
	state: ViewEntry.State,
	entryText: String,
	setEntryText: (String) -> Unit,
	onStartTextEdit: () -> Unit,
	onShowDeleteImage: () -> Unit,
	onAddImage: () -> Unit,
	outerModifier: Modifier,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	Row(
		modifier = outerModifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XXL, vertical = Ui.Padding.L),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.XXL),
	) {
		BodyTextZone(
			editText = state.editText,
			entryText = entryText,
			setEntryText = setEntryText,
			onStartTextEdit = onStartTextEdit,
			enableSpellChecking = state.spellCheckAllowed,
			modifier = Modifier.weight(1f).fillMaxHeight(),
			sharedKey = "encyclopedia-text-${state.entryDef.id}",
			animatedVisibilityScope = animatedVisibilityScope,
			sharedTransitionScope = sharedTransitionScope,
		)
		Column(
			modifier = Modifier.width(InsetFigureWidth),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			InsetFigure(
				state = state,
				onShowDeleteImage = onShowDeleteImage,
				onAddImage = onAddImage,
				sharedTransitionScope = sharedTransitionScope,
				animatedVisibilityScope = animatedVisibilityScope,
				modifier = Modifier
					.fillMaxWidth()
					.height(InsetFigureHeight),
			)
			HdMonoLabel(
				text = Res.string.encyclopedia_entry_figure_caption.get() + " · ↗",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CompactBody(
	state: ViewEntry.State,
	entryText: String,
	setEntryText: (String) -> Unit,
	onStartTextEdit: () -> Unit,
	onShowDeleteImage: () -> Unit,
	onAddImage: () -> Unit,
	outerModifier: Modifier,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val editing = state.editText
	Column(
		modifier = outerModifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XXL, vertical = Ui.Padding.L),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		if (!editing) {
			InsetFigure(
				state = state,
				onShowDeleteImage = onShowDeleteImage,
				onAddImage = onAddImage,
				sharedTransitionScope = sharedTransitionScope,
				animatedVisibilityScope = animatedVisibilityScope,
				modifier = Modifier
					.fillMaxWidth()
					.height(240.dp),
			)
			HdMonoLabel(
				text = Res.string.encyclopedia_entry_figure_caption.get() + " · ↗",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		BodyTextZone(
			editText = state.editText,
			entryText = entryText,
			setEntryText = setEntryText,
			onStartTextEdit = onStartTextEdit,
			enableSpellChecking = state.spellCheckAllowed,
			modifier = if (editing) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
			sharedKey = "encyclopedia-text-${state.entryDef.id}",
			animatedVisibilityScope = animatedVisibilityScope,
			sharedTransitionScope = sharedTransitionScope,
		)
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BodyTextZone(
	editText: Boolean,
	entryText: String,
	setEntryText: (String) -> Unit,
	onStartTextEdit: () -> Unit,
	enableSpellChecking: Boolean,
	modifier: Modifier,
	sharedKey: String,
	animatedVisibilityScope: AnimatedVisibilityScope,
	sharedTransitionScope: SharedTransitionScope,
) {
	val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
		color = MaterialTheme.colorScheme.onSurface,
		lineHeight = 26.sp,
	)
	val mutedStyle = bodyStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

	if (editText) {
		Column(modifier = modifier) {
			MarkdownEditField(
				initialMarkdown = entryText,
				onMarkdownChanged = setEntryText,
				enableSpellChecking = enableSpellChecking,
				contentPadding = PaddingValues(Ui.Padding.XL),
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f)
					.padding(vertical = Ui.Padding.M),
			)
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				modifier = Modifier.padding(top = Ui.Padding.M),
			)
		}
	} else {
		with(sharedTransitionScope) {
			if (entryText.isBlank()) {
				Text(
					text = Res.string.encyclopedia_entry_body_empty_label.get(),
					style = mutedStyle,
					modifier = modifier
						.sharedElement(
							sharedContentState = rememberSharedContentState(key = sharedKey),
							animatedVisibilityScope = animatedVisibilityScope,
						)
						.clickable(onClick = onStartTextEdit),
				)
			} else {
				MarkdownView(
					markdown = entryText,
					modifier = modifier
						.sharedElement(
							sharedContentState = rememberSharedContentState(key = sharedKey),
							animatedVisibilityScope = animatedVisibilityScope,
						)
						.clickable(onClick = onStartTextEdit),
				)
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun InsetFigure(
	state: ViewEntry.State,
	onShowDeleteImage: () -> Unit,
	onAddImage: () -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	modifier: Modifier = Modifier,
) {
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	val imagePath = state.entryImagePath
	Box(
		modifier = modifier
			.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape)
			.background(MaterialTheme.colorScheme.surfaceContainerLow),
	) {
		if (imagePath != null) {
			val context = LocalPlatformContext.current
			with(sharedTransitionScope) {
				AsyncImage(
					model = remember(imagePath, state.entryImageHash) {
						ImageRequest.Builder(context)
							.data(imagePath)
							.memoryCacheKeyExtras(
								mapOf("hash" to state.entryImageHash.toString()),
							)
							.placeholderMemoryCacheKey(imagePath)
							.crossfade(false)
							.build()
					},
					contentDescription = null,
					modifier = Modifier
						.fillMaxSize()
						.sharedElement(
							sharedContentState = rememberSharedContentState(
								key = "encyclopedia-image-${state.entryDef.id}",
							),
							animatedVisibilityScope = animatedVisibilityScope,
						)
						.clickable(onClick = onShowDeleteImage),
					contentScale = ContentScale.Fit,
				)
			}
		} else {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.clickable(onClick = onAddImage),
			) {
				HdEngravingPlaceholder(
					label = state.entryDef.name.uppercase(),
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f),
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
					HdMonoLabel(
						text = Res.string.encyclopedia_entry_figure_add.get(),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
	}
}

@Composable
private fun ParticularsLedger(
	state: ViewEntry.State,
	modifier: Modifier = Modifier,
) {
	val content = state.content ?: return
	val cells = listOf<@Composable () -> Unit>(
		{
			HdMetadataItem(
				label = Res.string.encyclopedia_entry_particulars_type.get(),
				value = state.entryDef.type.toStringResource().get(),
			)
		},
		{
			HdMetadataItem(
				label = Res.string.encyclopedia_entry_particulars_tags.get(),
				value = content.tags.size.toString(),
			)
		},
		{
			HdMetadataItem(
				label = Res.string.encyclopedia_entry_particulars_aliases.get(),
				value = content.aliases.size.toString(),
			)
		},
		{
			HdMetadataItem(
				label = Res.string.encyclopedia_entry_particulars_scenes.get(),
				value = state.appearsIn.size.toString(),
			)
		},
	)

	Column(modifier = modifier.fillMaxWidth()) {
		HdSectionHeader(
			marker = "—",
			title = Res.string.encyclopedia_entry_particulars_label.get(),
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.background(MaterialTheme.colorScheme.surfaceContainerLow),
		) {
			HdHairlineGrid(columns = 2, cells = cells)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsAndAliasesZone(
	state: ViewEntry.State,
	component: ViewEntry,
	compact: Boolean,
) {
	val content = state.content ?: return
	val padding = Modifier.padding(horizontal = Ui.Padding.XXL).padding(top = 28.dp)
	if (compact) {
		Column(
			modifier = padding.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(28.dp),
		) {
			TagsSection(content.tags, component)
			AliasesSection(content.aliases, component)
		}
	} else {
		Row(
			modifier = padding.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(28.dp),
		) {
			TagsSection(content.tags, component, modifier = Modifier.weight(1f))
			AliasesSection(content.aliases, component, modifier = Modifier.weight(1f))
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
	tags: Set<String>,
	component: ViewEntry,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdSectionHeader(
			marker = "—",
			title = Res.string.encyclopedia_entry_tags_label.get(),
		)
		FlowRow(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			tags.forEach { tag ->
				HdTagChip(
					label = tag,
					onClick = { component.showGlobalSearchForTag(tag) },
					onRemove = { component.removeTag(tag) },
				)
			}
			AddChip(
				label = Res.string.encyclopedia_entry_add_tag_chip.get(),
				onClick = component::startTagAdd,
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AliasesSection(
	aliases: List<String>,
	component: ViewEntry,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdSectionHeader(
			marker = "—",
			title = Res.string.encyclopedia_entry_aliases_label.get(),
		)
		FlowRow(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			aliases.forEach { alias ->
				AliasChip(label = alias, onRemove = { component.removeAlias(alias) })
			}
			AddChip(
				label = Res.string.encyclopedia_entry_add_alias_chip.get(),
				onClick = component::startAliasAdd,
			)
		}
	}
}

@Composable
private fun AppearsInZone(
	state: ViewEntry.State,
	component: ViewEntry,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XXL)
			.padding(top = 28.dp),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdSectionHeader(
			marker = "—",
			title = Res.string.encyclopedia_entry_appears_in_label.get(),
			trailing = {
				HdMonoLabel(
					text = "${state.appearsIn.size}",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			},
		)
		if (state.appearsIn.isEmpty()) {
			Text(
				text = Res.string.encyclopedia_entry_appears_in_empty.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		} else {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					)
					.padding(horizontal = Ui.Padding.L),
			) {
				state.appearsIn.forEachIndexed { index, appearance ->
					SceneRow(
						index = index + 1,
						appearance = appearance,
						isLast = index == state.appearsIn.lastIndex,
						onClick = { component.navigateToAppearance(appearance) },
					)
				}
			}
		}
	}
}

@Composable
private fun SceneRow(
	index: Int,
	appearance: ViewEntry.Appearance,
	isLast: Boolean,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = Res.string.encyclopedia_entry_scene_index_format.get(
				index.toString().padStart(2, '0'),
			),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.width(48.dp),
		)
		Icon(
			imageVector = appearanceIcon(appearance.source),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(16.dp),
		)
		Text(
			text = appearance.name,
			style = MaterialTheme.typography.titleSmall,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
		)
		HdMonoLabel(
			text = "↗",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
	if (!isLast) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
		)
	}
}

@Composable
private fun FooterColophon(compact: Boolean, ruleSoft: Color) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XXL)
			.padding(top = 36.dp, bottom = Ui.Padding.XXL),
	) {
		HorizontalDivider(thickness = Dp.Hairline, color = ruleSoft)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = Ui.Padding.L),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(
				text = if (compact) {
					Res.string.encyclopedia_entry_colophon_label_compact.get()
				} else {
					Res.string.encyclopedia_entry_colophon_label.get()
				},
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			HdMonoLabel(
				text = Res.string.encyclopedia_entry_colophon_end.get(),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@Composable
private fun TagAddDialog(
	state: ViewEntry.State,
	component: ViewEntry,
	scope: CoroutineScope,
) {
	val mainDispatcher = rememberMainDispatcher()
	SimpleDialog(
		title = Res.string.encyclopedia_entry_add_tags_dialog_title.get(),
		visible = state.showTagAdd,
		onCloseRequest = component::endTagAdd,
	) {
		var newTagsText by rememberSaveable { mutableStateOf("") }
		val existingTags = state.content?.tags.orEmpty()
		val suggestions = rememberTagSuggestions(newTagsText, existingTags, component::suggestTags)
		Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
			HdHairlineField(
				label = Res.string.encyclopedia_create_entry_tags_label.get(),
				value = newTagsText,
				onValueChange = { newTagsText = it },
			)
			HdTagSuggestionStrip(
				suggestions = suggestions,
				onSelect = { tag ->
					scope.launch {
						component.addTags(replaceTagPrefix(newTagsText, tag))
						withContext(mainDispatcher) { newTagsText = "" }
					}
				},
			)
			HdHairlineButton(
				label = Res.string.encyclopedia_entry_add_tags_button.get(),
				emphasised = true,
				onClick = {
					scope.launch {
						component.addTags(newTagsText)
						withContext(mainDispatcher) { newTagsText = "" }
					}
				},
			)
		}
	}
}

@Composable
private fun AliasAddDialog(
	state: ViewEntry.State,
	component: ViewEntry,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
	strRes: StrRes,
) {
	val mainDispatcher = rememberMainDispatcher()
	SimpleDialog(
		title = Res.string.encyclopedia_entry_add_alias_dialog_title.get(),
		visible = state.showAliasAdd,
		onCloseRequest = component::endAliasAdd,
	) {
		var newAliasText by rememberSaveable { mutableStateOf("") }
		Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
			HdHairlineField(
				label = Res.string.encyclopedia_entry_alias_hint.get(),
				value = newAliasText,
				onValueChange = { newAliasText = it },
			)
			HdHairlineButton(
				label = Res.string.encyclopedia_entry_add_alias_button.get(),
				emphasised = true,
				onClick = {
					scope.launch {
						val result = component.addAlias(newAliasText)
						withContext(mainDispatcher) {
							if (result.error == EntryError.NONE) newAliasText = ""
						}
						reportSaveResult(result, rootSnackbar, scope, strRes)
					}
				},
			)
		}
	}
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
	Row(
		modifier = Modifier
			.height(24.dp)
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			)
			.clickable(onClick = onClick)
			.padding(horizontal = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Icon(
			imageVector = Icons.Filled.Add,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(12.dp),
		)
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun AliasChip(label: String, onRemove: () -> Unit) {
	Row(
		modifier = Modifier
			.height(26.dp)
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			)
			.padding(start = 10.dp, end = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			fontWeight = FontWeight.Light,
		)
		Box(
			modifier = Modifier
				.size(18.dp)
				.clickable(onClick = onRemove),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				imageVector = Icons.Filled.Delete,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(12.dp),
			)
		}
	}
}

private fun folioInitials(entryDef: EntryDef): String =
	entryDef.name
		.uppercase()
		.split(' ')
		.mapNotNull { it.firstOrNull()?.toString() }
		.joinToString("")
		.take(3)
		.ifBlank { "—" }

private fun folioId(entryDef: EntryDef): String =
	entryDef.id.toString().padStart(3, '0')

private fun appearanceIcon(source: ViewEntry.AppearanceSource): ImageVector =
	when (source) {
		ViewEntry.AppearanceSource.Scene -> Icons.AutoMirrored.Filled.Article
	}

private fun reportSaveResult(
	result: EntryResult,
	rootSnackbar: RootSnackbarHostState,
	scope: CoroutineScope,
	strRes: StrRes,
) {
	scope.launch {
		when (result.error) {
			EntryError.NAME_TOO_LONG -> rootSnackbar.showSnackbar(
				strRes.get(
					Res.string.encyclopedia_create_entry_toast_too_long,
					EncyclopediaRepository.MAX_NAME_SIZE,
				),
			)

			EntryError.NAME_INVALID_CHARACTERS -> rootSnackbar.showSnackbar(
				strRes.get(Res.string.encyclopedia_create_entry_toast_invalid_name),
			)

			EntryError.TAG_TOO_LONG -> rootSnackbar.showSnackbar(
				strRes.get(
					Res.string.encyclopedia_create_entry_toast_tag_too_long,
					EncyclopediaRepository.MAX_TAG_SIZE,
				),
			)

			EntryError.NAME_TOO_SHORT -> rootSnackbar.showSnackbar(
				strRes.get(Res.string.encyclopedia_create_entry_toast_name_too_short),
			)

			EntryError.ALIAS_TOO_LONG -> rootSnackbar.showSnackbar(
				strRes.get(
					Res.string.encyclopedia_create_entry_toast_alias_too_long,
					EncyclopediaRepository.MAX_NAME_SIZE,
				),
			)

			EntryError.NONE -> {
				rootSnackbar.showSnackbar(
					strRes.get(Res.string.encyclopedia_create_entry_toast_success),
				)
				rootSnackbar.showSnackbar(
					strRes.get(Res.string.encyclopedia_entry_edit_save_toast),
				)
			}
		}
	}
}
