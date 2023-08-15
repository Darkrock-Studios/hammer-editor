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
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
internal fun SceneDraftConflict(
	entityConflict: ProjectSync.EntityConflict.SceneDraftConflict,
	component: ProjectSync
) {
	Column(modifier = Modifier.fillMaxSize()) {
		Spacer(modifier = Modifier.size(Ui.Padding.L))

		Row(modifier = Modifier.fillMaxSize()) {
			Column(modifier = Modifier.padding(Ui.Padding.L).weight(1f)) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = "Local Draft:",
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
						text = "Remote Draft:",
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