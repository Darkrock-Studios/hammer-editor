package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun EncyclopediaEntryConflict(
	entityConflict: ProjectSync.EntityConflict.EncyclopediaEntryConflict,
	component: ProjectSync
) {
	Column(modifier = Modifier.fillMaxSize()) {
		Spacer(modifier = Modifier.size(Ui.Padding.L))

		Box(
			modifier = Modifier.fillMaxWidth(),
			contentAlignment = Alignment.Center
		) {
			Row {
				Icon(
					Icons.Default.Warning,
					contentDescription = MR.strings.sync_conflict.get(),
					modifier = Modifier.size(32.dp).align(Alignment.CenterVertically),
					tint = MaterialTheme.colorScheme.error
				)

				Text(
					text = MR.strings.sync_conflict_encyclopedia.get(),
					style = MaterialTheme.typography.headlineSmall,
					modifier = Modifier.padding(start = Ui.Padding.L).align(Alignment.CenterVertically)
				)
			}
		}

		Spacer(modifier = Modifier.size(Ui.Padding.L))

		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.padding(Ui.Padding.L).weight(1f)) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = MR.strings.sync_conflict_local_entry.get(),
						style = MaterialTheme.typography.headlineSmall
					)
					Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
						Text(MR.strings.sync_conflict_local_use_button.get())
					}
				}
				SelectionContainer {
					Text(
						entityConflict.clientEntity.name,
						style = MaterialTheme.typography.bodyLarge
					)
				}
				Text(entityConflict.serverEntity.entryType)
				Text(
					if (entityConflict.serverEntity.image != null)
						MR.strings.sync_conflict_encyclopedia_has_image.get()
					else
						MR.strings.sync_conflict_encyclopedia_no_image.get(),
					style = MaterialTheme.typography.bodyLarge
				)
				SelectionContainer {
					Text(
						entityConflict.clientEntity.text,
						modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
					)
				}
				FlowRow {
					entityConflict.clientEntity.tags.forEach { tag ->
						SuggestionChip(onClick = {}, label = { Text(tag) })
					}
				}
			}

			Column(modifier = Modifier.padding(Ui.Padding.L).weight(1f)) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = MR.strings.sync_conflict_remote_entry.get(),
						style = MaterialTheme.typography.headlineSmall
					)
					Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
						Text(MR.strings.sync_conflict_remote_use_button.get())
					}
				}
				SelectionContainer {
					Text(
						entityConflict.serverEntity.name,
						style = MaterialTheme.typography.bodyLarge
					)
				}
				Text(entityConflict.serverEntity.entryType)
				Text(
					if (entityConflict.serverEntity.image != null)
						MR.strings.sync_conflict_encyclopedia_has_image.get()
					else
						MR.strings.sync_conflict_encyclopedia_no_image.get(),
					style = MaterialTheme.typography.bodyLarge
				)
				SelectionContainer {
					Text(
						entityConflict.serverEntity.text,
						modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
					)
				}
				FlowRow {
					entityConflict.serverEntity.tags.forEach { tag ->
						SuggestionChip(onClick = {}, label = { Text(tag) })
					}
				}
			}
		}
	}

	Column {
		Text(MR.strings.sync_conflict_encyclopedia_title.get())
		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.weight(1f)) {
				Text(MR.strings.sync_conflict_encyclopedia_local.get(entityConflict.clientEntity.name))
				Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
					Text(MR.strings.sync_conflict_local_use_button.get())
				}
				Text(entityConflict.clientEntity.text)
			}

			Column(modifier = Modifier.weight(1f)) {
				Text(MR.strings.sync_conflict_encyclopedia_local.get(entityConflict.serverEntity.name))
				Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
					Text(MR.strings.sync_conflict_remote_use_button.get())
				}
				Text(entityConflict.serverEntity.text)
			}
		}
	}
}