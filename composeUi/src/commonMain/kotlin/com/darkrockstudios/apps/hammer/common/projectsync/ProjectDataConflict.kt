package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.darkrockstudios.apps.hammer.common.compose.WindowSizeClass
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.common.data.projectdata.mergeDeletedTimelines
import com.darkrockstudios.apps.hammer.common.data.projectdata.mergeDictionaryWords
import com.darkrockstudios.apps.hammer.common.data.projectdata.mergeTimelinesByChoice
import com.darkrockstudios.apps.hammer.common.data.projectdata.withoutDeleted
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_cadence_day
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_cadence_week
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_dictionary_merged
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_explanation
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_author
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_dictionary
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_language
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_tags
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_theme
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_timelines
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_word_goal
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_resolve_button
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_timeline_calendar_gregorian
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_timeline_calendar_story
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_timeline_coarsest_unit
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_timeline_dated
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_timeline_lanes
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_timeline_manual
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_timelines_deleted
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_value_off
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_value_on
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_value_unset
import com.darkrockstudios.apps.hammer.sync_conflict_tab_local
import com.darkrockstudios.apps.hammer.sync_conflict_tab_remote
import com.darkrockstudios.apps.hammer.sync_conflict_title_project_data_field_cover
import org.jetbrains.compose.resources.pluralStringResource

internal enum class DataChoice { LOCAL, REMOTE }

