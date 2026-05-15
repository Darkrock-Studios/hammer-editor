package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.encyclopedia.EntryRefChipLabel

val SCENE_METADATA_MIN_WIDTH = 300.dp
val SCENE_METADATA_MAX_WIDTH = 600.dp

private enum class RefBucket { Characters, Locations, Other }

private fun EntryType.bucket(): RefBucket = when (this) {
	EntryType.PERSON -> RefBucket.Characters
	EntryType.PLACE -> RefBucket.Locations
	else -> RefBucket.Other
}

@Composable
private fun RefBucket.label(): String = when (this) {
	RefBucket.Characters -> Res.string.scene_editor_metadata_references_characters_label.get()
	RefBucket.Locations -> Res.string.scene_editor_metadata_references_locations_label.get()
	RefBucket.Other -> Res.string.scene_editor_metadata_references_other_label.get()
}

@Composable
private fun RefBucket.searchPlaceholder(): String = when (this) {
	RefBucket.Characters -> Res.string.scene_editor_metadata_references_add_dialog_search_characters.get()
	RefBucket.Locations -> Res.string.scene_editor_metadata_references_add_dialog_search_locations.get()
	RefBucket.Other -> Res.string.scene_editor_metadata_references_add_dialog_search_other.get()
}

@Composable
fun SceneMetadataPanelUi(
	component: SceneMetadataPanel,
	modifier: Modifier = Modifier,
	closeMetadata: () -> Unit
) {
	val state by component.state.subscribeAsState()
	var showAddDialog by rememberSaveable { mutableStateOf(false) }
	var showAddTagDialog by rememberSaveable { mutableStateOf(false) }

	Surface(
		modifier = modifier.widthIn(min = SCENE_METADATA_MIN_WIDTH),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		tonalElevation = 0.dp,
	) {
		Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = Ui.Padding.M, end = Ui.Padding.L, top = Ui.Padding.S, bottom = Ui.Padding.S),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(
					onClick = closeMetadata,
					modifier = Modifier.size(36.dp),
				) {
					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = Res.string.scene_editor_metadata_hide_button.get(),
						tint = MaterialTheme.colorScheme.onSurface,
					)
				}
				Spacer(Modifier.width(Ui.Padding.S))
				Text(
					text = Res.string.scene_editor_metadata_title.get(),
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				verticalAlignment = Alignment.Bottom,
			) {
				Column(modifier = Modifier.weight(1f).padding(end = Ui.Padding.M)) {
					Text(
						text = state.sceneItem.name,
						style = MaterialTheme.typography.titleLarge,
						color = MaterialTheme.colorScheme.onSurface,
					)
					HdEntityId(
						prefix = "SCN",
						id = state.sceneItem.id,
						modifier = Modifier.padding(top = 2.dp),
					)
				}
				Row(verticalAlignment = Alignment.Bottom) {
					HdMonoLabel(
						text = Res.string.scene_editor_metadata_word_count_label.get().removeSuffix(":"),
						modifier = Modifier.padding(end = 6.dp, bottom = 2.dp),
					)
					Text(
						text = "${state.wordCount}",
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurface,
					)
				}
			}
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Column(modifier = Modifier.padding(Ui.Padding.XL)) {

				HdHairlineField(
					label = Res.string.scene_editor_metadata_outline_label.get(),
					value = state.metadata.outline,
					onValueChange = component::updateOutline,
					placeholder = Res.string.scene_editor_metadata_outline_placeholder.get(),
					singleLine = false,
					minLines = 3,
				)

				SpacerXL()

				HdHairlineField(
					label = Res.string.scene_editor_metadata_notes_label.get(),
					value = state.metadata.notes,
					onValueChange = component::updateNotes,
					placeholder = Res.string.scene_editor_metadata_notes_placeholder.get(),
					singleLine = false,
					minLines = 3,
				)

				SpacerXL()

				var isDraftNameValid by remember(state.metadata.currentDraftName) {
					mutableStateOf(component.validateDraftName(state.metadata.currentDraftName))
				}
				HdHairlineField(
					label = Res.string.scene_editor_metadata_draft_name_label.get(),
					value = state.metadata.currentDraftName,
					onValueChange = { newName ->
						isDraftNameValid = component.validateDraftName(newName)
						component.updateDraftName(newName)
					},
					singleLine = true,
				)
				if (!isDraftNameValid) {
					SpacerM()
					Text(
						Res.string.scene_draft_invalid_name.get(),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.error,
					)
				}

				SpacerXL()

				CollapsableSection(
					startExpanded = true,
					header = {
						HdMonoLabel(
							text = Res.string.scene_editor_metadata_references_header.get(),
							modifier = Modifier.padding(end = Ui.Padding.M),
						)
					},
					trailingAction = {
						HdHairlineButton(
							label = Res.string.scene_editor_metadata_references_add_button.get(),
							onClick = { showAddDialog = true },
						)
					},
					body = { ReferencesBody(state, component) }
				)

				if (state.dismissedRefs.isNotEmpty()) {
					SpacerXL()
					CollapsableSection(
						startExpanded = false,
						header = {
							Row(verticalAlignment = Alignment.CenterVertically) {
								HdMonoLabel(
									text = Res.string.scene_editor_metadata_references_dismissed_label.get(),
								)
								Text(
									text = " · ${state.dismissedRefs.size}",
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.padding(start = 4.dp, end = Ui.Padding.M),
								)
							}
						},
						body = { DismissedBody(state, component) }
					)
				}

				SpacerXL()

				CollapsableSection(
					startExpanded = true,
					header = {
						HdMonoLabel(
							text = Res.string.scene_editor_metadata_tags_header.get(),
							modifier = Modifier.padding(end = Ui.Padding.M),
						)
					},
					trailingAction = {
						HdHairlineButton(
							label = Res.string.scene_editor_metadata_tags_add_button.get(),
							onClick = { showAddTagDialog = true },
						)
					},
					body = { TagsBody(state, component) }
				)

				SpacerXL()

				CollapsableSection(
					header = {
						HdMonoLabel(
							text = Res.string.scene_editor_metadata_advanced_header.get(),
							modifier = Modifier.padding(end = Ui.Padding.M),
						)
					},
					body = { AdvancedBody(state) }
				)
			}
		}
	}

	AddReferenceDialog(
		component = component,
		visible = showAddDialog,
		onDismiss = { showAddDialog = false },
	)

	AddTagDialog(
		component = component,
		visible = showAddTagDialog,
		existingTags = state.metadata.tags,
		onDismiss = { showAddTagDialog = false },
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferencesBody(
	state: SceneMetadataPanel.State,
	component: SceneMetadataPanel,
) {
	Column(modifier = Modifier.padding(top = Ui.Padding.M)) {
		if (state.confirmedRefs.isEmpty()) {
			Text(
				Res.string.scene_editor_metadata_references_empty.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			return@Column
		}
		val grouped = remember(state.confirmedRefs) {
			state.confirmedRefs.groupBy { it.type.bucket() }
		}
		RefBucket.entries.forEach { bucket ->
			val refs = grouped[bucket] ?: return@forEach
			RefChipBucket(
				label = bucket.label(),
				refs = refs,
				variant = HdReferenceChipVariant.Active,
				onClickRef = component::navigateToEntry,
				onActionRef = component::dismissReference,
				actionDescription = Res.string.scene_editor_metadata_references_dismiss.get(),
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DismissedBody(
	state: SceneMetadataPanel.State,
	component: SceneMetadataPanel,
) {
	Column(modifier = Modifier.padding(top = Ui.Padding.M)) {
		val grouped = remember(state.dismissedRefs) {
			state.dismissedRefs.groupBy { it.type.bucket() }
		}
		RefBucket.entries.forEach { bucket ->
			val refs = grouped[bucket] ?: return@forEach
			RefChipBucket(
				label = bucket.label(),
				refs = refs,
				variant = HdReferenceChipVariant.Dismissed,
				onClickRef = component::navigateToEntry,
				onActionRef = component::restoreDismissedReference,
				actionDescription = Res.string.scene_editor_metadata_references_restore.get(),
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefChipBucket(
	label: String,
	refs: List<EntryDef>,
	variant: HdReferenceChipVariant,
	onClickRef: (EntryDef) -> Unit,
	onActionRef: (Int) -> Unit,
	actionDescription: String,
) {
	HdMonoLabel(text = label)
	SpacerM()
	FlowRow(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
		verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
	) {
		for (ref in refs) {
			HdReferenceChip(
				type = ref.type,
				name = ref.name,
				onClick = { onClickRef(ref) },
				onAction = { onActionRef(ref.id) },
				actionContentDescription = actionDescription,
				variant = variant,
			)
		}
	}
	SpacerL()
}

@Composable
private fun AdvancedBody(state: SceneMetadataPanel.State) {
	Column(
		modifier = Modifier.padding(top = Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMetadataItem(
			label = Res.string.scene_editor_metadata_entity_id.get(),
			value = state.sceneItem.id.toString(),
			selectable = true,
		)
		HdMetadataItem(
			label = Res.string.scene_editor_metadata_entity_filename.get(),
			value = state.filename,
			selectable = true,
		)
		HdMetadataItem(
			label = Res.string.scene_editor_metadata_entity_path.get(),
			value = state.path,
			selectable = true,
		)
	}
}

@Composable
private fun AddReferenceDialog(
	component: SceneMetadataPanel,
	visible: Boolean,
	onDismiss: () -> Unit,
) {
	var query by rememberSaveable { mutableStateOf("") }
	var tab by rememberSaveable { mutableStateOf(RefBucket.Characters) }

	val suggestions = remember(query, tab) {
		component.searchEntriesForAdd(query)
			.filter { it.entryDef.type.bucket() == tab }
	}

	AnimatedDialog(
		visible = visible,
		onCloseRequest = onDismiss,
		dismissOnTapOutside = true,
	) {
		HdHairlineDialogShell(
			title = Res.string.scene_editor_metadata_references_add_dialog_title.get(),
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.scene_editor_metadata_references_add_dialog_close.get(),
		) {
			Row(modifier = Modifier.fillMaxWidth()) {
				RefBucket.entries.forEach { bucket ->
					TabPill(
						label = bucket.label(),
						active = tab == bucket,
						onClick = {
							tab = bucket
							query = ""
						},
						modifier = Modifier.weight(1f),
					)
				}
			}

			SpacerL()

			HdSearchField(
				value = query,
				onValueChange = { query = it },
				placeholder = tab.searchPlaceholder(),
				onClear = { query = "" },
				modifier = Modifier.fillMaxWidth(),
			)

			SpacerL()

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 120.dp, max = 320.dp),
			) {
				if (suggestions.isEmpty()) {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = Ui.Padding.XL),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = Res.string.scene_editor_metadata_references_add_dialog_empty.get(),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				} else {
					LazyColumn(modifier = Modifier.fillMaxWidth()) {
						items(suggestions, key = { it.entryDef.id }) { suggestion ->
							SuggestionRow(
								suggestion = suggestion,
								onClick = {
									component.addConfirmedReference(suggestion.entryDef.id)
									query = ""
								},
							)
							HorizontalDivider(
								thickness = Dp.Hairline,
								color = MaterialTheme.colorScheme.outlineVariant,
							)
						}
					}
				}
			}

			SpacerL()

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				HdHairlineButton(
					label = Res.string.scene_editor_metadata_references_add_dialog_done.get(),
					onClick = { requestDismiss() },
					emphasised = true,
				)
			}
		}
	}
}

@Composable
private fun TabPill(
	label: String,
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val accent = MaterialTheme.colorScheme.primary
	val onSurface = MaterialTheme.colorScheme.onSurface
	val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
	Box(
		modifier = modifier
			.clickable(onClick = onClick)
			.padding(vertical = Ui.Padding.M),
		contentAlignment = Alignment.Center,
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text(
				text = label,
				style = MaterialTheme.typography.labelLarge,
				color = if (active) onSurface else mutedColor,
			)
			SpacerM()
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(2.dp)
					.background(if (active) accent else MaterialTheme.colorScheme.outlineVariant)
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsBody(
	state: SceneMetadataPanel.State,
	component: SceneMetadataPanel,
) {
	val tags = state.metadata.tags
	if (tags.isEmpty()) {
		Text(
			Res.string.scene_editor_metadata_tags_empty.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = Ui.Padding.M),
		)
		return
	}
	val sortedTags = remember(tags) { tags.sorted() }
	FlowRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = Ui.Padding.M),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		for (tag in sortedTags) {
			HdTagChip(
				label = tag,
				active = true,
				onClick = { component.showGlobalSearchForTag(tag) },
				onRemove = { component.removeTag(tag) },
			)
		}
	}
}

@Composable
private fun AddTagDialog(
	component: SceneMetadataPanel,
	visible: Boolean,
	existingTags: Set<String>,
	onDismiss: () -> Unit,
) {
	var draft by rememberSaveable { mutableStateOf("") }
	LaunchedEffect(visible) { if (!visible) draft = "" }

	val suggestions = remember(draft, existingTags) {
		val prefix = draft.substringAfterLast(' ').trim().removePrefix("#")
		if (prefix.isEmpty()) emptyList()
		else component.suggestTags(prefix).filter { it !in existingTags }
	}

	AnimatedDialog(
		visible = visible,
		onCloseRequest = onDismiss,
		dismissOnTapOutside = true,
	) {
		val submit: () -> Unit = {
			if (draft.isNotBlank()) component.addTags(draft)
			requestDismiss()
		}
		HdHairlineDialogShell(
			title = Res.string.scene_editor_metadata_tags_add_dialog_title.get(),
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.scene_editor_metadata_references_add_dialog_close.get(),
		) {
			HdHairlineField(
				label = Res.string.scene_editor_metadata_tags_header.get(),
				value = draft,
				onValueChange = { draft = it },
				placeholder = Res.string.scene_editor_metadata_tags_add_dialog_placeholder.get(),
				singleLine = true,
			)

			SpacerM()

			HdTagSuggestionStrip(
				suggestions = suggestions,
				onSelect = { tag ->
					component.addTags(tag)
					draft = ""
				},
			)

			SpacerL()

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				HdHairlineButton(
					label = Res.string.scene_editor_metadata_tags_add_dialog_done.get(),
					onClick = submit,
					emphasised = true,
				)
			}
		}
	}
}

@Composable
private fun SuggestionRow(
	suggestion: SceneMetadataPanel.AddSuggestion,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = Ui.Padding.M, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
	) {
		EntryRefChipLabel(
			type = suggestion.entryDef.type,
			name = suggestion.entryDef.name,
		)
		if (suggestion.isDismissed) {
			Spacer(Modifier.width(Ui.Padding.M))
			HdMonoLabel(
				text = Res.string.scene_editor_metadata_references_add_dismissed_hint.get(),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
