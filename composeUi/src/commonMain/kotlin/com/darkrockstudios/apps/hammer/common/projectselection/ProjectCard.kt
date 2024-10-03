package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.util.format
import dev.icerock.moko.resources.StringResource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

val ProjectCardTestTag = "project-card"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
	projectData: ProjectData,
	onProjectClick: (projectDef: ProjectDef) -> Unit,
	onProjectAltClick: (projectDef: ProjectDef) -> Unit,
	onProjectRenameClick: (projectDef: ProjectDef) -> Unit
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = { onProjectClick(projectData.definition) },
			)
			.semantics { contentDescription = "Project Card for ${projectData.definition.name}" }
			.testTag(ProjectCardTestTag),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.XL)) {
			Row(modifier = Modifier.padding(bottom = Ui.Padding.S).fillMaxWidth()) {
				Text(
					projectData.definition.name,
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.headlineMedium,
					fontWeight = FontWeight.Bold,
				)

				ProjectOptionsMenu(
					items = listOf(
						MR.strings.project_select_card_delete_button,
						MR.strings.project_select_card_rename_button,
					),
				) {
					when (it) {
						MR.strings.project_select_card_delete_button -> {
							onProjectAltClick(projectData.definition)
						}

						MR.strings.project_select_card_rename_button -> {
							onProjectRenameClick(projectData.definition)
						}
					}
				}
			}

			val date = remember(projectData.metadata.info) {
				projectData.metadata.info.created.let { instant: Instant ->
					val created = instant.toLocalDateTime(TimeZone.currentSystemDefault())
					created.format("dd MMM `yy")
				}
			}

			Text(
				date,
				modifier = Modifier.padding(bottom = Ui.Padding.M, start = Ui.Padding.L),
				style = MaterialTheme.typography.bodySmall
			)
			HorizontalDivider(
				modifier = Modifier.fillMaxWidth(),
				thickness = 1.dp,
				color = MaterialTheme.colorScheme.outline
			)
		}
	}
}

@Composable
fun ProjectOptionsMenu(
	items: List<StringResource>,
	onItemClick: (StringResource) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }

	Box {
		IconButton(
			modifier = Modifier.testTag("More"),
			onClick = { expanded = true },
		) {
			Icon(
				Icons.Default.MoreVert,
				contentDescription = MR.strings.projects_list_item_more_button.get()
			)
		}

		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			items.forEach { item ->
				DropdownMenuItem(
					modifier = Modifier.testTag(item.get()),
					text = { Text(text = item.get()) },
					onClick = {
						onItemClick(item)
						expanded = false
					}
				)
			}
		}
	}
}