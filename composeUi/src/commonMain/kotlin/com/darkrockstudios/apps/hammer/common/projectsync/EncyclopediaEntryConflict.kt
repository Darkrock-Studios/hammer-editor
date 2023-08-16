package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
internal fun EncyclopediaEntryConflict(
	entityConflict: ProjectSync.EntityConflict.EncyclopediaEntryConflict,
	component: ProjectSync,
	screenCharacteristics: WindowSizeClass
) {
	EntityConflict(
		entityConflict = entityConflict,
		component = component,
		screenCharacteristics = screenCharacteristics,
		LocalEntity = { m, c, p -> LocalEntry(m, c, p) },
		RemoteEntity = { m, c, p -> RemoteEntry(m, c, p) },
	)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LocalEntry(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict<ApiProjectEntity.EncyclopediaEntryEntity>,
	component: ProjectSync
) {
	Column(modifier = modifier.padding(Ui.Padding.L)) {
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
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RemoteEntry(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict<ApiProjectEntity.EncyclopediaEntryEntity>,
	component: ProjectSync
) {
	Column(modifier = modifier.padding(Ui.Padding.L)) {
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

		Row {
			Text(
				modifier = Modifier.padding(end = Ui.Padding.M),
				text = MR.strings.sync_conflict_encyclopedia_label_name.get(),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold
			)

			SelectionContainer {
				Text(
					entityConflict.serverEntity.name,
					style = MaterialTheme.typography.bodyLarge
				)
			}
		}

		Row {
			Text(
				modifier = Modifier.padding(end = Ui.Padding.M),
				text = MR.strings.sync_conflict_encyclopedia_label_type.get(),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold
			)

			Text(
				text = entityConflict.serverEntity.entryType,
				style = MaterialTheme.typography.bodyLarge,
			)
		}

		Row {
			Text(
				modifier = Modifier.padding(end = Ui.Padding.M),
				text = MR.strings.sync_conflict_encyclopedia_label_image.get(),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold
			)

			Text(
				if (entityConflict.serverEntity.image != null)
					MR.strings.sync_conflict_encyclopedia_has_image.get()
				else
					MR.strings.sync_conflict_encyclopedia_no_image.get(),
				style = MaterialTheme.typography.bodyLarge
			)
		}

		Text(
			modifier = Modifier.padding(end = Ui.Padding.M),
			text = MR.strings.sync_conflict_encyclopedia_label_content.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Bold
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