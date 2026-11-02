package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSuggestField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdRungDateField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdReferenceChipBucket
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdReferenceChipModel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdGlyphColor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMark
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdSwatchColor
import com.darkrockstudios.apps.hammer.common.compose.references.ReferenceBucket
import com.darkrockstudios.apps.hammer.common.compose.references.label
import com.darkrockstudios.apps.hammer.common.compose.references.referenceBucket
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.timeline_calendar_gregorian
import com.darkrockstudios.apps.hammer.timeline_calendar_story
import com.darkrockstudios.apps.hammer.timeline_calendar_story_epoch
import com.darkrockstudios.apps.hammer.timeline_date_add_end
import com.darkrockstudios.apps.hammer.timeline_date_end
import com.darkrockstudios.apps.hammer.timeline_date_remove_end
import com.darkrockstudios.apps.hammer.timeline_date_start
import com.darkrockstudios.apps.hammer.timeline_error_date_invalid
import com.darkrockstudios.apps.hammer.timeline_error_date_required
import com.darkrockstudios.apps.hammer.timeline_error_duplicate_placement
import com.darkrockstudios.apps.hammer.timeline_error_end_before_start
import com.darkrockstudios.apps.hammer.timeline_error_event_not_found
import com.darkrockstudios.apps.hammer.timeline_error_label_too_long
import com.darkrockstudios.apps.hammer.timeline_error_lane_invalid
import com.darkrockstudios.apps.hammer.timeline_error_not_allowed
import com.darkrockstudios.apps.hammer.timeline_error_reference_not_found
import com.darkrockstudios.apps.hammer.timeline_error_subject_not_found
import com.darkrockstudios.apps.hammer.timeline_error_subject_required
import com.darkrockstudios.apps.hammer.timeline_error_subject_wrong_type
import com.darkrockstudios.apps.hammer.timeline_error_tag_too_long
import com.darkrockstudios.apps.hammer.timeline_error_title_too_long
import com.darkrockstudios.apps.hammer.timeline_kind_entry
import com.darkrockstudios.apps.hammer.timeline_kind_event
import com.darkrockstudios.apps.hammer.timeline_kind_scene
import com.darkrockstudios.apps.hammer.timeline_lane_hint
import com.darkrockstudios.apps.hammer.timeline_lane_label
import com.darkrockstudios.apps.hammer.timeline_lane_placeholder
import com.darkrockstudios.apps.hammer.timeline_lane_swatch_description
import com.darkrockstudios.apps.hammer.timeline_milestone_description

@Composable
internal fun TimelineKind.label(): String = when (this) {
	TimelineKind.EVENT -> Res.string.timeline_kind_event
	TimelineKind.SCENE -> Res.string.timeline_kind_scene
	TimelineKind.ENTRY -> Res.string.timeline_kind_entry
}.get()

/** The kind's glyph on its swatch: a placed entry on the EVENT colour, the rest on neutral ink. */
@Composable
internal fun TimelineKindMark(kind: TimelineKind, modifier: Modifier = Modifier, size: Dp = 18.dp) {
	val isEntry = kind == TimelineKind.ENTRY
	val swatch = if (isEntry) {
		LocalHammerColors.current.colorFor(EntryType.EVENT)
	} else {
		MaterialTheme.colorScheme.onSurfaceVariant
	}
	val glyphColor = if (isEntry) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface
	val description = kind.label()
	Box(
		modifier = modifier
			.size(size)
			.background(swatch, RectangleShape)
			.semantics { contentDescription = description },
		contentAlignment = Alignment.Center,
	) {
		Text(text = kind.hdMark(), style = MaterialTheme.typography.labelMedium, color = glyphColor)
	}
}

/** The ink diamond that marks a milestone. */
@Composable
internal fun MilestoneMark(modifier: Modifier = Modifier, size: Dp = 8.dp) {
	val description = Res.string.timeline_milestone_description.get()
	Box(
		modifier = modifier
			.semantics { contentDescription = description }
			.size(size + 2.dp),
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.size(size)
				.rotate(45f)
				.background(MaterialTheme.colorScheme.onSurface, RectangleShape),
		)
	}
}

