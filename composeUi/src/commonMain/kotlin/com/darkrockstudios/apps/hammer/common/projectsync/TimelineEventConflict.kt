package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
internal fun TimelineEventConflict(
	entityConflict: ProjectSync.EntityConflict.TimelineEventConflict,
	component: ProjectSync,
	screenCharacteristics: WindowSizeClass
) {
	EntityConflict(
		entityConflict = entityConflict,
		component = component,
		screenCharacteristics = screenCharacteristics,
		LocalEntity = @Composable { m, c, p ->
			LocalNote(m, c, p)
		},
		RemoteEntity = @Composable { m, c, p ->
			RemoteNote(m, c, p)
		},
	)
}

@Composable
private fun getDateText(entityConflict: ProjectSync.EntityConflict<ApiProjectEntity.TimelineEventEntity>): String {
	val date = entityConflict.clientEntity.date
	return if (date.isNullOrBlank()) {
		MR.strings.sync_conflict_timeline_event_missing_date.get()
	} else {
		date
	}
}

@Composable
private fun LocalNote(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict<ApiProjectEntity.TimelineEventEntity>,
	component: ProjectSync
) {
	Column(modifier = modifier.padding(Ui.Padding.L)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = MR.strings.sync_conflict_title_timeline_event_local.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
				Text(MR.strings.sync_conflict_local_use_button.get())
			}
		}


		SelectionContainer {
			Text(
				getDateText(entityConflict),
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
}

@Composable
private fun RemoteNote(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict<ApiProjectEntity.TimelineEventEntity>,
	component: ProjectSync
) {
	Column(modifier = modifier.padding(Ui.Padding.L)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = MR.strings.sync_conflict_title_timeline_event_remote.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
				Text(MR.strings.sync_conflict_remote_use_button.get())
			}
		}
		SelectionContainer {
			Text(
				getDateText(entityConflict),
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