package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val ModalMaxWidth = TextEditorDefaults.MAX_WIDTH * 1.25f
private val ModalMaxHeight = 760.dp

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

	val saveChanges: () -> Unit = {
		if (event != null) {
			scope.launch(dispatcherDefault) {
				val success = component.storeEvent(
					event.copy(
						date = dateText,
						content = eventText,
						tags = state.tags,
					)
				)
				if (success) {
					launch { rootSnackbar.showSnackbar(strRes.get(Res.string.timeline_view_toast_save_success)) }
				} else {
					launch { rootSnackbar.showSnackbar(strRes.get(Res.string.timeline_view_toast_save_failure)) }
				}
			}
		}
	}

	Box(
		modifier = modifier
			.fillMaxSize()
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
					.then(if (isEditing) Modifier.fillMaxHeight() else Modifier)
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
				CrumbRow(
					onClose = { component.confirmClose() },
					menuSlot = { DetailViewDropdownMenu(menuItems = state.menuItems) },
				)

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				StampRow(
					isEditing = isEditing,
					event = event,
					savedDate = event?.date,
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

				if (isEditing) {
					EditBody(
						dateText = dateText,
						onDateChanged = { component.onDateTextChanged(it) },
						tags = state.tags.toList(),
						onTagsChanged = { component.onTagsChanged(it.toSet()) },
						eventText = eventText,
						onEventTextChanged = { component.onEventTextChanged(it) },
						modifier = Modifier.weight(1f),
					)

					EditStatusFooter(text = eventText)
				} else {
					ViewBody(
						event = event,
						eventText = eventText,
						onEnterEdit = { component.beginEdit() },
						sharedTransitionScope = sharedTransitionScope,
						animatedVisibilityScope = animatedVisibilityScope,
						modifier = Modifier.weight(1f, fill = false),
					)

					ViewFolioFooter(event = event, eventText = eventText)
				}
			}
		}
	}

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
		Row(
			modifier = Modifier
				.clickable(
					onClick = onClose,
					role = Role.Button,
					onClickLabel = Res.string.timeline_view_close_button.get(),
				)
				.padding(vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(
				text = "← " + Res.string.timeline_view_crumb_root.get(),
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		Spacer(modifier = Modifier.weight(1f))
		menuSlot()
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StampRow(
	isEditing: Boolean,
	event: TimeLineEvent?,
	savedDate: String?,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	onEdit: () -> Unit,
	onSave: () -> Unit,
	onCancel: () -> Unit,
) {
	val sectionTitle = if (isEditing) {
		Res.string.timeline_view_label_editing.get()
	} else {
		Res.string.timeline_view_header.get()
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = "§ III · $sectionTitle",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		if (isEditing) {
			PulsingDot()
		}

		Box(
			modifier = Modifier
				.height(14.dp)
				.width(Dp.Hairline)
				.background(MaterialTheme.colorScheme.outlineVariant),
		)

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

		Spacer(modifier = Modifier.weight(1f))

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
	}
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

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ViewBody(
	event: TimeLineEvent?,
	eventText: String,
	onEnterEdit: () -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	modifier: Modifier = Modifier,
) {
	val scrollState = rememberScrollState()
	Column(
		modifier = modifier
			.fillMaxWidth()
			.verticalScroll(scrollState)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.XL),
	) {
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
					HdTagChip(label = tag, active = true)
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
	}
}

@Composable
private fun EditBody(
	dateText: String,
	onDateChanged: (String) -> Unit,
	tags: List<String>,
	onTagsChanged: (List<String>) -> Unit,
	eventText: String,
	onEventTextChanged: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(modifier = modifier.fillMaxWidth()) {
		HdHairlineField(
			label = Res.string.timeline_view_date_label.get(),
			value = dateText,
			onValueChange = onDateChanged,
			hint = Res.string.timeline_view_date_hint.get(),
			placeholder = Res.string.timeline_view_date_placeholder.get(),
			modifier = Modifier.padding(
				horizontal = Ui.Padding.XL,
				vertical = Ui.Padding.L,
			),
		)

		HdHairlineTagField(
			label = Res.string.timeline_create_tags_label.get(),
			tags = tags,
			onTagsChange = onTagsChanged,
			hint = Res.string.timeline_create_tags_hint.get(),
			placeholder = Res.string.timeline_create_tags_placeholder.get(),
			modifier = Modifier.padding(
				horizontal = Ui.Padding.XL,
				vertical = Ui.Padding.L,
			),
		)

		HorizontalDivider(
			thickness = 2.dp,
			color = MaterialTheme.colorScheme.outline,
		)

		MarkdownEditField(
			initialMarkdown = eventText,
			onMarkdownChanged = onEventTextChanged,
			contentPadding = PaddingValues(Ui.Padding.XL),
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
		)
	}
}

@Composable
private fun ViewFolioFooter(event: TimeLineEvent?, eventText: String) {
	val words = eventText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
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
		HdMonoLabel(
			text = Res.string.timeline_view_word_count_short.get(words),
		)
		val tagCount = event?.tags?.size ?: 0
		if (tagCount > 0) {
			Box(
				modifier = Modifier
					.height(14.dp)
					.width(Dp.Hairline)
					.background(MaterialTheme.colorScheme.outlineVariant),
			)
			HdMonoLabel(text = Res.string.timeline_view_tag_count_short.get(tagCount))
		}
	}
}

@Composable
private fun EditStatusFooter(text: String) {
	val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
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
