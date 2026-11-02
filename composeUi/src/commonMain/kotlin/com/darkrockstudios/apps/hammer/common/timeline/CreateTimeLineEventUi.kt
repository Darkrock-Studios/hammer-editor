package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.markdown.countWords
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.entitypicker.EntityChip
import com.darkrockstudios.apps.hammer.common.compose.entitypicker.EntityPickerDialog
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.find
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val TIME_LINE_CREATE_DATE_TAG = "timeline-create-date"
const val TIME_LINE_CREATE_TAGS_TAG = "timeline-create-tags"
const val TIME_LINE_CREATE_CONTENT_TAG = "timeline-create-content"
const val TIME_LINE_CREATE_CONFIRM_TAG = "timeline-create-confirm"
const val TIME_LINE_CREATE_KIND_TAG = "timeline-create-kind"
const val TIME_LINE_CREATE_SUBJECT_TAG = "timeline-create-subject"
const val TIME_LINE_CREATE_TITLE_TAG = "timeline-create-title"
const val TIME_LINE_CREATE_LANE_TAG = "timeline-create-lane"
const val TIME_LINE_CREATE_MILESTONE_TAG = "timeline-create-milestone"
const val TIME_LINE_REFERENCES_TAG = "timeline-references"

private val Kinds = listOf(TimelineKind.EVENT, TimelineKind.SCENE, TimelineKind.ENTRY)

/** Scenes, or entries of type EVENT: the only entries a timeline can place. */
internal fun subjectFilter(kind: TimelineKind): CatalogFilter = when (kind) {
	TimelineKind.ENTRY -> CatalogFilter(kinds = setOf(CatalogKind.Entry), entryTypes = setOf(EntryType.EVENT))
	else -> CatalogFilter(kinds = setOf(CatalogKind.Scene))
}

internal val ReferenceFilter = CatalogFilter(kinds = setOf(CatalogKind.Scene, CatalogKind.Entry))

/** Applies a picker's new selection as adds and removes against [current]. */
internal fun applyReferencePicks(
	current: List<CatalogItem>,
	picked: List<CatalogRef>,
	catalog: List<CatalogItem>,
	add: (CatalogItem) -> Unit,
	remove: (Int) -> Unit,
) {
	val currentRefs = current.map { it.ref }.toSet()
	picked.filter { it !in currentRefs }.forEach { ref -> catalog.find(ref)?.let(add) }
	current.filter { it.ref !in picked }.forEach { remove(it.id) }
}

