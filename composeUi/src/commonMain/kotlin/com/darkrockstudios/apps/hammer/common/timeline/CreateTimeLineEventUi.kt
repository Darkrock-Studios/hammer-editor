package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.markdown.countWords
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
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
const val TIME_LINE_CREATE_START_TAG = "timeline-create-start"
const val TIME_LINE_CREATE_END_TAG = "timeline-create-end"
const val TIME_LINE_CREATE_ADD_END_TAG = "timeline-create-add-end"
const val TIME_LINE_CREATE_BODY_TAG = "timeline-create-body"

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
	val contentText = component.contentText.subscribeAsState()
	var dateText by rememberSaveable { mutableStateOf("") }
	var dateFocused by remember { mutableStateOf(false) }
	var titleFocused by remember { mutableStateOf(false) }
	var laneFocused by remember { mutableStateOf(false) }
	var datesFocused by remember { mutableStateOf(false) }
	var resetVersion by remember { mutableStateOf(0) }
	var pickingSubject by remember { mutableStateOf(false) }
	var pickingReferences by remember { mutableStateOf(false) }
	val tags = rememberSaveableStringList()

	val isEvent = state.kind == TimelineKind.EVENT
	val canCreate = state.canCreate

	HdCreateModal(
		title = Res.string.timeline_create_header.get(),
		modifier = modifier,
		footerMeta = if (isEvent) {
			{
				val text = contentText.value
				val wordCount = remember(text) { countWords(text) }
				HdMonoLabel(text = Res.string.timeline_create_word_count.get(wordCount, text.length))
			}
		} else {
			null
		},
		actions = {
			HdHairlineButton(
				label = Res.string.timeline_create_cancel_button.get(),
				onClick = { component.closeCreation() },
			)
			HdHairlineButton(
				label = Res.string.timeline_create_create_button.get(),
				emphasised = true,
				enabled = canCreate,
				modifier = Modifier.testTag(TIME_LINE_CREATE_CONFIRM_TAG),
				onClick = {
					scope.launch {
						val error = component.create(dateText, contentText.value, tags.toSet())
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
		},
	) {
		val fields: @Composable () -> Unit = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(HdCreateModalDefaults.BodyPadding),
				verticalArrangement = Arrangement.spacedBy(HdCreateModalDefaults.BodySpacing),
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
							optional = true,
							placeholder = Res.string.timeline_title_placeholder.get(),
							onFocusChanged = { titleFocused = it },
							autoFocus = true,
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

				if (state.isDated) {
					CollapseWhileTyping(keepVisible = datesFocused) {
						DateRows(
							calendar = state.calendar ?: CalendarDef(),
							start = state.start,
							end = state.end,
							onStartChange = component::setStart,
							onEndChange = component::setEnd,
							onFocusChanged = { datesFocused = it },
							startTag = TIME_LINE_CREATE_START_TAG,
							endTag = TIME_LINE_CREATE_END_TAG,
							addEndTag = TIME_LINE_CREATE_ADD_END_TAG,
						)
					}
				} else {
					CollapseWhileTyping(keepVisible = dateFocused) {
						HdHairlineField(
							label = Res.string.timeline_when_label.get(),
							value = dateText,
							onValueChange = { dateText = it },
							optional = true,
							placeholder = Res.string.timeline_create_date_placeholder.get(),
							onFocusChanged = { dateFocused = it },
							testTag = TIME_LINE_CREATE_DATE_TAG,
						)
					}
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
		}

		// One FieldsOverBody for every kind, so switching kind keeps the fields' own state.
		FieldsOverBody(
			bodyMinHeight = EventBodyMinHeight + 22.dp,
			modifier = Modifier.weight(1f, fill = isEvent),
			fields = fields,
			body = if (isEvent) {
				{
					Column(
						modifier = Modifier
							.padding(horizontal = 28.dp)
							.padding(bottom = 22.dp)
							.fillMaxWidth()
							.testTag(TIME_LINE_CREATE_BODY_TAG),
						verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
					) {
						HdFieldLabel(text = Res.string.timeline_create_body_label.get())
						key(resetVersion) {
							MarkdownEditField(
								initialMarkdown = remember { contentText.value },
								onMarkdownChanged = { component.onContentChanged(it) },
								enableSpellChecking = state.spellCheckAllowed,
								contentPadding = PaddingValues(Ui.Padding.XL),
								testTag = TIME_LINE_CREATE_CONTENT_TAG,
								modifier = Modifier.fillMaxWidth(),
							)
						}
					}
				}
			} else {
				null
			},
		)
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
			items = state.subjectChoices,
			filter = subjectFilter(subjectKind),
			multiple = false,
			selected = remember(state.subject) { listOfNotNull(state.subject?.ref) },
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
		selected = remember(state.references) { state.references.map { it.ref } },
		onSelectedChange = { refs ->
			applyReferencePicks(state.references, refs, state.catalog, component::addReference, component::removeReference)
		},
		onDismiss = { pickingReferences = false },
	)

	if (state.confirmDiscard) {
		SimpleConfirm(
			title = Res.string.timeline_view_discard_title.get(),
			message = Res.string.timeline_view_discard_message.get(),
			positiveButton = Res.string.timeline_view_discard_confirm.get(),
			negativeButton = Res.string.timeline_view_discard_keep.get(),
			destructive = true,
			kind = Res.string.confirm_kind_discard.get(),
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
		HdFieldLabel(text = Res.string.timeline_subject_label.get())
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
			HdFieldLabel(text = Res.string.timeline_references_header.get())
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
