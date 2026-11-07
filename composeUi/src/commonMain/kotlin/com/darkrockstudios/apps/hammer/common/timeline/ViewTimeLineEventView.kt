package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.images.rememberImageRequest
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

private val SubjectImageHeight = 220.dp

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
internal fun ViewBody(
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
internal fun PlacementBody(
	state: ViewTimeLineEvent.State,
	onOpenSubject: () -> Unit,
	onOpenReference: (CatalogItem) -> Unit,
	onShowPlacement: (ViewTimeLineEvent.OtherPlacement) -> Unit,
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
					HdHairlineButton(
						label = Res.string.timeline_open_scene.get(),
						onClick = onOpenSubject,
						modifier = Modifier.testTag(TIME_LINE_VIEW_OPEN_SUBJECT_TAG),
					)
				}

				is SubjectSummary.Archived -> {
					HdWarningNotice(
						label = Res.string.timeline_subject_archived.get(),
						message = Res.string.timeline_subject_archived_message.get(),
						modifier = Modifier.testTag(TIME_LINE_VIEW_SUBJECT_ARCHIVED_TAG),
					)
					SubjectHeading(title = subject.scene.name, caption = null)
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

			AlsoOnRow(placements = state.alsoOn, onShow = onShowPlacement)
		}

		MpScrollBarColumn(
			modifier = scrollBarOverlay(),
			state = scrollState,
		)
	}
}

/** `ALSO ON` and a chip per other timeline the subject is placed on; a chip opens it there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlsoOnRow(
	placements: List<ViewTimeLineEvent.OtherPlacement>,
	onShow: (ViewTimeLineEvent.OtherPlacement) -> Unit,
) {
	if (placements.isEmpty()) return
	FlowRow(
		modifier = Modifier
			.fillMaxWidth()
			.testTag(TIME_LINE_VIEW_ALSO_ON_TAG),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		itemVerticalAlignment = Alignment.CenterVertically,
	) {
		HdMonoLabel(text = Res.string.timeline_also_on.get())
		placements.forEach { placement ->
			HdReferenceChip(
				glyph = HD_EVENT_GLYPH,
				swatchColor = MaterialTheme.colorScheme.onSurfaceVariant,
				glyphColor = MaterialTheme.colorScheme.surface,
				name = placement.name,
				onClick = { onShow(placement) },
				onAction = null,
				actionContentDescription = "",
				modifier = Modifier.testTag(timelineAlsoOnChipTag(placement.timelineId)),
			)
		}
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
