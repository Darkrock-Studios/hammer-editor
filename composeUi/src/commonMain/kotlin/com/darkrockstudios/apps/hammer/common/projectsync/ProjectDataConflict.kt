package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get

@Composable
internal fun ProjectDataConflict(
	conflictState: ProjectSynchronization.ProjectDataConflictState,
	component: ProjectSynchronization,
) {
	var authorChoice by remember { mutableStateOf(Side.LOCAL) }
	var themeChoice by remember { mutableStateOf(Side.LOCAL) }
	var goalChoice by remember { mutableStateOf(Side.LOCAL) }

	Column(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L)) {
		Text(
			Res.string.sync_conflict_project_data_explanation.get(),
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(bottom = Ui.Padding.L)
		)

		ConflictRow(
			label = Res.string.sync_conflict_project_data_field_author.get(),
			localValue = displayString(conflictState.local.authorName),
			serverValue = displayString(conflictState.server.authorName),
			selected = authorChoice,
			onSelect = { authorChoice = it },
		)
		Spacer(Modifier.size(Ui.Padding.M))

		ConflictRow(
			label = Res.string.sync_conflict_project_data_field_theme.get(),
			localValue = displayString(conflictState.local.theme),
			serverValue = displayString(conflictState.server.theme),
			selected = themeChoice,
			onSelect = { themeChoice = it },
		)
		Spacer(Modifier.size(Ui.Padding.M))

		ConflictRow(
			label = Res.string.sync_conflict_project_data_field_word_goal.get(),
			localValue = displayWordGoal(conflictState.local.wordCountGoal),
			serverValue = displayWordGoal(conflictState.server.wordCountGoal),
			selected = goalChoice,
			onSelect = { goalChoice = it },
		)
		Spacer(Modifier.size(Ui.Padding.L))

		Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
			Button(
				onClick = {
					component.resolveProjectDataConflict(
						buildResolved(
							local = conflictState.local,
							server = conflictState.server,
							authorChoice = authorChoice,
							themeChoice = themeChoice,
							goalChoice = goalChoice,
						)
					)
				},
			) {
				Text(Res.string.sync_conflict_project_data_resolve_button.get())
			}
		}
	}
}

@Composable
private fun ConflictRow(
	label: String,
	localValue: String,
	serverValue: String,
	selected: Side,
	onSelect: (Side) -> Unit,
) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			label,
			style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
		)
		Spacer(Modifier.size(Ui.Padding.S))
		Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			SideOption(
				modifier = Modifier.weight(1f),
				heading = Res.string.sync_conflict_tab_local.get(),
				value = localValue,
				selected = selected == Side.LOCAL,
				onClick = { onSelect(Side.LOCAL) },
			)
			Spacer(Modifier.size(Ui.Padding.M))
			SideOption(
				modifier = Modifier.weight(1f),
				heading = Res.string.sync_conflict_tab_remote.get(),
				value = serverValue,
				selected = selected == Side.SERVER,
				onClick = { onSelect(Side.SERVER) },
			)
		}
	}
}

@Composable
private fun SideOption(
	modifier: Modifier,
	heading: String,
	value: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	Row(
		modifier = modifier
			.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
			.padding(Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
	) {
		RadioButton(selected = selected, onClick = onClick)
		Spacer(Modifier.size(Ui.Padding.S))
		Column(modifier = Modifier.wrapContentSize()) {
			Text(heading, style = MaterialTheme.typography.labelSmall)
			Text(value, style = MaterialTheme.typography.bodyMedium)
		}
	}
}

private enum class Side { LOCAL, SERVER }

private fun buildResolved(
	local: ProjectData,
	server: ProjectData,
	authorChoice: Side,
	themeChoice: Side,
	goalChoice: Side,
): ProjectData = ProjectData(
	authorName = if (authorChoice == Side.LOCAL) local.authorName else server.authorName,
	theme = if (themeChoice == Side.LOCAL) local.theme else server.theme,
	wordCountGoal = if (goalChoice == Side.LOCAL) local.wordCountGoal else server.wordCountGoal,
)

@Composable
private fun displayString(value: String?): String =
	if (value.isNullOrBlank()) Res.string.sync_conflict_project_data_value_unset.get() else value

@Composable
private fun displayString(theme: ProjectTheme?): String =
	if (theme == null) Res.string.sync_conflict_project_data_value_unset.get()
	else "${theme.color1} • ${theme.color2}"

@Composable
private fun displayWordGoal(goal: WordCountGoal?): String {
	if (goal == null) return Res.string.sync_conflict_project_data_value_unset.get()
	val cadence = when (goal.cadence) {
		WordCountGoal.Cadence.DAY -> Res.string.sync_conflict_project_data_cadence_day.get()
		WordCountGoal.Cadence.WEEK -> Res.string.sync_conflict_project_data_cadence_week.get()
	}
	return "${goal.count} / $cadence"
}
