package com.darkrockstudios.apps.hammer.common.encyclopedia

import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdPlaceholderColor
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdRegion
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdHangsHeads
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdControlFrame
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.compose.actions.RegisterActions
import com.darkrockstudios.apps.hammer.common.encyclopedia.rename.RenameEntryDialog
import com.darkrockstudios.apps.hammer.common.compose.CollapseWhileTyping
import com.darkrockstudios.apps.hammer.common.compose.DetailViewDropdownMenu
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCrumbBackLink
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDetailStampRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.glyph
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberTagSuggestions
import com.darkrockstudios.apps.hammer.common.compose.images.StagedImage
import com.darkrockstudios.apps.hammer.common.compose.images.imageAddResultMessage
import com.darkrockstudios.apps.hammer.common.compose.images.pickImageFile
import com.darkrockstudios.apps.hammer.common.compose.images.stageImage
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.rememberDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.images.ImageViewerDialog
import com.darkrockstudios.apps.hammer.common.timeline.TimelinePlacementChips
import com.darkrockstudios.apps.hammer.timeline_subject_timelines_label
import com.darkrockstudios.apps.hammer.common.images.ImageViewerItem
import com.darkrockstudios.apps.hammer.common.data.tagindex.replaceTagPrefix
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_alias_too_long
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_invalid_name
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_name_too_short
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_success
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_tag_too_long
import com.darkrockstudios.apps.hammer.encyclopedia_create_entry_toast_too_long
import com.darkrockstudios.apps.hammer.encyclopedia_entry_add_alias_button
import com.darkrockstudios.apps.hammer.encyclopedia_entry_add_alias_chip
import com.darkrockstudios.apps.hammer.encyclopedia_entry_add_alias_dialog_title
import com.darkrockstudios.apps.hammer.encyclopedia_entry_add_tag_chip
import com.darkrockstudios.apps.hammer.encyclopedia_entry_alias_hint
import com.darkrockstudios.apps.hammer.encyclopedia_entry_aliases_label
import com.darkrockstudios.apps.hammer.encyclopedia_entry_appears_in_empty
import com.darkrockstudios.apps.hammer.encyclopedia_entry_appears_in_label
import com.darkrockstudios.apps.hammer.encyclopedia_entry_dictionary_toggle_hint
import com.darkrockstudios.apps.hammer.encyclopedia_entry_dictionary_toggle_label
import com.darkrockstudios.apps.hammer.encyclopedia_entry_body_empty_label
import com.darkrockstudios.apps.hammer.encyclopedia_entry_close_button
import com.darkrockstudios.apps.hammer.encyclopedia_entry_crumb_root
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.AddTagCard
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.encyclopedia_entry_add_alias_done
import com.darkrockstudios.apps.hammer.confirm_kind_discard
import com.darkrockstudios.apps.hammer.confirm_kind_delete
import com.darkrockstudios.apps.hammer.confirm_kind_remove
import com.darkrockstudios.apps.hammer.encyclopedia_entry_discard_confirm
import com.darkrockstudios.apps.hammer.encyclopedia_entry_discard_keep
import com.darkrockstudios.apps.hammer.encyclopedia_entry_delete_confirm
import com.darkrockstudios.apps.hammer.encyclopedia_entry_remove_image_confirm
import com.darkrockstudios.apps.hammer.encyclopedia_entry_confirm_cancel
import com.darkrockstudios.apps.hammer.encyclopedia_entry_delete_message
import com.darkrockstudios.apps.hammer.encyclopedia_entry_delete_title
import com.darkrockstudios.apps.hammer.encyclopedia_entry_delete_toast
import com.darkrockstudios.apps.hammer.encyclopedia_entry_discard_message
import com.darkrockstudios.apps.hammer.encyclopedia_entry_discard_title
import com.darkrockstudios.apps.hammer.encyclopedia_entry_edit_button
import com.darkrockstudios.apps.hammer.encyclopedia_entry_edit_cancel_button
import com.darkrockstudios.apps.hammer.encyclopedia_entry_edit_save_button
import com.darkrockstudios.apps.hammer.encyclopedia_entry_edit_save_toast
import com.darkrockstudios.apps.hammer.encyclopedia_entry_folio_format
import com.darkrockstudios.apps.hammer.encyclopedia_entry_name_hint
import com.darkrockstudios.apps.hammer.encyclopedia_entry_remove_image_message
import com.darkrockstudios.apps.hammer.encyclopedia_entry_remove_image_title
import com.darkrockstudios.apps.hammer.encyclopedia_entry_scene_index_format
import com.darkrockstudios.apps.hammer.encyclopedia_entry_tags_label
import io.github.vinceglb.filekit.PlatformFile
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
	val strRes = rememberStrRes()
	val dispatcherMain = rememberMainDispatcher()
	val dispatcherDefault = rememberDefaultDispatcher()
	val dispatcherIo = rememberIoDispatcher()
	val state by component.state.subscribeAsState()

	var entryNameText by rememberSaveable { mutableStateOf(state.content?.name ?: "") }
	var entryText by rememberSaveable { mutableStateOf(state.content?.text ?: "") }
	var discardConfirm by rememberSaveable { mutableStateOf(false) }
	var seeded by rememberSaveable { mutableStateOf(false) }

	val screen = LocalScreenCharacteristic.current
	val isCompact = screen.windowWidthClass == WindowWidthSizeClass.Compact

	LaunchedEffect(state.content, state.editName, state.editText) {
		val loaded = state.content ?: return@LaunchedEffect
		// Once the fields hold the entry, an open edit owns them: re-seeding would discard
		// unsaved text. Until then they must be filled even if the edit started first,
		// otherwise a save writes an empty entry. Leaving edit mode reseeds, which is what
		// makes a discard from outside this screen drop the typed text.
		if (seeded && (state.editName || state.editText)) return@LaunchedEffect
		entryNameText = loaded.name
		entryText = loaded.text
		seeded = true
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

	val requestClose: () -> Unit = {
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
	}
	RegisterActions(ActionLayer.Item, mapOf(HammerActions.ItemClose.id to ActionHandler { requestClose() }))

	suspend fun reportImage(result: ImageAddResult) {
		imageAddResultMessage(result, strRes)?.let { rootSnackbar.showSnackbar(it) }
	}

	suspend fun importPicked(file: PlatformFile) {
		val result = when (val staged = file.stageImage(dispatcherIo)) {
			is StagedImage.Staged -> component.importImage(staged.path)
			is StagedImage.Failed -> staged.result
		}
		reportImage(result)
	}

	val primaryImageModifier = with(sharedTransitionScope) {
		Modifier.sharedElement(
			sharedContentState = rememberSharedContentState(
				key = "encyclopedia-image-${state.entryDef.id}",
			),
			animatedVisibilityScope = animatedVisibilityScope,
		)
	}
	var viewerIndex by remember { mutableStateOf<Int?>(null) }
	val figure: @Composable (Modifier) -> Unit = { figureModifier ->
		EntryImagePager(
			entryName = state.entryDef.name,
			images = state.images,
			currentIndex = state.currentImageIndex,
			onShowIndex = component::showImageAt,
			onAdd = component::showAddImageDialog,
			onAddFromLibrary = component::addImageFromLibrary,
			onOpenImage = { viewerIndex = it },
			onFilesDropped = { files -> scope.launch { files.forEach { importPicked(it) } } },
			onSetPrimary = { id -> scope.launch { component.setPrimaryImage(id) } },
			onRemove = component::showRemoveImageConfirm,
			modifier = figureModifier,
			primaryImageModifier = primaryImageModifier,
		)
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
								menuSlot = { DetailViewDropdownMenu(state.menuItems, ActionPlace.Entry, state.entryDef.id) },
								onClose = requestClose,
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
							figure = figure,
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
							figure = figure,
							outerModifier = bodyOuterModifier,
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						)
					}

					if (!editing) {
						TagsAndAliasesZone(
							state = state,
							component = component,
							compact = isCompact,
						)

						AppearsInZone(
							state = state,
							component = component,
						)

						TimelinesZone(
							state = state,
							component = component,
						)

						DictionaryToggleZone(
							state = state,
							component = component,
							scope = scope,
						)

						Spacer(modifier = Modifier.height(Ui.Padding.XXL))
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
			positiveButton = Res.string.encyclopedia_entry_discard_confirm.get(),
			negativeButton = Res.string.encyclopedia_entry_discard_keep.get(),
			destructive = true,
			kind = Res.string.confirm_kind_discard.get(),
			onDismiss = { discardConfirm = false },
			onConfirm = {
				discardChanges()
				discardConfirm = false
			},
		)
	}

	LaunchedEffect(state.showAddImageDialog) {
		if (state.showAddImageDialog) {
			val file = pickImageFile(::reportImage)
			// Imported outside this effect: closing the dialog below restarts it.
			if (file != null) scope.launch { importPicked(file) }
			component.closeAddImageDialog()
		}
	}

	viewerIndex?.let { index ->
		ImageViewerDialog(
			items = state.images.map { ImageViewerItem.ofFile(it.path, it.hash, it.name) },
			initialIndex = index,
			onDismissed = { viewerIndex = null },
		)
	}

	state.removeImageConfirm?.let { imageId ->
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_remove_image_title.get(),
			message = Res.string.encyclopedia_entry_remove_image_message.get(),
			positiveButton = Res.string.encyclopedia_entry_remove_image_confirm.get(),
			negativeButton = Res.string.encyclopedia_entry_confirm_cancel.get(),
			kind = Res.string.confirm_kind_remove.get(),
			onDismiss = { component.dismissRemoveImageConfirm() },
			onConfirm = {
				scope.launch { component.removeImage(imageId) }
				component.dismissRemoveImageConfirm()
			},
		)
	}

	if (state.showDeleteEntryDialog) {
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_delete_title.get(state.entryDef.name),
			message = Res.string.encyclopedia_entry_delete_message.get(),
			positiveButton = Res.string.encyclopedia_entry_delete_confirm.get(),
			negativeButton = Res.string.encyclopedia_entry_confirm_cancel.get(),
			destructive = true,
			kind = Res.string.confirm_kind_delete.get(),
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
			positiveButton = Res.string.encyclopedia_entry_discard_confirm.get(),
			negativeButton = Res.string.encyclopedia_entry_discard_keep.get(),
			destructive = true,
			kind = Res.string.confirm_kind_discard.get(),
			onDismiss = { component.dismissConfirmClose() },
			onConfirm = {
				component.dismissConfirmClose()
				component.finishNameEdit()
				component.finishTextEdit()
				closeEntry()
			},
		)
	}

	val renameSlot by component.renameSlot.subscribeAsState()
	renameSlot.child?.instance?.let { RenameEntryDialog(component = it) }

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
		HdCrumbBackLink(
			label = Res.string.encyclopedia_entry_crumb_root.get(),
			onClick = onClose,
			onClickLabel = Res.string.encyclopedia_entry_close_button.get(),
		)
		Spacer(modifier = Modifier.weight(1f))
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
					style = titleStyle.copy(color = hdPlaceholderColor),
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
	figure: @Composable (Modifier) -> Unit,
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
		figure(Modifier.width(InsetFigureWidth).height(InsetFigureHeight))
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CompactBody(
	state: ViewEntry.State,
	entryText: String,
	setEntryText: (String) -> Unit,
	onStartTextEdit: () -> Unit,
	figure: @Composable (Modifier) -> Unit,
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
			figure(
				Modifier
					.fillMaxWidth()
					.height(240.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsAndAliasesZone(
	state: ViewEntry.State,
	component: ViewEntry,
	compact: Boolean,
) {
	val content = state.content ?: return
	val padding = Modifier.padding(horizontal = Ui.Padding.XXL).padding(top = 28.dp)
	if (compact || hdHangsHeads()) {
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

@Composable
private fun DictionaryToggleZone(
	state: ViewEntry.State,
	component: ViewEntry,
	scope: CoroutineScope,
) {
	if (!state.dictionaryFeatureEnabled) return
	val content = state.content ?: return
	HdHairlineToggleRow(
		checked = !content.excludeFromDictionary,
		label = Res.string.encyclopedia_entry_dictionary_toggle_label.get(),
		hint = Res.string.encyclopedia_entry_dictionary_toggle_hint.get(),
		onCheckedChange = { include ->
			scope.launch { component.setExcludeFromDictionary(!include) }
		},
		modifier = Modifier
			.padding(horizontal = Ui.Padding.XXL)
			.padding(top = 28.dp)
			.fillMaxWidth(),
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
	tags: Set<String>,
	component: ViewEntry,
	modifier: Modifier = Modifier,
) {
	HdRegion(
		modifier = modifier,
		contentSpacing = Ui.Padding.L,
		title = Res.string.encyclopedia_entry_tags_label.get(),
	) {
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
	HdRegion(
		modifier = modifier,
		contentSpacing = Ui.Padding.L,
		title = Res.string.encyclopedia_entry_aliases_label.get(),
	) {
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
	HdRegion(
		modifier = Modifier
			.padding(horizontal = Ui.Padding.XXL)
			.padding(top = 28.dp),
		contentSpacing = Ui.Padding.L,
		title = Res.string.encyclopedia_entry_appears_in_label.get(),
		headerTrailing = {
			HdMonoLabel(
				text = "${state.appearsIn.size}",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		},
	) {
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
					.hdControlFrame(MaterialTheme.colorScheme.outlineVariant)
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

/** The entry's placements, a chip per timeline; left out when it is on none. */
@Composable
private fun TimelinesZone(
	state: ViewEntry.State,
	component: ViewEntry,
) {
	if (state.timelines.isEmpty()) return
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XXL)
			.padding(top = 28.dp),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdSectionHeader(
			marker = "—",
			title = Res.string.timeline_subject_timelines_label.get(),
		)
		TimelinePlacementChips(
			placements = state.timelines,
			onShow = component::showTimelinePlacement,
		)
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
private fun TagAddDialog(
	state: ViewEntry.State,
	component: ViewEntry,
	scope: CoroutineScope,
) {
	val mainDispatcher = rememberMainDispatcher()
	var newTagsText by rememberSaveable { mutableStateOf("") }
	val existingTags = state.content?.tags.orEmpty()
	val suggestions = rememberTagSuggestions(newTagsText, existingTags, component::suggestTags)

	AnimatedDialog(
		visible = state.showTagAdd,
		onCloseRequest = component::endTagAdd,
		dismissOnTapOutside = true,
		onDismissed = { newTagsText = "" },
	) {
		AddTagCard(
			draft = newTagsText,
			onDraftChange = { newTagsText = it },
			suggestions = suggestions,
			onSelectSuggestion = { tag ->
				scope.launch {
					component.addTags(replaceTagPrefix(newTagsText, tag))
					withContext(mainDispatcher) { newTagsText = "" }
				}
			},
			onDone = {
				val draft = newTagsText
				if (draft.isNotBlank()) {
					scope.launch { component.addTags(draft) }
				}
				newTagsText = ""
				requestDismiss()
			},
		)
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
	var newAliasText by rememberSaveable { mutableStateOf("") }

	AnimatedDialog(
		visible = state.showAliasAdd,
		onCloseRequest = component::endAliasAdd,
		dismissOnTapOutside = true,
		onDismissed = { newAliasText = "" },
	) {
		AliasAddCard(
			alias = newAliasText,
			onAliasChange = { newAliasText = it },
			onDone = {
				val alias = newAliasText
				if (alias.isBlank()) {
					requestDismiss()
				} else {
					scope.launch {
						val result = component.addAlias(alias)
						withContext(mainDispatcher) {
							if (result.error == EntryError.NONE) {
								newAliasText = ""
								requestDismiss()
							}
						}
						reportSaveResult(result, rootSnackbar, scope, strRes)
					}
				}
			},
		)
	}
}

@Composable
internal fun AliasAddCard(
	alias: String,
	onAliasChange: (String) -> Unit,
	onDone: () -> Unit,
) {
	HdHairlineDialogShell(
		title = Res.string.encyclopedia_entry_add_alias_dialog_title.get(),
		onClose = null,
		closeContentDescription = null,
	) {
		HdHairlineField(
			label = Res.string.encyclopedia_entry_alias_hint.get(),
			value = alias,
			onValueChange = onAliasChange,
			singleLine = true,
		)

		SpacerL()

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.End,
		) {
			HdHairlineButton(
				label = if (alias.isBlank()) {
					Res.string.encyclopedia_entry_add_alias_done.get()
				} else {
					Res.string.encyclopedia_entry_add_alias_button.get()
				},
				onClick = onDone,
				emphasised = true,
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