@Composable
internal fun ProjectDataConflict(
	conflictState: ProjectSynchronization.ProjectDataConflictState,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass,
) {
	var authorChoice by remember { mutableStateOf(DataChoice.LOCAL) }
	var themeChoice by remember { mutableStateOf(DataChoice.LOCAL) }
	var goalChoice by remember { mutableStateOf(DataChoice.LOCAL) }
	var tagsChoice by remember { mutableStateOf(DataChoice.LOCAL) }
	var languageChoice by remember { mutableStateOf(DataChoice.LOCAL) }
	var dictionaryChoice by remember { mutableStateOf(DataChoice.LOCAL) }
	var coverChoice by remember { mutableStateOf(DataChoice.LOCAL) }
	var timelinesChoice by remember { mutableStateOf(DataChoice.LOCAL) }

	val authorConflict = conflictState.local.authorName != conflictState.server.authorName
	val themeConflict = conflictState.local.theme != conflictState.server.theme
	val goalConflict = conflictState.local.wordCountGoal != conflictState.server.wordCountGoal
	val tagsConflict = conflictState.local.tags != conflictState.server.tags
	val languageConflict = conflictState.local.language != conflictState.server.language
	val dictionaryConflict =
		conflictState.local.encyclopediaDictionary != conflictState.server.encyclopediaDictionary
	val coverConflict = conflictState.local.coverImageId != conflictState.server.coverImageId
	// Deletions apply whichever side is picked, so both sides show only the defs that survive.
	val deletedTimelines = mergeDeletedTimelines(conflictState.local, conflictState.server)
	val localTimelines = conflictState.local.timelines.withoutDeleted(deletedTimelines)
	val serverTimelines = conflictState.server.timelines.withoutDeleted(deletedTimelines)
	val localDeletedCount = timelinesDeletedOnlyBy(conflictState.local, conflictState.server)
	val serverDeletedCount = timelinesDeletedOnlyBy(conflictState.server, conflictState.local)
	val timelinesConflict = localTimelines != serverTimelines

	val unsetLabel = Res.string.sync_conflict_project_data_value_unset.get()
	val onLabel = Res.string.sync_conflict_project_data_value_on.get()
	val offLabel = Res.string.sync_conflict_project_data_value_off.get()
	val localLabel = Res.string.sync_conflict_tab_local.get()
	val remoteLabel = Res.string.sync_conflict_tab_remote.get()
	val dayLabel = Res.string.sync_conflict_project_data_cadence_day.get()
	val weekLabel = Res.string.sync_conflict_project_data_cadence_week.get()
	val stackVertical = screenCharacteristics.widthSizeClass == WindowWidthSizeClass.Compact
	val timelineLabels = timelineSummaryLabels(
		timelines = localTimelines + serverTimelines,
		deletedCounts = listOf(localDeletedCount, serverDeletedCount),
		unsetLabel = unsetLabel,
	)

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
	) {
		Text(
			text = Res.string.sync_conflict_project_data_explanation.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		PerFieldChoice(
			label = Res.string.sync_conflict_project_data_field_author.get(),
			conflict = authorConflict,
			localValue = displayString(conflictState.local.authorName, unsetLabel),
			serverValue = displayString(conflictState.server.authorName, unsetLabel),
			selected = authorChoice,
			onSelect = { authorChoice = it },
			localLabel = localLabel,
			remoteLabel = remoteLabel,
			stackVertical = stackVertical,
		)

		PerFieldChoice(
			label = Res.string.sync_conflict_project_data_field_theme.get(),
			conflict = themeConflict,
			localValue = displayTheme(conflictState.local.theme, unsetLabel),
			serverValue = displayTheme(conflictState.server.theme, unsetLabel),
			selected = themeChoice,
			onSelect = { themeChoice = it },
			localLabel = localLabel,
			remoteLabel = remoteLabel,
			stackVertical = stackVertical,
		)

		PerFieldChoice(
			label = Res.string.sync_conflict_project_data_field_word_goal.get(),
			conflict = goalConflict,
			localValue = displayWordGoal(conflictState.local.wordCountGoal, unsetLabel, dayLabel, weekLabel),
			serverValue = displayWordGoal(conflictState.server.wordCountGoal, unsetLabel, dayLabel, weekLabel),
			selected = goalChoice,
			onSelect = { goalChoice = it },
			localLabel = localLabel,
			remoteLabel = remoteLabel,
			stackVertical = stackVertical,
		)

		PerFieldChoice(
			label = Res.string.sync_conflict_project_data_field_tags.get(),
			conflict = tagsConflict,
			localValue = displayTags(conflictState.local.tags, unsetLabel),
			serverValue = displayTags(conflictState.server.tags, unsetLabel),
			selected = tagsChoice,
			onSelect = { tagsChoice = it },
			localLabel = localLabel,
			remoteLabel = remoteLabel,
			stackVertical = stackVertical,
		)

		PerFieldChoice(
			label = Res.string.sync_conflict_project_data_field_language.get(),
			conflict = languageConflict,
			localValue = displayLanguage(conflictState.local.language, unsetLabel),
			serverValue = displayLanguage(conflictState.server.language, unsetLabel),
			selected = languageChoice,
			onSelect = { languageChoice = it },
			localLabel = localLabel,
			remoteLabel = remoteLabel,
			stackVertical = stackVertical,
		)

		PerFieldChoice(
			label = Res.string.sync_conflict_project_data_field_dictionary.get(),
			conflict = dictionaryConflict,
			localValue = if (conflictState.local.encyclopediaDictionary) onLabel else offLabel,
			serverValue = if (conflictState.server.encyclopediaDictionary) onLabel else offLabel,
			selected = dictionaryChoice,
			onSelect = { dictionaryChoice = it },
			localLabel = localLabel,
			remoteLabel = remoteLabel,
			stackVertical = stackVertical,
		)

		PerFieldChoice(
			label = Res.string.sync_conflict_title_project_data_field_cover.get(),
			conflict = coverConflict,
			selected = coverChoice,
			onSelect = { coverChoice = it },
			localLabel = localLabel,
			remoteLabel = remoteLabel,
			stackVertical = stackVertical,
			localContent = { CoverValue(conflictState.local.coverImageId, conflictState.localImages, unsetLabel) },
			serverContent = { CoverValue(conflictState.server.coverImageId, conflictState.localImages, unsetLabel) },
		)

		PerFieldChoice(
			label = Res.string.sync_conflict_project_data_field_timelines.get(),
			conflict = timelinesConflict,
			localValue = timelinesSummary(localTimelines, localDeletedCount, timelineLabels),
			serverValue = timelinesSummary(serverTimelines, serverDeletedCount, timelineLabels),
			selected = timelinesChoice,
			onSelect = { timelinesChoice = it },
			localLabel = localLabel,
			remoteLabel = remoteLabel,
			stackVertical = stackVertical,
		)

		if (conflictState.local.dictionaryWords != conflictState.server.dictionaryWords) {
			Text(
				text = Res.string.sync_conflict_project_data_dictionary_merged.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.End,
		) {
			HdHairlineButton(
				label = Res.string.sync_conflict_project_data_resolve_button.get().uppercase(),
				onClick = {
					component.resolveProjectDataConflict(
						buildResolved(
							local = conflictState.local,
							server = conflictState.server,
							authorChoice = authorChoice,
							themeChoice = themeChoice,
							goalChoice = goalChoice,
							tagsChoice = tagsChoice,
							languageChoice = languageChoice,
							dictionaryChoice = dictionaryChoice,
							coverChoice = coverChoice,
							timelinesChoice = timelinesChoice,
						)
					)
				},
				emphasised = true,
			)
		}
	}
}

@Composable
private fun PerFieldChoice(
	label: String,
	conflict: Boolean,
	localValue: String,
	serverValue: String,
	selected: DataChoice,
	onSelect: (DataChoice) -> Unit,
	localLabel: String,
	remoteLabel: String,
	stackVertical: Boolean,
) {
	PerFieldChoice(
		label = label,
		conflict = conflict,
		selected = selected,
		onSelect = onSelect,
		localLabel = localLabel,
		remoteLabel = remoteLabel,
		stackVertical = stackVertical,
		localContent = { ValueText(localValue) },
		serverContent = { ValueText(serverValue) },
	)
}

@Composable
private fun PerFieldChoice(
	label: String,
	conflict: Boolean,
	selected: DataChoice,
	onSelect: (DataChoice) -> Unit,
	localLabel: String,
	remoteLabel: String,
	stackVertical: Boolean,
	localContent: @Composable () -> Unit,
	serverContent: @Composable () -> Unit,
) {
	HdConflictField(label = label, conflict = conflict) {
		if (stackVertical) {
			Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				DataChoiceCell(
					heading = localLabel,
					selected = selected == DataChoice.LOCAL,
					onClick = { onSelect(DataChoice.LOCAL) },
					modifier = Modifier.fillMaxWidth(),
					content = localContent,
				)
				DataChoiceCell(
					heading = remoteLabel,
					selected = selected == DataChoice.REMOTE,
					onClick = { onSelect(DataChoice.REMOTE) },
					modifier = Modifier.fillMaxWidth(),
					content = serverContent,
				)
			}
		} else {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				DataChoiceCell(
					heading = localLabel,
					selected = selected == DataChoice.LOCAL,
					onClick = { onSelect(DataChoice.LOCAL) },
					modifier = Modifier.weight(1f),
					content = localContent,
				)
				DataChoiceCell(
					heading = remoteLabel,
					selected = selected == DataChoice.REMOTE,
					onClick = { onSelect(DataChoice.REMOTE) },
					modifier = Modifier.weight(1f),
					content = serverContent,
				)
			}
		}
	}
}

