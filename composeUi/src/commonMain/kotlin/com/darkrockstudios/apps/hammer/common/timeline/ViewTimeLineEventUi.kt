package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.markdown.countWords
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.entitypicker.EntityPickerDialog
import com.darkrockstudios.apps.hammer.common.compose.images.rememberImageRequest
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateRange
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val TIME_LINE_VIEW_OPEN_SUBJECT_TAG = "timeline-view-open-subject"
const val TIME_LINE_VIEW_SUBJECT_MISSING_TAG = "timeline-view-subject-missing"
const val TIME_LINE_VIEW_LANE_TAG = "timeline-view-lane"
const val TIME_LINE_VIEW_MILESTONE_TAG = "timeline-view-milestone"
const val TIME_LINE_VIEW_START_TAG = "timeline-view-start"
const val TIME_LINE_VIEW_END_TAG = "timeline-view-end"
const val TIME_LINE_VIEW_WHEN_TAG = "timeline-view-when"

private val ModalMaxWidth = TextEditorDefaults.MAX_WIDTH * 1.25f
private val ModalMaxHeight = 760.dp
private val SubjectImageHeight = 220.dp

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun ViewTimeLineEventUi(
	component: ViewTimeLineEvent,
	modifier: Modifier = Modifier,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val strRes = rememberStrRes()
	val dispatcherDefault = rememberDefaultDispatcher()
	val state by component.state.subscribeAsState()
	val dateText by component.dateText.subscribeAsState()
	val eventText by component.contentText.subscribeAsState()
	val event = state.event
	val isEditing = state.isEditing
	val isEvent = state.kind == TimelineKind.EVENT
	var pickingReferences by remember { mutableStateOf(false) }

	val saveChanges: () -> Unit = {
		if (event != null) {
			scope.launch(dispatcherDefault) {
				val error = component.saveEdit()
				val message = if (error == TimeLineEventError.NONE) {
					strRes.get(Res.string.timeline_view_toast_save_success)
				} else {
					error.toastText(strRes) ?: strRes.get(Res.string.timeline_view_toast_save_failure)
				}
				launch { rootSnackbar.showSnackbar(message) }
			}
		}
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.saveShortcutModifier { if (isEditing) saveChanges() }
			.background(MaterialTheme.colorScheme.surfaceDim),
		contentAlignment = Alignment.TopCenter,
	) {
		with(sharedTransitionScope) {
			Column(
				modifier = Modifier
					.padding(Ui.Padding.XL)
					.widthIn(max = ModalMaxWidth)
					.heightIn(max = ModalMaxHeight)
					.fillMaxWidth()
					.then(if (isEditing && isEvent) Modifier.fillMaxHeight() else Modifier)
					.background(MaterialTheme.colorScheme.surface)
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					)
					.sharedElement(
						sharedContentState = rememberSharedContentState(
							key = "timeline-card-${event?.id}",
						),
						animatedVisibilityScope = animatedVisibilityScope,
					),
			) {
				CollapseWhileTyping(enabled = isEditing) {
					Column {
						CrumbRow(
							onClose = { component.confirmClose() },
							menuSlot = { DetailViewDropdownMenu(state.menuItems, ActionPlace.Event, event?.id) },
						)

						HorizontalDivider(
							thickness = Dp.Hairline,
							color = MaterialTheme.colorScheme.outlineVariant,
						)
					}
				}

				StampRow(
					isEditing = isEditing,
					state = state,
					savedDate = storedWhen(state),
					sharedTransitionScope = sharedTransitionScope,
					animatedVisibilityScope = animatedVisibilityScope,
					onEdit = { component.beginEdit() },
					onSave = saveChanges,
					onCancel = { component.confirmDiscard() },
				)

				HorizontalDivider(
					thickness = 2.dp,
					color = MaterialTheme.colorScheme.outline,
				)

				when {
					isEditing && isEvent -> {
						EditBody(
							state = state,
							component = component,
							dateText = dateText,
							eventText = eventText,
							onAddReferences = { pickingReferences = true },
							modifier = Modifier.weight(1f),
						)

						EditStatusFooter(text = eventText)
					}

					isEditing -> {
						PlacementFields(
							state = state,
							component = component,
							dateText = dateText,
							modifier = Modifier.padding(Ui.Padding.XL),
						)
					}

					isEvent -> {
						ViewBody(
							state = state,
							eventText = eventText,
							onEnterEdit = { component.beginEdit() },
							onTagClick = component::showGlobalSearchForTag,
							onTagRemove = { tag -> scope.launch { component.removeTag(tag) } },
							onOpenReference = component::openReference,
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
							modifier = Modifier.weight(1f, fill = false),
						)

						ViewFolioFooter(event = event, eventText = eventText)
					}

					else -> {
						PlacementBody(
							state = state,
							onOpenSubject = component::openSubject,
							onOpenReference = component::openReference,
							modifier = Modifier.weight(1f, fill = false),
						)

						ViewFolioFooter(event = event, eventText = null)
					}
				}
			}
		}
	}

	EntityPickerDialog(
		visible = pickingReferences,
		title = Res.string.timeline_references_pick_title.get(),
		items = state.catalog.filter { it.id != event?.id },
		filter = ReferenceFilter,
		multiple = true,
		selected = state.references.map { it.ref },
		onSelectedChange = { refs ->
			applyReferencePicks(state.references, refs, state.catalog, component::addReference, component::removeReference)
		},
		onDismiss = { pickingReferences = false },
	)

	if (state.confirmClose) {
		SimpleConfirm(
			title = Res.string.timeline_view_discard_title.get(),
			message = Res.string.timeline_view_discard_message.get(),
			onDismiss = { component.cancelClose() }
		) {
			component.closeEvent()
		}
	}

	if (state.confirmDiscard) {
		SimpleConfirm(
			title = Res.string.timeline_view_discard_title.get(),
			message = Res.string.timeline_view_discard_message.get(),
			onDismiss = { component.cancelDiscard() }
		) {
			component.discardEdit()
		}
	}

	if (state.confirmDelete) {
		SimpleConfirm(
			title = Res.string.timeline_view_confirm_delete_title.get(),
			message = Res.string.timeline_view_confirm_delete_message.get(),
			onDismiss = { component.endDeleteEvent() }
		) {
			scope.launch {
				component.deleteEvent()
			}
		}
	}
}

