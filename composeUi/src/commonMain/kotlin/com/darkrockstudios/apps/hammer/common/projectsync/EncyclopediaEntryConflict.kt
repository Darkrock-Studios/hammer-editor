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
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui

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
					contentDescription = "Conflict",
					modifier = Modifier.size(32.dp).align(Alignment.CenterVertically),
					tint = MaterialTheme.colorScheme.error
				)

				Text(
					text = "Encyclopedia Entry Conflict:",
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
						text = "Local Entry:",
						style = MaterialTheme.typography.headlineSmall
					)
					Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
						Text("Use Local")
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
					if (entityConflict.serverEntity.image != null) "Has Image" else "No Image",
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
						text = "Remote Entry:",
						style = MaterialTheme.typography.headlineSmall
					)
					Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
						Text("Use Remote")
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
					if (entityConflict.serverEntity.image != null) "Has Image" else "No Image",
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
		Text("Encyclopedia Entry Conflict")
		Spacer(modifier = Modifier.size(Ui.Padding.L))
		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.weight(1f)) {
				Text("Local Event: ${entityConflict.clientEntity.name}")
				Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
					Text("Use Local")
				}
				Text(entityConflict.clientEntity.text)
			}

			Column(modifier = Modifier.weight(1f)) {
				Text("Remote Event: ${entityConflict.serverEntity.name}")
				Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
					Text("Use Remote")
				}
				Text(entityConflict.serverEntity.text)
			}
		}
	}
}