/** A lane's colour square, by its position in the lane list. */
@Composable
internal fun LaneSwatch(lane: String, index: Int, modifier: Modifier = Modifier, size: Dp = 10.dp) {
	val description = Res.string.timeline_lane_swatch_description.get(lane)
	Box(
		modifier = modifier
			.size(size)
			.background(LocalHammerColors.current.laneColor(index), RectangleShape)
			.semantics { contentDescription = description },
	)
}

/** Lane input: free text, existing lanes offered under it in their colours. */
@Composable
internal fun LaneField(
	value: String,
	onValueChange: (String) -> Unit,
	lanes: List<String>,
	suggest: (String) -> List<String>,
	modifier: Modifier = Modifier,
	onFocusChanged: (Boolean) -> Unit = {},
	testTag: String? = null,
) {
	val colors = LocalHammerColors.current
	val matched = lanes.indexOf(value.trim())
	HdHairlineSuggestField(
		label = Res.string.timeline_lane_label.get(),
		value = value,
		onValueChange = onValueChange,
		suggest = suggest,
		hint = Res.string.timeline_lane_hint.get(),
		placeholder = Res.string.timeline_lane_placeholder.get(),
		suggestionAccent = { lane -> lanes.indexOf(lane).takeIf { it >= 0 }?.let(colors::laneColor) },
		trailing = if (matched >= 0) {
			{ LaneSwatch(lane = value.trim(), index = matched) }
		} else {
			null
		},
		modifier = modifier,
		onFocusChanged = onFocusChanged,
		testTag = testTag,
	)
}

/**
 * [items] in their reference buckets (Characters, Locations, Other, Scenes), skipping empty ones.
 * A null [onRemove] shows them read-only.
 */
@Composable
internal fun TimelineReferenceBuckets(
	items: List<CatalogItem>,
	onOpen: (CatalogItem) -> Unit,
	onRemove: ((CatalogItem) -> Unit)?,
	removeDescription: String,
	modifier: Modifier = Modifier,
) {
	val grouped = items.groupBy { item ->
		if (item.kind == CatalogKind.Scene) ReferenceBucket.Scenes else (item.entryType ?: EntryType.THING).referenceBucket()
	}
	Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
		ReferenceBucket.entries.forEach { bucket ->
			val inBucket = grouped[bucket].orEmpty()
			if (inBucket.isEmpty()) return@forEach
			val byKey = inBucket.associateBy { it.id }
			HdReferenceChipBucket(
				label = bucket.label(),
				chips = inBucket.map { item ->
					HdReferenceChipModel(
						key = item.id,
						glyph = item.hdMark(),
						swatchColor = item.hdSwatchColor(),
						glyphColor = item.hdGlyphColor(),
						name = item.name,
					)
				},
				onClick = { chip -> byKey[chip.key]?.let(onOpen) },
				onAction = onRemove?.let { remove -> { chip -> byKey[chip.key]?.let(remove) } },
				actionContentDescription = removeDescription,
			)
		}
	}
}

