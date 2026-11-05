package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.StoryDateProblem
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDateField
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDateProblem
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineRuleProblem
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntityId
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMark
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.parseStoryDateText
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.toCompactText
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_date_bad_calendar
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_date_end_before_start
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_date_not_a_prefix
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_date_out_of_range
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_date_unknown_unit
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_date_hint
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_empty
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_invalid_date
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_milestone_off
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_milestone_on
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_milestone_toggle
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_missing_date
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_placement_no_text
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_blank_unit
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_cached_title_too_long
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_event_has_subject
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_label_too_long
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_lane_blank
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_lane_too_long
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_placement_has_content
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_placement_has_references
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_placement_has_tags
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_placement_needs_subject
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_placement_own_subject
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_self_reference
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_subject_is_reference
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_tag_too_long
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_title_too_long
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_too_many_rungs
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_rule_unit_too_long
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_content
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_end
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_label
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_lane
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_milestone
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_references
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_start
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_tags
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_title
import kotlinx.coroutines.launch

internal const val TIMELINE_CONFLICT_START_TAG = "timeline-conflict-start"
internal const val TIMELINE_CONFLICT_END_TAG = "timeline-conflict-end"

/** The local pane's fields as typed. */
internal data class TimelineEventEdits(
	val title: String,
	val label: String,
	val content: String,
	val lane: String,
	val milestone: Boolean,
	val start: String,
	val end: String,
)

internal sealed interface LocalTimelineEvent {
	data class Resolved(val entity: ApiProjectEntity.TimelineEventEntity) : LocalTimelineEvent

	/** A date field holds text that is neither blank nor a readable date. */
	data class UnreadableDates(val start: Boolean, val end: Boolean) : LocalTimelineEvent
}

internal fun ApiProjectEntity.TimelineEventEntity.toEdits() = TimelineEventEdits(
	title = title.orEmpty(),
	label = label.orEmpty(),
	content = content,
	lane = lane.orEmpty(),
	milestone = milestone,
	start = start?.toCompactText().orEmpty(),
	end = end?.toCompactText().orEmpty(),
)

/**
 * The client copy with [edits] applied. Kind, subject, tags and references stay as the client has
 * them, and a placement keeps its content because it has none to edit.
 */
internal fun buildLocalTimelineEvent(
	client: ApiProjectEntity.TimelineEventEntity,
	edits: TimelineEventEdits,
): LocalTimelineEvent {
	val start = readDateField(edits.start)
	val end = readDateField(edits.end)
	if (start is DateField.Unreadable || end is DateField.Unreadable) {
		return LocalTimelineEvent.UnreadableDates(
			start = start is DateField.Unreadable,
			end = end is DateField.Unreadable,
		)
	}
	return LocalTimelineEvent.Resolved(
		client.copy(
			title = edits.title.ifBlank { null },
			label = edits.label.ifBlank { null },
			content = if (client.kind == TimelineKind.EVENT) edits.content else client.content,
			lane = edits.lane.trim().ifEmpty { null },
			milestone = edits.milestone,
			start = (start as DateField.Read).date,
			end = (end as DateField.Read).date,
		)
	)
}

private sealed interface DateField {
	data class Read(val date: StoryDate?) : DateField
	data object Unreadable : DateField
}

private fun readDateField(text: String): DateField =
	if (text.isBlank()) {
		DateField.Read(null)
	} else {
		parseStoryDateText(text)?.let { DateField.Read(it) } ?: DateField.Unreadable
	}