@Composable
private fun DataChoiceCell(
	heading: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	val borderColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.outlineVariant
	val backgroundColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
	else MaterialTheme.colorScheme.surface
	Column(
		modifier = modifier
			.background(backgroundColor, RectangleShape)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
			.padding(Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		HdMonoLabel(
			text = heading,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		content()
	}
}

@Composable
private fun ValueText(value: String) {
	Text(
		text = value,
		style = MaterialTheme.typography.bodyLarge,
		color = MaterialTheme.colorScheme.onSurface,
	)
}

/** A cover this device does not have yet shows as a tile with its id. */
@Composable
private fun CoverValue(coverImageId: Int?, localImages: Map<Int, ImageFile>, unsetLabel: String) {
	if (coverImageId == null) {
		ValueText(unsetLabel)
	} else {
		ImageThumb(
			id = coverImageId,
			file = localImages[coverImageId],
			modifier = Modifier
				.size(width = CoverThumbWidth, height = CoverThumbHeight)
				.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
		)
	}
}

private val CoverThumbWidth = 56.dp
private val CoverThumbHeight = 84.dp

internal fun buildResolved(
	local: ProjectData,
	server: ProjectData,
	authorChoice: DataChoice,
	themeChoice: DataChoice,
	goalChoice: DataChoice,
	tagsChoice: DataChoice,
	languageChoice: DataChoice,
	dictionaryChoice: DataChoice,
	coverChoice: DataChoice,
	timelinesChoice: DataChoice,
): ProjectData {
	val deletedTimelines = mergeDeletedTimelines(local, server)
	return ProjectData(
		authorName = if (authorChoice == DataChoice.LOCAL) local.authorName else server.authorName,
		theme = if (themeChoice == DataChoice.LOCAL) local.theme else server.theme,
		wordCountGoal = if (goalChoice == DataChoice.LOCAL) local.wordCountGoal else server.wordCountGoal,
		tags = if (tagsChoice == DataChoice.LOCAL) local.tags else server.tags,
		language = if (languageChoice == DataChoice.LOCAL) local.language else server.language,
		encyclopediaDictionary = if (dictionaryChoice == DataChoice.LOCAL) local.encyclopediaDictionary else server.encyclopediaDictionary,
		dictionaryWords = mergeDictionaryWords(local, server),
		coverImageId = if (coverChoice == DataChoice.LOCAL) local.coverImageId else server.coverImageId,
		timelines = mergeTimelinesByChoice(
			local = local.timelines,
			server = server.timelines,
			deleted = deletedTimelines,
			preferLocal = timelinesChoice == DataChoice.LOCAL,
		),
		deletedTimelines = deletedTimelines,
	)
}

/** How many timelines [side] deleted that [other] still has no tombstone for. */
internal fun timelinesDeletedOnlyBy(side: ProjectData, other: ProjectData): Int =
	(mergeDeletedTimelines(side) - other.deletedTimelines).size

private fun displayString(value: String?, unsetLabel: String): String =
	if (value.isNullOrBlank()) unsetLabel else value

private fun displayTheme(theme: ProjectTheme?, unsetLabel: String): String =
	if (theme == null) unsetLabel else "${theme.primary} • ${theme.secondary}"

private fun displayTags(tags: Set<String>, unsetLabel: String): String =
	if (tags.isEmpty()) unsetLabel else tags.sorted().joinToString("  ") { "#$it" }

private fun displayLanguage(tag: String?, unsetLabel: String): String =
	if (tag.isNullOrBlank()) unsetLabel else Locale.forLanguageTag(tag).displayName()

private fun displayWordGoal(goal: WordCountGoal?, unsetLabel: String, dayLabel: String, weekLabel: String): String {
	if (goal == null) return unsetLabel
	val cadence = when (goal.cadence) {
		WordCountGoal.Cadence.DAY -> dayLabel
		WordCountGoal.Cadence.WEEK -> weekLabel
	}
	return "${goal.count} / $cadence"
}

internal class TimelineSummaryLabels(
	val unset: String,
	val manual: String,
	val dated: String,
	val storyCalendar: String,
	val gregorianCalendar: String,
	val coarsestUnit: (String) -> String,
	val lanes: (Int) -> String,
	val deleted: (Int) -> String,
)

/** Resolves the lane-count and unit phrases [timelines] need up front, keeping [timelinesSummary] pure. */
@Composable
private fun timelineSummaryLabels(
	timelines: List<TimelineDef>,
	deletedCounts: List<Int>,
	unsetLabel: String,
): TimelineSummaryLabels {
	val laneCounts = timelines.map { it.laneOrder.size }.filter { it > 0 }.distinct()
	val units = timelines.mapNotNull { it.calendar?.coarsestUnit }.distinct()
	val laneLabels = laneCounts.associateWith {
		pluralStringResource(Res.plurals.sync_conflict_project_data_timeline_lanes, it, it)
	}
	val deletedLabels = deletedCounts.filter { it > 0 }.distinct().associateWith {
		pluralStringResource(Res.plurals.sync_conflict_project_data_timelines_deleted, it, it)
	}
	val unitLabels = units.associateWith { Res.string.sync_conflict_project_data_timeline_coarsest_unit.get(it) }
	return TimelineSummaryLabels(
		unset = unsetLabel,
		manual = Res.string.sync_conflict_project_data_timeline_manual.get(),
		dated = Res.string.sync_conflict_project_data_timeline_dated.get(),
		storyCalendar = Res.string.sync_conflict_project_data_timeline_calendar_story.get(),
		gregorianCalendar = Res.string.sync_conflict_project_data_timeline_calendar_gregorian.get(),
		coarsestUnit = { unitLabels[it] ?: it },
		lanes = { laneLabels[it] ?: it.toString() },
		deleted = { deletedLabels[it] ?: it.toString() },
	)
}

/**
 * One line per timeline, e.g. `Main · dated · Story calendar (Landing) · 2 lanes`, then a line
 * counting the timelines this side deleted when [deletedCount] is above zero.
 */
internal fun timelinesSummary(
	timelines: List<TimelineDef>,
	deletedCount: Int,
	labels: TimelineSummaryLabels,
): String {
	val lines = if (timelines.isEmpty()) listOf(labels.unset) else timelines.map { timelineSummary(it, labels) }
	val deletedLine = if (deletedCount > 0) listOf(labels.deleted(deletedCount)) else emptyList()
	return (lines + deletedLine).joinToString("\n")
}

private fun timelineSummary(def: TimelineDef, labels: TimelineSummaryLabels): String {
	val parts = mutableListOf(def.name.ifBlank { def.id })
	parts += when (def.mode) {
		TimelineMode.MANUAL -> labels.manual
		TimelineMode.DATED -> labels.dated
	}
	def.calendar?.let { calendar ->
		val preset = when (calendar.preset) {
			CalendarPreset.STORY -> labels.storyCalendar
			CalendarPreset.GREGORIAN -> labels.gregorianCalendar
		}
		val epoch = calendar.epochName?.takeIf { it.isNotBlank() }
		parts += if (epoch != null) "$preset ($epoch)" else preset
		calendar.coarsestUnit?.let { parts += labels.coarsestUnit(it) }
	}
	if (def.laneOrder.isNotEmpty()) parts += labels.lanes(def.laneOrder.size)
	return parts.joinToString(" · ")
}