@Composable
private fun CrumbRow(
	onClose: () -> Unit,
	menuSlot: @Composable () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdCrumbBackLink(
			label = Res.string.timeline_view_crumb_root.get(),
			onClick = onClose,
			onClickLabel = Res.string.timeline_view_close_button.get(),
		)
		Spacer(modifier = Modifier.weight(1f))
		menuSlot()
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StampRow(
	isEditing: Boolean,
	state: ViewTimeLineEvent.State,
	savedDate: String?,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	onEdit: () -> Unit,
	onSave: () -> Unit,
	onCancel: () -> Unit,
) {
	val event = state.event
	val sectionTitle = if (isEditing) {
		Res.string.timeline_view_label_editing.get()
	} else {
		state.kind.label()
	}

	HdDetailStampRow(
		stackActionsWhenNarrow = isEditing,
		leading = {
			HdMonoLabel(
				text = "§ III · $sectionTitle",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)

			if (isEditing) {
				PulsingDot()
			}
		},
		meta = {
			MetaRule()

			val metaText = if (isEditing) {
				Res.string.timeline_view_status_unsaved.get()
			} else {
				savedDate?.takeIf { it.isNotBlank() } ?: Res.string.timeline_view_undated.get()
			}
			with(sharedTransitionScope) {
				HdMonoLabel(
					text = metaText,
					modifier = Modifier.sharedElement(
						sharedContentState = rememberSharedContentState(
							key = "timeline-date-${event?.id}",
						),
						animatedVisibilityScope = animatedVisibilityScope,
					),
				)
			}

			val lane = event?.lane
			if (!isEditing && lane != null) {
				MetaRule()
				val index = state.laneIndex
				if (index != null) LaneSwatch(lane = lane, index = index)
				HdMonoLabel(text = lane, maxLines = 1)
			}
		},
		actions = {
			if (isEditing) {
				HdHairlineButton(
					label = Res.string.timeline_view_save_button.get(),
					onClick = onSave,
					emphasised = true,
				)
				HdHairlineButton(
					label = Res.string.timeline_view_cancel_button.get(),
					onClick = onCancel,
				)
			} else {
				HdHairlineButton(
					label = Res.string.timeline_view_edit_button.get(),
					onClick = onEdit,
				)
			}
		},
	)
}

@Composable
private fun MetaRule() {
	Box(
		modifier = Modifier
			.height(14.dp)
			.width(Dp.Hairline)
			.background(MaterialTheme.colorScheme.outlineVariant),
	)
}

@Composable
private fun PulsingDot() {
	val transition = rememberInfiniteTransition(label = "timelineEditingPulse")
	val alpha by transition.animateFloat(
		initialValue = 1f,
		targetValue = 0.35f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 800),
			repeatMode = RepeatMode.Reverse,
		),
		label = "timelineEditingPulseAlpha",
	)
	Box(
		modifier = Modifier
			.size(7.dp)
			.alpha(alpha)
			.background(MaterialTheme.colorScheme.primary, RectangleShape),
	)
}

