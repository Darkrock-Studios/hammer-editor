package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.entitypicker.EntityPickerDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.formatStoryDateRange
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val TIME_LINE_VIEW_OPEN_SUBJECT_TAG = "timeline-view-open-subject"
const val TIME_LINE_VIEW_SUBJECT_MISSING_TAG = "timeline-view-subject-missing"
const val TIME_LINE_VIEW_SUBJECT_ARCHIVED_TAG = "timeline-view-subject-archived"
const val TIME_LINE_VIEW_LANE_TAG = "timeline-view-lane"
const val TIME_LINE_VIEW_MILESTONE_TAG = "timeline-view-milestone"
const val TIME_LINE_VIEW_START_TAG = "timeline-view-start"
const val TIME_LINE_VIEW_END_TAG = "timeline-view-end"
const val TIME_LINE_VIEW_WHEN_TAG = "timeline-view-when"
const val TIME_LINE_VIEW_FRAME_TAG = "timeline-view-frame"

private val ModalMaxWidth = TextEditorDefaults.MAX_WIDTH * 1.25f
private val ModalMaxHeight = 760.dp
private const val DIALOG_SCRIM_ALPHA = 0.32f
private const val DIALOG_CARD_FADE_MS = 140
private const val DIALOG_BACK_SHRINK = 0.1f

/** How long the dialog takes to grow out of an event's mark, or shrink back into it. */
internal const val DIALOG_MORPH_MS = 320

/** The shared bounds an event's mark on the drawing hands to the dialog opened from it. */
internal fun eventDialogKey(eventId: Int) = "timeline-event-dialog-$eventId"

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun ViewTimeLineEventUi(
	component: ViewTimeLineEvent,
	modifier: Modifier = Modifier,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	dialog: Boolean = false,
	onFrame: (() -> Unit)? = null,
	dialogBackProgress: () -> Float = { 0f },
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

	// As a dialog the event sits on a scrim over the drawing; a tap on the scrim closes it.
	val scrim = MaterialTheme.colorScheme.scrim
	val backdrop = if (dialog) {
		Modifier
			// Thins as a back gesture pulls the dialog away.
			.drawBehind { drawRect(scrim.copy(alpha = DIALOG_SCRIM_ALPHA * (1f - dialogBackProgress() / 2f))) }
			.clickable(interactionSource = null, indication = null) { component.confirmClose() }
	} else {
		Modifier.background(MaterialTheme.colorScheme.surfaceDim)
	}
	Box(
		modifier = modifier
			.fillMaxSize()
			.saveShortcutModifier { if (isEditing) saveChanges() }
			.then(backdrop),
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
					.then(
						if (dialog) {
							// The whole card, frame and all, grows out of the event's mark on the drawing
							// and shrinks back into it, laid out afresh at each size and staying opaque
							// until it has nearly landed. A back gesture shrinks it as it pulls, and a tap
							// on the card is kept from reaching the scrim.
							Modifier
								.sharedBounds(
									sharedContentState = rememberSharedContentState(eventDialogKey(component.eventId)),
									animatedVisibilityScope = animatedVisibilityScope,
									enter = fadeIn(tween(DIALOG_CARD_FADE_MS)),
									exit = fadeOut(tween(DIALOG_CARD_FADE_MS, delayMillis = DIALOG_MORPH_MS - DIALOG_CARD_FADE_MS)),
									boundsTransform = { _, _ -> tween(DIALOG_MORPH_MS, easing = FastOutSlowInEasing) },
									resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
								)
								.graphicsLayer {
									val scale = 1f - DIALOG_BACK_SHRINK * dialogBackProgress()
									scaleX = scale
									scaleY = scale
								}
								.cardFrame()
								.pointerInput(Unit) { detectTapGestures() }
						} else {
							Modifier
								.cardFrame()
								.sharedElement(
									sharedContentState = rememberSharedContentState(
										key = "timeline-card-${event?.id}",
									),
									animatedVisibilityScope = animatedVisibilityScope,
								)
						}
					),
			) {
				CollapseWhileTyping(enabled = isEditing) {
					Column {
						CrumbRow(
							onClose = { component.confirmClose() },
							onFrame = onFrame.takeIf { !isEditing },
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

/** The card's surface and hairline frame. */
@Composable
private fun Modifier.cardFrame(): Modifier = this
	.background(MaterialTheme.colorScheme.surface)
	.border(width = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant, shape = RectangleShape)

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