@Composable
internal fun TimelineEventConflict(
	entityConflict: ProjectSynchronization.EntityConflict.TimelineEventConflict,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val client = entityConflict.clientEntity
	val initial = remember(client) { client.toEdits() }
	var title by rememberSaveable(client) { mutableStateOf(initial.title) }
	var label by rememberSaveable(client) { mutableStateOf(initial.label) }
	var content by rememberSaveable(client) { mutableStateOf(initial.content) }
	var lane by rememberSaveable(client) { mutableStateOf(initial.lane) }
	var milestone by rememberSaveable(client) { mutableStateOf(initial.milestone) }
	var start by rememberSaveable(client) { mutableStateOf(initial.start) }
	var end by rememberSaveable(client) { mutableStateOf(initial.end) }
	var startUnreadable by rememberSaveable(client) { mutableStateOf(false) }
	var endUnreadable by rememberSaveable(client) { mutableStateOf(false) }
	var tagError by rememberSaveable(client) { mutableStateOf<String?>(null) }
	var ruleProblem by remember(client) { mutableStateOf<TimelineRuleProblem?>(null) }
	var dateProblem by remember(client) { mutableStateOf<TimelineDateProblem?>(null) }

	val contentDiff = rememberContentDiff(entityConflict.serverEntity.content, content)

	val useLocal: () -> Unit = {
		val edits = TimelineEventEdits(title, label, content, lane, milestone, start, end)
		when (val local = buildLocalTimelineEvent(client, edits)) {
			is LocalTimelineEvent.UnreadableDates -> {
				startUnreadable = local.start
				endUnreadable = local.end
			}

			is LocalTimelineEvent.Resolved -> {
				startUnreadable = false
				endUnreadable = false
				val error = component.resolveConflict(local.entity)
				if (error is ProjectSynchronization.EntityMergeError.TimelineEventMergeError) {
					ruleProblem = error.ruleProblem
					dateProblem = error.dateProblem
					scope.launch { tagError = error.tagError?.text(strRes) }
				}
			}
		}
	}
	val useRemote = { component.resolveConflict(entityConflict.serverEntity); Unit }

	EntityConflict(
		entityConflict = entityConflict,
		component = component,
		screenCharacteristics = screenCharacteristics,
		onUseLocal = useLocal,
		onUseRemote = useRemote,
		LocalBody = { m, c, _ ->
			val server = c.serverEntity
			val invalidDate = Res.string.sync_conflict_timeline_event_invalid_date.get()
			val dateHint = Res.string.sync_conflict_timeline_event_date_hint.get()
			TimelineEventPane(m) {
				TimelineEventHeader(client)
				ruleProblem?.let { HdMonoLabel(text = it.text(), color = MaterialTheme.colorScheme.error) }
				dateProblem?.let { HdMonoLabel(text = it.text(), color = MaterialTheme.colorScheme.error) }
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_title.get(),
					conflict = server.title != client.title,
				) {
					HdHairlineField(label = "", value = title, onValueChange = { title = it })
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_label.get(),
					conflict = server.label != client.label,
				) {
					HdHairlineField(label = "", value = label, onValueChange = { label = it })
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_start.get(),
					conflict = server.start != client.start,
				) {
					StoryDateTextField(
						value = start,
						onValueChange = { start = it },
						hint = dateHint,
						error = invalidDate.takeIf { startUnreadable },
						testTag = TIMELINE_CONFLICT_START_TAG,
					)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_end.get(),
					conflict = server.end != client.end,
				) {
					StoryDateTextField(
						value = end,
						onValueChange = { end = it },
						hint = dateHint,
						error = invalidDate.takeIf { endUnreadable },
						testTag = TIMELINE_CONFLICT_END_TAG,
					)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_lane.get(),
					conflict = server.lane != client.lane,
				) {
					HdHairlineField(label = "", value = lane, onValueChange = { lane = it })
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_milestone.get(),
					conflict = server.milestone != client.milestone,
				) {
					HdHairlineToggleRow(
						checked = milestone,
						onCheckedChange = { milestone = it },
						label = Res.string.sync_conflict_timeline_event_milestone_toggle.get(),
					)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_content.get(),
					conflict = server.content != client.content,
				) {
					if (client.kind == TimelineKind.EVENT) {
						HdHairlineField(
							label = "",
							value = content,
							onValueChange = { content = it },
							singleLine = false,
							minLines = 6,
							visualTransformation = rememberLocalDiffHighlight(contentDiff),
							modifier = Modifier.fillMaxWidth(),
						)
					} else {
						PlacementHasNoText()
					}
				}
				CountFields(server = server, client = client, shown = client, tagError = tagError)
			}
		},
		RemoteBody = { m, c, _ ->
			val server = c.serverEntity
			val noDate = Res.string.sync_conflict_timeline_event_missing_date.get()
			val none = Res.string.sync_conflict_timeline_event_empty.get()
			TimelineEventPane(m) {
				TimelineEventHeader(server)
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_title.get(),
					conflict = server.title != client.title,
				) {
					ReadOnlyLine(server.title?.takeIf { it.isNotBlank() } ?: none)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_label.get(),
					conflict = server.label != client.label,
				) {
					ReadOnlyLine(server.label?.takeIf { it.isNotBlank() } ?: noDate)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_start.get(),
					conflict = server.start != client.start,
				) {
					ReadOnlyLine(server.start?.toCompactText() ?: noDate)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_end.get(),
					conflict = server.end != client.end,
				) {
					ReadOnlyLine(server.end?.toCompactText() ?: noDate)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_lane.get(),
					conflict = server.lane != client.lane,
				) {
					ReadOnlyLine(server.lane ?: none)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_milestone.get(),
					conflict = server.milestone != client.milestone,
				) {
					ReadOnlyLine(
						if (server.milestone) {
							Res.string.sync_conflict_timeline_event_milestone_on.get()
						} else {
							Res.string.sync_conflict_timeline_event_milestone_off.get()
						}
					)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_content.get(),
					conflict = server.content != client.content,
				) {
					if (server.kind == TimelineKind.EVENT) {
						ReadOnlyBlock(rememberRemoteDiffHighlight(server.content, contentDiff))
					} else {
						PlacementHasNoText()
					}
				}
				CountFields(server = server, client = client, shown = server, tagError = null)
			}
		},
	)
}