/** `◆ MILESTONE` when the event is one; the lane sits in the stamp row. */
@Composable
private fun MilestoneLine(event: TimeLineEvent?) {
	if (event?.milestone != true) return
	Row(
		modifier = Modifier
			.padding(bottom = Ui.Padding.L)
			.testTag(TIME_LINE_VIEW_MILESTONE_TAG),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		MilestoneMark()
		HdMonoLabel(text = Res.string.timeline_milestone_label.get(), color = MaterialTheme.colorScheme.onSurface)
	}
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ViewBody(
	state: ViewTimeLineEvent.State,
	eventText: String,
	onEnterEdit: () -> Unit,
	onTagClick: (String) -> Unit,
	onTagRemove: (String) -> Unit,
	onOpenReference: (CatalogItem) -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	modifier: Modifier = Modifier,
) {
	val event = state.event
	val scrollState = rememberScrollState()
	Box(modifier = modifier) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.XL),
		) {
			MilestoneLine(event)

			val title = event?.title
			if (!title.isNullOrBlank()) {
				Text(
					text = title,
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier.padding(bottom = Ui.Padding.L),
				)
			}

			val eventTags = event?.tags
			if (!eventTags.isNullOrEmpty()) {
				FlowRow(
					modifier = Modifier
						.fillMaxWidth()
						.padding(bottom = Ui.Padding.L),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					eventTags.sorted().forEach { tag ->
						HdTagChip(
							label = tag,
							active = true,
							onClick = { onTagClick(tag) },
							onRemove = { onTagRemove(tag) },
						)
					}
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					modifier = Modifier.padding(bottom = Ui.Padding.L),
				)
			}

			with(sharedTransitionScope) {
				MarkdownView(
					markdown = eventText,
					modifier = Modifier
						.fillMaxWidth()
						.sharedElement(
							sharedContentState = rememberSharedContentState(
								key = "timeline-content-${event?.id}",
							),
							animatedVisibilityScope = animatedVisibilityScope,
						)
						.clickable(onClick = onEnterEdit),
				)
			}

			if (state.references.isNotEmpty()) {
				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					modifier = Modifier.padding(vertical = Ui.Padding.L),
				)
				ReferencesSection(
					references = state.references,
					onAdd = {},
					onRemove = null,
					onOpen = onOpenReference,
				)
			}
		}

		MpScrollBarColumn(
			modifier = scrollBarOverlay(),
			state = scrollState,
		)
	}
}

