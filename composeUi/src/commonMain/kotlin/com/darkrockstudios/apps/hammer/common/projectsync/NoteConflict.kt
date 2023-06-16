package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
internal fun NoteConflict(
	entityConflict: ProjectSync.EntityConflict.NoteConflict,
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
					text = "Note Conflict:",
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
						text = "Local Note:",
						style = MaterialTheme.typography.headlineSmall
					)
					Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
						Text("Use Local")
					}
				}
				SelectionContainer {
					Text(
						entityConflict.clientEntity.content,
						modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
					)
				}
			}

			Column(modifier = Modifier.padding(Ui.Padding.L).weight(1f)) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = "Remote Note:",
						style = MaterialTheme.typography.headlineSmall
					)
					Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
						Text("Use Remote")
					}
				}
				SelectionContainer {
					Text(
						entityConflict.serverEntity.content,
						modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
					)
				}
			}
		}
	}
}