@Composable
private fun TimelineEventPane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
		content = content,
	)
}

/** Kind glyph, subject id and the subject's cached title; these come with whichever side is used. */
@Composable
private fun TimelineEventHeader(entity: ApiProjectEntity.TimelineEventEntity) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Text(
			text = entity.kind.hdMark(),
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		entity.subject?.let { subject ->
			HdEntityId(prefix = entity.kind.subjectPrefix(), id = subject)
		}
		entity.cachedTitle?.takeIf { it.isNotBlank() }?.let { cachedTitle ->
			Text(
				text = cachedTitle,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

private fun TimelineKind.subjectPrefix(): String = when (this) {
	TimelineKind.EVENT -> "EVT"
	TimelineKind.SCENE -> "SCN"
	TimelineKind.ENTRY -> "ENT"
}

@Composable
private fun StoryDateTextField(
	value: String,
	onValueChange: (String) -> Unit,
	hint: String,
	error: String?,
	testTag: String,
) {
	HdHairlineField(
		label = "",
		value = value,
		onValueChange = onValueChange,
		placeholder = hint,
		error = error,
		capitalization = KeyboardCapitalization.None,
		autoCorrectEnabled = false,
		testTag = testTag,
	)
}

@Composable
private fun PlacementHasNoText() {
	Text(
		text = Res.string.sync_conflict_timeline_event_placement_no_text.get(),
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

/** Tags and references are not merged here; [shown]'s sets ride along with its side. */
@Composable
private fun CountFields(
	server: ApiProjectEntity.TimelineEventEntity,
	client: ApiProjectEntity.TimelineEventEntity,
	shown: ApiProjectEntity.TimelineEventEntity,
	tagError: String?,
) {
	HdConflictField(
		label = Res.string.sync_conflict_title_timeline_event_field_tags.get(),
		conflict = server.tags != client.tags,
	) {
		ReadOnlyLine(shown.tags.size.toString())
		tagError?.let { HdMonoLabel(text = it, color = MaterialTheme.colorScheme.error) }
	}
	HdConflictField(
		label = Res.string.sync_conflict_title_timeline_event_field_references.get(),
		conflict = server.references != client.references,
	) {
		ReadOnlyLine(shown.references.size.toString())
	}
}

@Composable
internal fun TimelineRuleProblem.text(): String = when (this) {
	TimelineRuleProblem.SubjectIsReference -> Res.string.sync_conflict_timeline_rule_subject_is_reference.get()
	TimelineRuleProblem.SelfReference -> Res.string.sync_conflict_timeline_rule_self_reference.get()
	TimelineRuleProblem.EventHasSubject -> Res.string.sync_conflict_timeline_rule_event_has_subject.get()
	is TimelineRuleProblem.PlacementNeedsSubject -> Res.string.sync_conflict_timeline_rule_placement_needs_subject.get()
	TimelineRuleProblem.PlacementIsOwnSubject -> Res.string.sync_conflict_timeline_rule_placement_own_subject.get()
	TimelineRuleProblem.PlacementHasReferences -> Res.string.sync_conflict_timeline_rule_placement_has_references.get()
	TimelineRuleProblem.PlacementHasContent -> Res.string.sync_conflict_timeline_rule_placement_has_content.get()
	TimelineRuleProblem.PlacementHasTags -> Res.string.sync_conflict_timeline_rule_placement_has_tags.get()
	TimelineRuleProblem.LaneBlank -> Res.string.sync_conflict_timeline_rule_lane_blank.get()
	TimelineRuleProblem.LaneTooLong -> Res.string.sync_conflict_timeline_rule_lane_too_long.get(TimelineLimits.MAX_LANE)
	TimelineRuleProblem.TitleTooLong -> Res.string.sync_conflict_timeline_rule_title_too_long.get(TimelineLimits.MAX_TITLE)
	TimelineRuleProblem.CachedTitleTooLong ->
		Res.string.sync_conflict_timeline_rule_cached_title_too_long.get(TimelineLimits.MAX_TITLE)

	TimelineRuleProblem.LabelTooLong -> Res.string.sync_conflict_timeline_rule_label_too_long.get(TimelineLimits.MAX_LABEL)
	TimelineRuleProblem.TagTooLong -> Res.string.sync_conflict_timeline_rule_tag_too_long.get(TimelineLimits.MAX_TAG)
	is TimelineRuleProblem.TooManyRungs ->
		Res.string.sync_conflict_timeline_rule_too_many_rungs.get(field.label(), TimelineLimits.MAX_RUNGS)

	is TimelineRuleProblem.BlankUnitName -> Res.string.sync_conflict_timeline_rule_blank_unit.get(field.label())
	is TimelineRuleProblem.UnitNameTooLong ->
		Res.string.sync_conflict_timeline_rule_unit_too_long.get(field.label(), TimelineLimits.MAX_UNIT_NAME)
}

@Composable
internal fun TimelineDateProblem.text(): String = when (this) {
	TimelineDateProblem.EndBeforeStart ->
		Res.string.sync_conflict_timeline_date_end_before_start.get()

	is TimelineDateProblem.Invalid -> when (val p = problem) {
		is StoryDateProblem.UnknownUnit ->
			Res.string.sync_conflict_timeline_date_unknown_unit.get(field.label(), p.unit)

		StoryDateProblem.NotAPrefix -> Res.string.sync_conflict_timeline_date_not_a_prefix.get(field.label())
		is StoryDateProblem.OutOfRange ->
			Res.string.sync_conflict_timeline_date_out_of_range.get(field.label(), p.unit, p.value.toString())

		is StoryDateProblem.UnknownCoarsestUnit,
		StoryDateProblem.CoarsestUnitNotSupported -> Res.string.sync_conflict_timeline_date_bad_calendar.get()
	}
}

@Composable
private fun TimelineDateField.label(): String = when (this) {
	TimelineDateField.START -> Res.string.sync_conflict_title_timeline_event_field_start.get()
	TimelineDateField.END -> Res.string.sync_conflict_title_timeline_event_field_end.get()
}