/** A placement, read-only: what it places, with a way to open it. */
@Composable
private fun PlacementBody(
	state: ViewTimeLineEvent.State,
	onOpenSubject: () -> Unit,
	onOpenReference: (CatalogItem) -> Unit,
	modifier: Modifier = Modifier,
) {
	val scrollState = rememberScrollState()
	Box(modifier = modifier) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)
				.padding(Ui.Padding.XL),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			MilestoneLine(state.event)

			when (val subject = state.subject) {
				is SubjectSummary.Entry -> {
					val image = state.subjectImage
					if (image != null) {
						AsyncImage(
							model = rememberImageRequest(path = image.path, version = image.version),
							contentDescription = image.name,
							contentScale = ContentScale.Fit,
							modifier = Modifier
								.fillMaxWidth()
								.height(SubjectImageHeight)
								.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
								.background(MaterialTheme.colorScheme.surfaceContainerLow),
						)
					}
					SubjectHeading(title = subject.entry.name, caption = Res.string.timeline_entry_subtitle.get())
					Excerpt(subject.excerpt)
					HdHairlineButton(
						label = Res.string.timeline_open_entry.get(),
						onClick = onOpenSubject,
						modifier = Modifier.testTag(TIME_LINE_VIEW_OPEN_SUBJECT_TAG),
					)
				}

				is SubjectSummary.Scene -> {
					SubjectHeading(title = subject.scene.name, caption = subject.chapterPath.ifBlank { null })
					Excerpt(subject.excerpt)
					if (state.references.isNotEmpty()) {
						HdMonoLabel(text = Res.string.timeline_cast_header.get())
						TimelineReferenceBuckets(
							items = state.references,
							onOpen = onOpenReference,
							onRemove = null,
							removeDescription = "",
						)
					}
					if (!subject.scene.archived) {
						HdHairlineButton(
							label = Res.string.timeline_open_scene.get(),
							onClick = onOpenSubject,
							modifier = Modifier.testTag(TIME_LINE_VIEW_OPEN_SUBJECT_TAG),
						)
					}
				}

				SubjectSummary.Missing -> {
					HdWarningNotice(
						label = Res.string.timeline_subject_missing.get(),
						message = Res.string.timeline_subject_missing_message.get(),
						modifier = Modifier.testTag(TIME_LINE_VIEW_SUBJECT_MISSING_TAG),
					)
					SubjectHeading(title = state.displayTitle, caption = null)
				}

				null -> Unit
			}
		}

		MpScrollBarColumn(
			modifier = scrollBarOverlay(),
			state = scrollState,
		)
	}
}

@Composable
private fun SubjectHeading(title: String, caption: String?) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(
			text = title,
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
		if (caption != null) HdMonoLabel(text = caption)
	}
}

@Composable
private fun Excerpt(text: String) {
	if (text.isBlank()) return
	Text(
		text = text,
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurface,
	)
}

/** The stored "when": the dates on a DATED timeline when they fit its calendar, else the label. */
@Composable
private fun storedWhen(state: ViewTimeLineEvent.State): String? {
	val event = state.event ?: return null
	val start = event.start
	if (state.isDated && start != null) {
		val calendar = state.calendar ?: CalendarDef()
		if (StoryCalendar.validate(calendar, start) == null) {
			return formatStoryDateRange(calendar, start, event.end, rememberStoryDateWords())
		}
	}
	return event.label
}

/** The fields every kind has: when (dates on a DATED timeline), lane and milestone. */
@Composable
private fun PlacementFields(
	state: ViewTimeLineEvent.State,
	component: ViewTimeLineEvent,
	dateText: String,
	modifier: Modifier = Modifier,
	onFocusChanged: (Boolean) -> Unit = {},
) {
	var dateFocused by remember { mutableStateOf(false) }
	var laneFocused by remember { mutableStateOf(false) }
	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
		if (state.isDated) {
			DateRows(
				calendar = state.calendar ?: CalendarDef(),
				start = state.start,
				end = state.end,
				onStartChange = component::onStartChanged,
				onEndChange = component::onEndChanged,
				onFocusChanged = {
					dateFocused = it
					onFocusChanged(dateFocused || laneFocused)
				},
				startTag = TIME_LINE_VIEW_START_TAG,
				endTag = TIME_LINE_VIEW_END_TAG,
			)
		} else {
			HdHairlineField(
				label = Res.string.timeline_when_label.get(),
				value = dateText,
				onValueChange = component::onDateTextChanged,
				hint = Res.string.timeline_view_date_hint.get(),
				placeholder = Res.string.timeline_view_date_placeholder.get(),
				onFocusChanged = {
					dateFocused = it
					onFocusChanged(dateFocused || laneFocused)
				},
				testTag = TIME_LINE_VIEW_WHEN_TAG,
			)
		}
		LaneField(
			value = state.lane,
			onValueChange = component::onLaneChanged,
			lanes = state.lanes,
			suggest = component::suggestLanes,
			onFocusChanged = {
				laneFocused = it
				onFocusChanged(dateFocused || laneFocused)
			},
			testTag = TIME_LINE_VIEW_LANE_TAG,
		)
		HdHairlineToggleRow(
			checked = state.milestone,
			onCheckedChange = component::onMilestoneChanged,
			label = Res.string.timeline_milestone_label.get(),
			hint = Res.string.timeline_milestone_hint.get(),
		)
	}
}

