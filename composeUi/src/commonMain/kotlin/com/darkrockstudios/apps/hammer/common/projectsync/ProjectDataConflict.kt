package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_cadence_day
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_cadence_week
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_explanation
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_author
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_tags
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_theme
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_field_word_goal
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_resolve_button
import com.darkrockstudios.apps.hammer.sync_conflict_project_data_value_unset
import com.darkrockstudios.apps.hammer.sync_conflict_tab_local
import com.darkrockstudios.apps.hammer.sync_conflict_tab_remote

private enum class DataChoice { LOCAL, REMOTE }

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

	val authorConflict = conflictState.local.authorName != conflictState.server.authorName
	val themeConflict = conflictState.local.theme != conflictState.server.theme
	val goalConflict = conflictState.local.wordCountGoal != conflictState.server.wordCountGoal
	val tagsConflict = conflictState.local.tags != conflictState.server.tags

	val unsetLabel = Res.string.sync_conflict_project_data_value_unset.get()
	val localLabel = Res.string.sync_conflict_tab_local.get()
	val remoteLabel = Res.string.sync_conflict_tab_remote.get()
	val dayLabel = Res.string.sync_conflict_project_data_cadence_day.get()
	val weekLabel = Res.string.sync_conflict_project_data_cadence_week.get()
	val stackVertical = screenCharacteristics.widthSizeClass == WindowWidthSizeClass.Compact

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
	HdConflictField(label = label, conflict = conflict) {
		if (stackVertical) {
			Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				DataChoiceCell(
					heading = localLabel,
					value = localValue,
					selected = selected == DataChoice.LOCAL,
					onClick = { onSelect(DataChoice.LOCAL) },
					modifier = Modifier.fillMaxWidth(),
				)
				DataChoiceCell(
					heading = remoteLabel,
					value = serverValue,
					selected = selected == DataChoice.REMOTE,
					onClick = { onSelect(DataChoice.REMOTE) },
					modifier = Modifier.fillMaxWidth(),
				)
			}
		} else {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				DataChoiceCell(
					heading = localLabel,
					value = localValue,
					selected = selected == DataChoice.LOCAL,
					onClick = { onSelect(DataChoice.LOCAL) },
					modifier = Modifier.weight(1f),
				)
				DataChoiceCell(
					heading = remoteLabel,
					value = serverValue,
					selected = selected == DataChoice.REMOTE,
					onClick = { onSelect(DataChoice.REMOTE) },
					modifier = Modifier.weight(1f),
				)
			}
		}
	}
}

@Composable
private fun DataChoiceCell(
	heading: String,
	value: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
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
		Text(
			text = value,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

private fun buildResolved(
	local: ProjectData,
	server: ProjectData,
	authorChoice: DataChoice,
	themeChoice: DataChoice,
	goalChoice: DataChoice,
	tagsChoice: DataChoice,
): ProjectData = ProjectData(
	authorName = if (authorChoice == DataChoice.LOCAL) local.authorName else server.authorName,
	theme = if (themeChoice == DataChoice.LOCAL) local.theme else server.theme,
	wordCountGoal = if (goalChoice == DataChoice.LOCAL) local.wordCountGoal else server.wordCountGoal,
	tags = if (tagsChoice == DataChoice.LOCAL) local.tags else server.tags,
)

private fun displayString(value: String?, unsetLabel: String): String =
	if (value.isNullOrBlank()) unsetLabel else value

private fun displayTheme(theme: ProjectTheme?, unsetLabel: String): String =
	if (theme == null) unsetLabel else "${theme.primary} • ${theme.secondary}"

private fun displayTags(tags: Set<String>, unsetLabel: String): String =
	if (tags.isEmpty()) unsetLabel else tags.sorted().joinToString("  ") { "#$it" }

private fun displayWordGoal(goal: WordCountGoal?, unsetLabel: String, dayLabel: String, weekLabel: String): String {
	if (goal == null) return unsetLabel
	val cadence = when (goal.cadence) {
		WordCountGoal.Cadence.DAY -> dayLabel
		WordCountGoal.Cadence.WEEK -> weekLabel
	}
	return "${goal.count} / $cadence"
}