@Composable
fun CreateTimeLineEventUi(
	component: CreateTimeLineEvent,
	scope: CoroutineScope,
	modifier: Modifier,
	rootSnackbar: RootSnackbarHostState,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	val contentText by component.contentText.subscribeAsState()
	var dateText by rememberSaveable { mutableStateOf("") }
	var dateFocused by remember { mutableStateOf(false) }
	var titleFocused by remember { mutableStateOf(false) }
	var laneFocused by remember { mutableStateOf(false) }
	var resetVersion by remember { mutableStateOf(0) }
	var pickingSubject by remember { mutableStateOf(false) }
	var pickingReferences by remember { mutableStateOf(false) }
	val tags = rememberSaveableStringList()

	val isEvent = state.kind == TimelineKind.EVENT
	val canCreate = isEvent || state.subject != null
	val wordCount = remember(contentText) { countWords(contentText) }
	val charCount = contentText.length

	Card(
		modifier = modifier.padding(Ui.Padding.XL)
			.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL),
		shape = RectangleShape,
	) {
		Column(modifier = Modifier.fillMaxWidth()) {

			CollapseWhileTyping {
				Column(modifier = Modifier.fillMaxWidth()) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(
								start = Ui.Padding.XL,
								end = Ui.Padding.XL,
								top = Ui.Padding.XL,
								bottom = Ui.Padding.L,
							),
						verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
					) {
						HdSectionHeader(
							marker = Res.string.timeline_create_marker.get(),
							title = Res.string.timeline_create_header.get(),
							trailing = {
								HdMonoLabel(text = Res.string.timeline_create_draft_label.get())
							},
						)
					}

					HorizontalDivider(
						thickness = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
					)
				}
			}

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				CollapseWhileTyping {
					HdHairlineSegmentedPicker(
						options = Kinds,
						selected = state.kind,
						onSelect = component::setKind,
						label = { it.label() },
						title = Res.string.timeline_kind_label.get(),
						modifier = Modifier.testTag(TIME_LINE_CREATE_KIND_TAG),
					)
				}

				// Each field below hides while the body editor has focus, but stays while it holds focus
				// itself so it remains reachable.
				if (isEvent) {
					CollapseWhileTyping(keepVisible = titleFocused) {
						HdHairlineField(
							label = Res.string.timeline_title_label.get(),
							value = state.title,
							onValueChange = component::setTitle,
							hint = Res.string.timeline_title_hint.get(),
							placeholder = Res.string.timeline_title_placeholder.get(),
							onFocusChanged = { titleFocused = it },
							testTag = TIME_LINE_CREATE_TITLE_TAG,
						)
					}
				} else {
					SubjectRow(
						kind = state.kind,
						subject = state.subject,
						onChoose = { pickingSubject = true },
						onClear = { component.setSubject(null) },
					)
				}

				CollapseWhileTyping(keepVisible = dateFocused) {
					HdHairlineField(
						label = Res.string.timeline_when_label.get(),
						value = dateText,
						onValueChange = { dateText = it },
						hint = Res.string.timeline_create_date_hint.get(),
						placeholder = Res.string.timeline_create_date_placeholder.get(),
						onFocusChanged = { dateFocused = it },
						testTag = TIME_LINE_CREATE_DATE_TAG,
					)
				}

				CollapseWhileTyping(keepVisible = laneFocused) {
					Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
						LaneField(
							value = state.lane,
							onValueChange = component::setLane,
							lanes = state.lanes,
							suggest = component::suggestLanes,
							onFocusChanged = { laneFocused = it },
							testTag = TIME_LINE_CREATE_LANE_TAG,
						)
						HdHairlineToggleRow(
							checked = state.milestone,
							onCheckedChange = component::setMilestone,
							label = Res.string.timeline_milestone_label.get(),
							hint = Res.string.timeline_milestone_hint.get(),
							modifier = Modifier.testTag(TIME_LINE_CREATE_MILESTONE_TAG),
						)
					}
				}

				if (isEvent) {
					HdHairlineTagField(
						label = Res.string.timeline_create_tags_label.get(),
						tags = tags,
						onTagsChange = {
							tags.clear()
							tags.addAll(it)
						},
						hint = Res.string.timeline_create_tags_hint.get(),
						placeholder = Res.string.timeline_create_tags_placeholder.get(),
						suggestTags = component::suggestTags,
						testTag = TIME_LINE_CREATE_TAGS_TAG,
					)

					CollapseWhileTyping {
						ReferencesSection(
							references = state.references,
							onAdd = { pickingReferences = true },
							onRemove = { component.removeReference(it.id) },
						)
					}
				}
			}

			if (isEvent) {
				Box(
					modifier = Modifier
						.padding(horizontal = Ui.Padding.XL)
						.padding(bottom = Ui.Padding.L)
						.fillMaxWidth()
						.weight(1f, fill = true)
						.border(
							width = Dp.Hairline,
							color = MaterialTheme.colorScheme.outlineVariant,
							shape = RectangleShape,
						),
				) {
					key(resetVersion) {
						MarkdownEditField(
							initialMarkdown = contentText,
							onMarkdownChanged = { component.onContentChanged(it) },
							enableSpellChecking = state.spellCheckAllowed,
							contentPadding = PaddingValues(Ui.Padding.XL),
							testTag = TIME_LINE_CREATE_CONTENT_TAG,
							modifier = Modifier
								.fillMaxWidth()
								.widthIn(max = TextEditorDefaults.MAX_WIDTH),
						)
					}
				}
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (isEvent) {
					HdMonoLabel(text = Res.string.timeline_create_word_count.get(wordCount, charCount))
				}
				Spacer(modifier = Modifier.weight(1f))
				HdHairlineButton(
					label = Res.string.timeline_create_cancel_button.get(),
					onClick = { component.closeCreation() },
				)
				HdHairlineButton(
					label = Res.string.timeline_create_create_button.get(),
					emphasised = canCreate && (!isEvent || contentText.isNotBlank()),
					enabled = canCreate,
					modifier = Modifier.testTag(TIME_LINE_CREATE_CONFIRM_TAG),
					onClick = {
						scope.launch {
							val error = component.create(dateText, contentText, tags.toSet())
							if (error == TimeLineEventError.NONE) {
								launch {
									rootSnackbar.showSnackbar(strRes.get(Res.string.timeline_create_toast_success))
								}
								dateText = ""
								tags.clear()
								resetVersion++
								component.closeCreation()
							} else {
								error.toastText(strRes)?.let { launch { rootSnackbar.showSnackbar(it) } }
							}
						}
					},
				)
			}
		}
	}

	val subjectKind = state.kind
	if (subjectKind != TimelineKind.EVENT) {
		EntityPickerDialog(
			visible = pickingSubject,
			title = if (subjectKind == TimelineKind.SCENE) {
				Res.string.timeline_pick_scene_title.get()
			} else {
				Res.string.timeline_pick_entry_title.get()
			},
			items = state.catalog,
			filter = subjectFilter(subjectKind),
			multiple = false,
			selected = listOfNotNull(state.subject?.ref),
			onSelectedChange = { refs -> component.setSubject(refs.firstOrNull()?.let { state.catalog.find(it) }) },
			onDismiss = { pickingSubject = false },
		)
	}

	EntityPickerDialog(
		visible = pickingReferences,
		title = Res.string.timeline_references_pick_title.get(),
		items = state.catalog,
		filter = ReferenceFilter,
		multiple = true,
		selected = state.references.map { it.ref },
		onSelectedChange = { refs ->
			applyReferencePicks(state.references, refs, state.catalog, component::addReference, component::removeReference)
		},
		onDismiss = { pickingReferences = false },
	)

	if (state.confirmDiscard) {
		SimpleConfirm(
			title = Res.string.timeline_view_discard_title.get(),
			message = Res.string.timeline_view_discard_message.get(),
			onDismiss = { component.cancelDiscard() },
		) {
			component.discardAndClose()
		}
	}
}

