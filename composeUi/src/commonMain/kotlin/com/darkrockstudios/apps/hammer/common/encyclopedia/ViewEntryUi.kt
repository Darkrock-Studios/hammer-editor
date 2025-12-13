package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
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
	val state by component.state.subscribeAsState()

	val filePickerLauncher = rememberFilePickerLauncher(
		type = FileKitType.Image
	) { file ->
		if (file != null) {
			scope.launch { component.setImage(file.absolutePath()) }
		}
		component.closeAddImageDialog()
	}

	var entryNameText by rememberSaveable { mutableStateOf(state.content?.name ?: "") }
	var entryText by rememberSaveable { mutableStateOf(state.content?.text ?: "") }

	var discardConfirm by rememberSaveable { mutableStateOf(false) }

	val screen = LocalScreenCharacteristic.current
	val content = state.content

	LaunchedEffect(state.content) {
		state.content?.let {
			entryNameText = it.name
			entryText = it.text
		}
	}

	BoxWithConstraints(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center
	) {
		with(sharedTransitionScope) {
			Card(
				modifier = modifier
					.padding(top = Ui.Padding.XL, bottom = Ui.Padding.L, start = Ui.Padding.M, end = Ui.Padding.M)
					.heightIn(max = maxHeight)
					.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f)
					.sharedElement(
						sharedContentState = rememberSharedContentState(key = "encyclopedia-card-${state.entryDef.id}"),
						animatedVisibilityScope = animatedVisibilityScope
					),
				elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
			) {
			Column(modifier = Modifier.widthIn(128.dp, 700.dp).wrapContentHeight()) {
				if (state.editName) {
					TextField(
						modifier = Modifier
							.padding(top = Ui.Padding.M, bottom = Ui.Padding.M)
							.wrapContentHeight()
							.fillMaxWidth(),
						value = entryNameText,
						onValueChange = { entryNameText = it },
						placeholder = { Text(Res.string.encyclopedia_entry_name_hint.get()) }
					)
				} else {
					Row(modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.L, start = Ui.Padding.L)) {
						Text(
							entryNameText,
							style = MaterialTheme.typography.displaySmall,
							color = MaterialTheme.colorScheme.onBackground,
							modifier = Modifier
								.weight(1f)
								.sharedElement(
									sharedContentState = rememberSharedContentState(key = "encyclopedia-title-${state.entryDef.id}"),
									animatedVisibilityScope = animatedVisibilityScope
								)
								.clickable { component.startNameEdit() }
						)

						ViewEntryMenuUi(component)

						IconButton(
							onClick = {
								if (state.editName || state.editText) {
									component.confirmClose()
								} else {
									closeEntry()
								}
							},
						) {
							Icon(
								Icons.Filled.Close,
								contentDescription = Res.string.encyclopedia_entry_close_button.get(),
								tint = MaterialTheme.colorScheme.onSurface
							)
						}
					}
				}

				Row(
					modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
					horizontalArrangement = Arrangement.End,
					verticalAlignment = Alignment.CenterVertically
				) {
					if (content != null && (state.editName || state.editText)) {
						IconButton(onClick = {
							scope.launch {
								val result = component.updateEntry(
									name = entryNameText,
									text = entryText,
									tags = content.tags
								)

								if (result.error == EntryError.NONE) {
									withContext(dispatcherMain) {
										component.finishNameEdit()
										component.finishTextEdit()
									}
								}

								reportSaveResult(result, rootSnackbar, scope, strRes)
							}
						}) {
							Icon(
								Icons.Filled.Check,
								Res.string.encyclopedia_entry_edit_save_button.get(),
								tint = MaterialTheme.colorScheme.onSurface
							)
						}

						IconButton(onClick = { discardConfirm = true }) {
							Icon(
								Icons.Filled.Cancel,
								Res.string.encyclopedia_entry_edit_cancel_button.get(),
								tint = MaterialTheme.colorScheme.error
							)
						}

						if (screen.needsExplicitClose) {
							Spacer(modifier = Modifier.size(Ui.Padding.XL))

							HorizontalDivider(
								modifier = Modifier.fillMaxHeight().width(1.dp)
									.padding(top = Ui.Padding.M, bottom = Ui.Padding.M),
								thickness = DividerDefaults.Thickness,
								color = MaterialTheme.colorScheme.outline
							)

							Spacer(modifier = Modifier.size(Ui.Padding.XL))
						}

						if (discardConfirm) {
							SimpleConfirm(
								title = Res.string.encyclopedia_entry_discard_title.get(),
								message = Res.string.encyclopedia_entry_discard_message.get(),
								onDismiss = { discardConfirm = false }
							) {
								entryNameText = content.name
								entryText = content.text

								component.finishNameEdit()
								component.finishTextEdit()

								discardConfirm = false
							}
						}
					}
				}

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				if (screen.windowWidthClass != WindowWidthSizeClass.Compact) {
					Row {
						Image(
							modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.medium),
							state = state,
							showDeleteImageDialog = component::showDeleteImageDialog,
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						)
						Contents(
							modifier = Modifier.weight(1f).wrapContentHeight()
								.verticalScroll(rememberScrollState()),
							component = component,
							state = state,
							editText = state.editText,
							entryText = entryText,
							setEntryText = { entryText = it },
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						) { component.startTextEdit() }
					}
				} else {
					Column(
						modifier = Modifier.fillMaxWidth().wrapContentHeight().verticalScroll(rememberScrollState())
					) {
						Image(
							modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(MaterialTheme.shapes.medium),
							state = state,
							showDeleteImageDialog = component::showDeleteImageDialog,
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						)
						Contents(
							modifier = Modifier.wrapContentHeight(),
							component = component,
							state = state,
							editText = state.editText,
							entryText = entryText,
							setEntryText = { entryText = it },
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
						) { component.startTextEdit() }
					}
				}
			}
			}
		}
	}

	LaunchedEffect(state.showAddImageDialog) {
		if (state.showAddImageDialog) {
			filePickerLauncher.launch()
		}
	}

	if (state.showDeleteImageDialog) {
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_delete_image_title.get(),
			message = Res.string.encyclopedia_entry_delete_image_message.get(),
			onDismiss = { component.closeDeleteImageDialog() }
		) {
			scope.launch {
				component.removeEntryImage()
				state.entryImagePath?.let { path ->
					imageLoader.diskCache?.remove(path)
					imageLoader.memoryCache?.remove(MemoryCache.Key(path))
				}
			}
			component.closeDeleteImageDialog()
		}
	}

	if (state.showDeleteEntryDialog) {
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_delete_title.get(),
			message = Res.string.encyclopedia_entry_delete_message.get(),
			onDismiss = { component.closeDeleteEntryDialog() }
		) {
			scope.launch(dispatcherDefault) {
				if (component.deleteEntry(state.entryDef)) {
					withContext(dispatcherMain) {
						closeEntry()
					}
					rootSnackbar.showSnackbar(strRes.get(Res.string.encyclopedia_entry_delete_toast))
				}
			}
			component.closeDeleteEntryDialog()
		}
	}

	if (state.confirmClose) {
		SimpleConfirm(
			title = Res.string.encyclopedia_entry_discard_title.get(),
			message = Res.string.encyclopedia_entry_discard_message.get(),
			onDismiss = { component.dismissConfirmClose() }
		) {
			component.dismissConfirmClose()
			closeEntry()
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Image(
	modifier: Modifier = Modifier,
	state: ViewEntry.State,
	showDeleteImageDialog: () -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	if (state.entryImagePath != null) {
		Box(modifier = modifier.wrapContentHeight()) {
			with(sharedTransitionScope) {
				with(animatedVisibilityScope) {
					val context = LocalPlatformContext.current
					AsyncImage(
						model = remember(state.entryImagePath) {
							ImageRequest.Builder(context)
								.data(state.entryImagePath)
								.memoryCacheKeyExtras(mapOf("hash" to state.entryImageHash.toString()))
								.placeholderMemoryCacheKey(state.entryImagePath)
								.crossfade(false)
								.build()
						},
						contentDescription = null,
						modifier = Modifier.wrapContentHeight()
							.fillMaxWidth()
							.align(Alignment.TopEnd)
							.animateEnterExit(
								enter = fadeIn(),
								exit = fadeOut()
							)
//							.sharedElement(
//								sharedContentState = rememberSharedContentState(key = "encyclopedia-image-${state.entryDef.id}"),
//								animatedVisibilityScope = animatedVisibilityScope
//							)
							.clip(MaterialTheme.shapes.medium)
							.clickable(onClick = showDeleteImageDialog),
						contentScale = ContentScale.Fit,
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun Contents(
	modifier: Modifier = Modifier,
	component: ViewEntry,
	state: ViewEntry.State,
	editText: Boolean,
	entryText: String,
	setEntryText: (String) -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	beginEdit: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val content = state.content

	Column(
		modifier = modifier
			.padding(start = Ui.Padding.XL, end = Ui.Padding.XL, bottom = Ui.Padding.XL)
	) {
		with(sharedTransitionScope) {
			AssistChip(
				onClick = {},
				enabled = false,
				label = { Text(state.entryDef.type.toStringResource().get()) },
				leadingIcon = {
					Icon(
						getEntryTypeIcon(state.entryDef.type),
						state.entryDef.type.toStringResource().get()
					)
				},
				modifier = Modifier
					.padding(end = Ui.Padding.L)
					.sharedElement(
						sharedContentState = rememberSharedContentState(key = "encyclopedia-chip-${state.entryDef.id}"),
						animatedVisibilityScope = animatedVisibilityScope
					)
			)
		}

		Spacer(modifier = Modifier.size(Ui.Padding.XL))

		if (content != null) {
			if (editText) {
				OutlinedTextField(
					value = entryText,
					onValueChange = setEntryText,
					modifier = Modifier.fillMaxWidth()
						.padding(PaddingValues(bottom = Ui.Padding.XL)),
					placeholder = { Text(text = Res.string.encyclopedia_entry_body_empty_placeholder.get()) },
					maxLines = 10,
				)
			} else {
				val text = entryText.ifBlank {
					Res.string.encyclopedia_entry_body_empty_label.get()
				}
				with(sharedTransitionScope) {
					Text(
						text,
						modifier = Modifier
							.fillMaxWidth()
							.sharedElement(
								sharedContentState = rememberSharedContentState(key = "encyclopedia-text-${state.entryDef.id}"),
								animatedVisibilityScope = animatedVisibilityScope
							)
							.clickable { beginEdit() },
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onBackground,
					)
				}
			}

			Spacer(modifier = Modifier.size(Ui.Padding.XL))

			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
				verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
			) {
				for (tag in content.tags) {
					InputChip(
						onClick = {
							component.removeTag(tag)
						},
						label = { Text(tag) },
						trailingIcon = {
							Icon(
								Icons.Filled.Delete,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurface
							)
						},
						enabled = true,
						selected = false
					)
				}

				InputChip(
					onClick = {
						component.startTagAdd()
					},
					label = { Text(Res.string.encyclopedia_entry_add_tag.get()) },
					leadingIcon = {
						Icon(
							Icons.Filled.Add,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurface
						)
					},
					enabled = true,
					selected = false
				)
			}
		} else {
			CircularProgressIndicator()
		}
	}

	SimpleDialog(
		title = Res.string.encyclopedia_entry_add_tags_dialog_title.get(),
		visible = state.showTagAdd,
		onCloseRequest = component::endTagAdd,
	) {
		var newTagsText by rememberSaveable { mutableStateOf("") }
		TextField(
			modifier = Modifier.fillMaxWidth()
				.padding(PaddingValues(bottom = Ui.Padding.L)),
			value = newTagsText,
			onValueChange = { newTagsText = it },
			placeholder = { Text(Res.string.encyclopedia_create_entry_tags_label.get()) }
		)
		Button(onClick = {
			scope.launch {
				component.addTags(newTagsText)
				withContext(mainDispatcher) {
					newTagsText = ""
				}
			}
		}) {
			Text(Res.string.encyclopedia_entry_add_tags_button.get())
		}
	}
}

private fun reportSaveResult(
	result: EntryResult,
	rootSnackbar: RootSnackbarHostState,
	scope: CoroutineScope,
	strRes: StrRes
) {
	scope.launch {
		when (result.error) {
			EntryError.NAME_TOO_LONG -> scope.launch {
				rootSnackbar.showSnackbar(
					strRes.get(
						Res.string.encyclopedia_create_entry_toast_too_long,
						EncyclopediaRepository.MAX_NAME_SIZE
					)
				)
			}

			EntryError.NAME_INVALID_CHARACTERS -> scope.launch {
				rootSnackbar.showSnackbar(
					strRes.get(Res.string.encyclopedia_create_entry_toast_invalid_name)
				)
			}

			EntryError.TAG_TOO_LONG -> scope.launch {
				rootSnackbar.showSnackbar(
					strRes.get(
						Res.string.encyclopedia_create_entry_toast_tag_too_long,
						EncyclopediaRepository.MAX_TAG_SIZE
					)
				)
			}

			EntryError.NAME_TOO_SHORT -> scope.launch {
				rootSnackbar.showSnackbar(
					strRes.get(
						Res.string.encyclopedia_create_entry_toast_tag_too_short,
					)
				)
			}

			EntryError.NONE -> {
				scope.launch { rootSnackbar.showSnackbar(strRes.get(Res.string.encyclopedia_create_entry_toast_success)) }
				rootSnackbar.showSnackbar(strRes.get(Res.string.encyclopedia_entry_edit_save_toast))
			}
		}
	}
}