/** The toast for a failed create or save; null for [TimeLineEventError.NONE]. */
internal suspend fun TimeLineEventError.toastText(strRes: StrRes): String? = when (this) {
	TimeLineEventError.NONE -> null
	TimeLineEventError.TAG_TOO_LONG -> strRes.get(Res.string.timeline_error_tag_too_long, TimelineLimits.MAX_TAG)
	TimeLineEventError.LANE_INVALID -> strRes.get(Res.string.timeline_error_lane_invalid, TimelineLimits.MAX_LANE)
	TimeLineEventError.TITLE_TOO_LONG -> strRes.get(Res.string.timeline_error_title_too_long, TimelineLimits.MAX_TITLE)
	TimeLineEventError.LABEL_TOO_LONG -> strRes.get(Res.string.timeline_error_label_too_long, TimelineLimits.MAX_LABEL)
	TimeLineEventError.SUBJECT_REQUIRED -> strRes.get(Res.string.timeline_error_subject_required)
	TimeLineEventError.SUBJECT_WRONG_TYPE -> strRes.get(Res.string.timeline_error_subject_wrong_type)
	TimeLineEventError.SUBJECT_NOT_FOUND -> strRes.get(Res.string.timeline_error_subject_not_found)
	TimeLineEventError.DUPLICATE_PLACEMENT -> strRes.get(Res.string.timeline_error_duplicate_placement)
	TimeLineEventError.REFERENCE_NOT_FOUND -> strRes.get(Res.string.timeline_error_reference_not_found)
	TimeLineEventError.DATE_REQUIRED -> strRes.get(Res.string.timeline_error_date_required)
	TimeLineEventError.DATE_INVALID -> strRes.get(Res.string.timeline_error_date_invalid)
	TimeLineEventError.END_BEFORE_START -> strRes.get(Res.string.timeline_error_end_before_start)
	TimeLineEventError.EVENT_NOT_FOUND -> strRes.get(Res.string.timeline_error_event_not_found)
	TimeLineEventError.SUBJECT_NOT_ALLOWED,
	TimeLineEventError.REFERENCES_NOT_ALLOWED,
	TimeLineEventError.CONTENT_NOT_ALLOWED,
	TimeLineEventError.TAGS_NOT_ALLOWED,
	TimeLineEventError.SELF_REFERENCE,
	TimeLineEventError.MOVE_NOT_ALLOWED,
	TimeLineEventError.MOVE_FAILED -> strRes.get(Res.string.timeline_error_not_allowed)
}

/** Start, then End behind a `+ end` affordance until it has a value. */
@Composable
internal fun DateRows(
	calendar: CalendarDef,
	start: StoryDate?,
	end: StoryDate?,
	onStartChange: (StoryDate?) -> Unit,
	onEndChange: (StoryDate?) -> Unit,
	modifier: Modifier = Modifier,
	onFocusChanged: (Boolean) -> Unit = {},
	startTag: String? = null,
	endTag: String? = null,
	addEndTag: String? = null,
) {
	var endShown by rememberSaveable { mutableStateOf(end != null) }
	var startFocused by remember { mutableStateOf(false) }
	var endFocused by remember { mutableStateOf(false) }
	Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
		HdRungDateField(
			label = Res.string.timeline_date_start.get(),
			calendar = calendar,
			value = start,
			onValueChange = onStartChange,
			onFocusChanged = {
				startFocused = it
				onFocusChanged(startFocused || endFocused)
			},
			testTag = startTag,
		)
		if (endShown || end != null) {
			HdRungDateField(
				label = Res.string.timeline_date_end.get(),
				calendar = calendar,
				value = end,
				onValueChange = onEndChange,
				onFocusChanged = {
					endFocused = it
					onFocusChanged(startFocused || endFocused)
				},
				testTag = endTag,
			)
			HdHairlineButton(
				label = Res.string.timeline_date_remove_end.get(),
				onClick = {
					endShown = false
					onEndChange(null)
				},
			)
		} else {
			HdHairlineButton(
				label = Res.string.timeline_date_add_end.get(),
				onClick = { endShown = true },
				modifier = if (addEndTag != null) Modifier.testTag(addEndTag) else Modifier,
			)
		}
	}
}

/** `Story calendar · Landing`, `Story calendar` or `Gregorian`. */
@Composable
internal fun CalendarDef.displayName(): String = when (preset) {
	CalendarPreset.STORY -> epochName?.trim()?.takeIf { it.isNotEmpty() }
		?.let { Res.string.timeline_calendar_story_epoch.get(it) }
		?: Res.string.timeline_calendar_story.get()

	CalendarPreset.GREGORIAN -> Res.string.timeline_calendar_gregorian.get()
}