@Composable
private fun EditBody(
	state: ViewTimeLineEvent.State,
	component: ViewTimeLineEvent,
	dateText: String,
	eventText: String,
	onAddReferences: () -> Unit,
	modifier: Modifier = Modifier,
) {
	// Each field hides while the body editor has focus, like the tags; kept visible while it holds
	// focus itself so it stays reachable. CollapseWhileTyping centralizes the IME + debounce rules.
	var titleFocused by remember { mutableStateOf(false) }
	var placementFocused by remember { mutableStateOf(false) }
	val fieldPadding = Modifier.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)

	Column(modifier = modifier.fillMaxWidth()) {
		CollapseWhileTyping(keepVisible = titleFocused) {
			HdHairlineField(
				label = Res.string.timeline_title_label.get(),
				value = state.title,
				onValueChange = component::onTitleChanged,
				hint = Res.string.timeline_title_hint.get(),
				placeholder = Res.string.timeline_title_placeholder.get(),
				onFocusChanged = { titleFocused = it },
				modifier = fieldPadding,
			)
		}

		CollapseWhileTyping(keepVisible = placementFocused) {
			PlacementFields(
				state = state,
				component = component,
				dateText = dateText,
				onFocusChanged = { placementFocused = it },
				modifier = fieldPadding,
			)
		}

		HdHairlineTagField(
			label = Res.string.timeline_create_tags_label.get(),
			tags = state.tags.toList(),
			onTagsChange = { component.onTagsChanged(it.toSet()) },
			hint = Res.string.timeline_create_tags_hint.get(),
			placeholder = Res.string.timeline_create_tags_placeholder.get(),
			suggestTags = component::suggestTags,
			modifier = fieldPadding,
		)

		CollapseWhileTyping {
			ReferencesSection(
				references = state.references,
				onAdd = onAddReferences,
				onRemove = { component.removeReference(it.id) },
				onOpen = component::openReference,
				modifier = fieldPadding,
			)
		}

		CollapseWhileTyping {
			HorizontalDivider(
				thickness = 2.dp,
				color = MaterialTheme.colorScheme.outline,
			)
		}

		MarkdownEditField(
			initialMarkdown = eventText,
			onMarkdownChanged = component::onEventTextChanged,
			enableSpellChecking = state.spellCheckAllowed,
			imagePicker = component::pickImageForText,
			contentPadding = PaddingValues(Ui.Padding.XL),
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
		)
	}
}

@Composable
private fun ViewFolioFooter(event: TimeLineEvent?, eventText: String?) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		event?.id?.let { id ->
			HdEntityId(prefix = "TIME", id = id)
		}
		Spacer(modifier = Modifier.weight(1f))
		if (eventText != null) {
			HdMonoLabel(
				text = Res.string.timeline_view_word_count_short.get(countWords(eventText)),
			)
		}
		val tagCount = event?.tags?.size ?: 0
		if (tagCount > 0) {
			MetaRule()
			HdMonoLabel(text = Res.string.timeline_view_tag_count_short.get(tagCount))
		}
	}
}

@Composable
private fun EditStatusFooter(text: String) {
	val words = countWords(text)
	val chars = text.length

	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(text = Res.string.timeline_view_status_word_char.get(words, chars))
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(text = Res.string.timeline_view_status_hint.get())
	}
}
