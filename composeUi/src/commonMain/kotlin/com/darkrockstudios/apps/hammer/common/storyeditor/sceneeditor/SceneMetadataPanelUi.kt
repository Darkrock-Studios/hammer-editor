package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.references.EntryReferenceBuckets
import com.darkrockstudios.apps.hammer.common.compose.references.ReferenceBucket
import com.darkrockstudios.apps.hammer.common.compose.references.entryTypes
import com.darkrockstudios.apps.hammer.common.compose.references.label
import com.darkrockstudios.apps.hammer.common.compose.references.referenceBucket
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.tagindex.replaceTagPrefix
import com.darkrockstudios.apps.hammer.common.encyclopedia.EntryRefChipLabel
import com.darkrockstudios.apps.hammer.common.timeline.TimelinePlacementChips
import com.darkrockstudios.apps.hammer.common.util.format
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

val SCENE_METADATA_MIN_WIDTH = 300.dp
val SCENE_METADATA_MAX_WIDTH = 600.dp

@Composable
private fun ReferenceBucket.searchPlaceholder(): String = when (this) {
	ReferenceBucket.Characters -> Res.string.scene_editor_metadata_references_add_dialog_search_characters.get()
	ReferenceBucket.Locations -> Res.string.scene_editor_metadata_references_add_dialog_search_locations.get()
	ReferenceBucket.Other, ReferenceBucket.Scenes -> Res.string.scene_editor_metadata_references_add_dialog_search_other.get()
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
		val scrollState = rememberScrollState()
		Box {
			Column(modifier = Modifier.verticalScroll(scrollState)) {

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(start = Ui.Padding.XL, end = Ui.Padding.S, top = Ui.Padding.S, bottom = Ui.Padding.S),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					Text(
						text = Res.string.scene_editor_metadata_title.get(),
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurface,
					)
					HdEntityId(prefix = "SCN", id = state.sceneItem.id)
					Spacer(Modifier.weight(1f))
					IconButton(onClick = closeMetadata) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = Res.string.scene_editor_metadata_hide_button.get(),
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				TimestampStrip(
					wordCount = state.wordCount,
					created = state.metadata.created,
					edited = state.metadata.lastEdited,
				)
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

					if (state.timelines.isNotEmpty()) {
						SpacerXL()
						CollapsableSection(
							startExpanded = true,
							header = {
								HdMonoLabel(
									text = Res.string.timeline_subject_timelines_label.get(),
									modifier = Modifier.padding(end = Ui.Padding.M),
								)
							},
							body = {
								TimelinePlacementChips(
									placements = state.timelines,
									onShow = component::showTimelinePlacement,
									modifier = Modifier.padding(top = Ui.Padding.M),
								)
							},
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

			MpScrollBarColumn(
				modifier = scrollBarOverlay(),
				state = scrollState,
			)
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
		EntryRefBuckets(
			refs = state.confirmedRefs,
			variant = HdReferenceChipVariant.Active,
			onClickRef = component::navigateToEntry,
			onActionRef = component::dismissReference,
			actionDescription = Res.string.scene_editor_metadata_references_dismiss.get(),
		)
	}
}

@Composable
private fun DismissedBody(
	state: SceneMetadataPanel.State,
	component: SceneMetadataPanel,
) {
	Column(modifier = Modifier.padding(top = Ui.Padding.M)) {
		EntryRefBuckets(
			refs = state.dismissedRefs,
			variant = HdReferenceChipVariant.Dismissed,
			onClickRef = component::navigateToEntry,
			onActionRef = component::restoreDismissedReference,
			actionDescription = Res.string.scene_editor_metadata_references_restore.get(),
		)
	}
}

@Composable
private fun EntryRefBuckets(
	refs: List<EntryDef>,
	variant: HdReferenceChipVariant,
	onClickRef: (EntryDef) -> Unit,
	onActionRef: (Int) -> Unit,
	actionDescription: String,
) {
	val byId = remember(refs) { refs.associateBy { it.id } }
	val grouped = remember(refs) { refs.groupBy { it.type.referenceBucket() } }
	EntryReferenceBuckets.forEach { bucket ->
		val bucketRefs = grouped[bucket] ?: return@forEach
		HdReferenceChipBucket(
			label = bucket.label(),
			chips = bucketRefs.map { hdEntryChipModel(key = it.id, type = it.type, name = it.name) },
			onClick = { chip -> byId[chip.key]?.let(onClickRef) },
			onAction = { chip -> byId[chip.key]?.let { onActionRef(it.id) } },
			actionContentDescription = actionDescription,
			variant = variant,
		)
		SpacerL()
	}
}

@Composable
private fun TimestampStrip(
	wordCount: Int,
	created: Instant?,
	edited: Instant?,
) {
	val unset = Res.string.scene_editor_metadata_timestamp_unset.get()
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
	) {
		TimestampCell(
			label = Res.string.scene_editor_metadata_word_count_label.get().removeSuffix(":"),
			value = wordCount.toString(),
			modifier = Modifier.weight(1f),
		)
		TimestampCell(
			label = Res.string.scene_editor_metadata_created_label.get(),
			value = created?.let(::formatStamp) ?: unset,
			modifier = Modifier.weight(1f),
		)
		TimestampCell(
			label = Res.string.scene_editor_metadata_edited_label.get(),
			value = edited?.let(::formatStamp) ?: unset,
			modifier = Modifier.weight(1f),
		)
	}
}

@Composable
private fun TimestampCell(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		HdMonoLabel(
			text = label,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

private fun formatStamp(instant: Instant): String =
	instant.toLocalDateTime(TimeZone.currentSystemDefault())
		.format("dd MMM `yy")
		.uppercase()

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
	var tab by rememberSaveable { mutableStateOf(ReferenceBucket.Characters) }

	val suggestions = remember(query, tab) {
		component.searchEntriesForAdd(query, types = tab.entryTypes())
	}

	AnimatedDialog(
		visible = visible,
		onCloseRequest = onDismiss,
		dismissOnTapOutside = true,
	) {
		AddReferenceCard(
			tab = tab,
			onTabChange = {
				tab = it
				query = ""
			},
			query = query,
			onQueryChange = { query = it },
			suggestions = suggestions,
			onAdd = {
				component.addConfirmedReference(it.entryDef.id)
				query = ""
			},
			onClose = { requestDismiss() },
		)
	}
}

@Composable
internal fun AddReferenceCard(
	tab: ReferenceBucket,
	onTabChange: (ReferenceBucket) -> Unit,
	query: String,
	onQueryChange: (String) -> Unit,
	suggestions: List<SceneMetadataPanel.AddSuggestion>,
	onAdd: (SceneMetadataPanel.AddSuggestion) -> Unit,
	onClose: () -> Unit,
) {
	HdHairlineDialogShell(
		title = Res.string.scene_editor_metadata_references_add_dialog_title.get(),
		onClose = null,
		closeContentDescription = null,
	) {
		Row(modifier = Modifier.fillMaxWidth()) {
			EntryReferenceBuckets.forEach { bucket ->
				HdTabPill(
					label = bucket.label(),
					active = tab == bucket,
					onClick = { onTabChange(bucket) },
					modifier = Modifier.weight(1f),
				)
			}
		}

		SpacerL()

		HdSearchField(
			value = query,
			onValueChange = onQueryChange,
			placeholder = tab.searchPlaceholder(),
			onClear = { onQueryChange("") },
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
							onClick = { onAdd(suggestion) },
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
				onClick = onClose,
				emphasised = true,
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

	val suggestions = rememberTagSuggestions(draft, existingTags, component::suggestTags)

	AnimatedDialog(
		visible = visible,
		onCloseRequest = onDismiss,
		dismissOnTapOutside = true,
	) {
		AddTagCard(
			draft = draft,
			onDraftChange = { draft = it },
			suggestions = suggestions,
			onSelectSuggestion = { tag ->
				component.addTags(replaceTagPrefix(draft, tag))
				draft = ""
			},
			onDone = {
				if (draft.isNotBlank()) component.addTags(draft)
				requestDismiss()
			},
		)
	}
}

@Composable
internal fun AddTagCard(
	draft: String,
	onDraftChange: (String) -> Unit,
	suggestions: List<String>,
	onSelectSuggestion: (String) -> Unit,
	onDone: () -> Unit,
) {
	HdHairlineDialogShell(
		title = Res.string.scene_editor_metadata_tags_add_dialog_title.get(),
		onClose = null,
		closeContentDescription = null,
	) {
		HdHairlineField(
			label = Res.string.scene_editor_metadata_tags_add_dialog_field_label.get(),
			value = draft,
			onValueChange = onDraftChange,
			singleLine = true,
			capitalization = KeyboardCapitalization.None,
			autoCorrectEnabled = false,
		)

		SpacerM()

		HdTagSuggestionStrip(
			suggestions = suggestions,
			onSelect = onSelectSuggestion,
		)

		SpacerL()

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.End,
		) {
			HdHairlineButton(
				label = if (draft.isBlank()) {
					Res.string.scene_editor_metadata_tags_add_dialog_done.get()
				} else {
					Res.string.scene_editor_metadata_tags_add_dialog_add.get()
				},
				onClick = onDone,
				emphasised = true,
			)
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
		Spacer(Modifier.weight(1f))
		Icon(
			imageVector = Icons.Default.Add,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