/** The placed scene or entry as a chip, or a prompt, with the button that opens the picker. */
@Composable
private fun SubjectRow(
	kind: TimelineKind,
	subject: CatalogItem?,
	onChoose: () -> Unit,
	onClear: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		HdMonoLabel(text = Res.string.timeline_subject_label.get())
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			if (subject != null) {
				EntityChip(item = subject, onClick = onChoose, onRemove = onClear)
			} else {
				Text(
					text = Res.string.timeline_subject_none.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Spacer(modifier = Modifier.weight(1f))
			HdHairlineButton(
				label = if (kind == TimelineKind.SCENE) {
					Res.string.timeline_choose_scene.get()
				} else {
					Res.string.timeline_choose_entry.get()
				},
				onClick = onChoose,
				modifier = Modifier.testTag(TIME_LINE_CREATE_SUBJECT_TAG),
			)
		}
	}
}

/** `REFERENCES ─── [+ Add]` over the reference buckets. */
@Composable
internal fun ReferencesSection(
	references: List<CatalogItem>,
	onAdd: () -> Unit,
	onRemove: ((CatalogItem) -> Unit)?,
	modifier: Modifier = Modifier,
	onOpen: (CatalogItem) -> Unit = {},
) {
	Column(
		modifier = modifier.fillMaxWidth().testTag(TIME_LINE_REFERENCES_TAG),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(text = Res.string.timeline_references_header.get())
			Spacer(modifier = Modifier.weight(1f))
			if (onRemove != null) {
				HdHairlineButton(label = Res.string.timeline_references_add.get(), onClick = onAdd)
			}
		}
		if (references.isEmpty()) {
			Text(
				text = Res.string.timeline_references_empty.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		} else {
			TimelineReferenceBuckets(
				items = references,
				onOpen = onOpen,
				onRemove = onRemove,
				removeDescription = Res.string.timeline_references_remove.get(),
			)
		}
	}
}
