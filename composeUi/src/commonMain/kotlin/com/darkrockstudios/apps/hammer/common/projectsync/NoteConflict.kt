package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.launch

@Composable
internal fun NoteConflict(
	entityConflict: ProjectSynchronization.EntityConflict.NoteConflict,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass
) {
	EntityConflict(
		entityConflict = entityConflict,
		component = component,
		screenCharacteristics = screenCharacteristics,
		LocalEntity = { m, c, p -> LocalNote(m, c, p) },
		RemoteEntity = { m, c, p -> RemoteNote(m, c, p) },
	)
}

@Composable
private fun LocalNote(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<ApiProjectEntity.NoteEntity>,
	component: ProjectSynchronization
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val entity = component.state.value.entityConflict?.clientEntity as? ApiProjectEntity.NoteEntity
	var contentTextValue by rememberSaveable(entity) { mutableStateOf(entity?.content ?: "") }
	var contentError by rememberSaveable(entity) { mutableStateOf<String?>(null) }

	Column(modifier = modifier.padding(Ui.Padding.M)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = Res.string.sync_conflict_title_note_local.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = {
				val error = component.resolveConflict(
					entityConflict.clientEntity.copy(
						content = contentTextValue
					)
				)

				if (error is ProjectSynchronization.EntityMergeError.NoteMergeError) {
					scope.launch {
						contentError = error.noteError?.text(strRes)
					}
				}
			}) {
				Text(Res.string.sync_conflict_local_use_button.get())
			}
		}
		Spacer(Modifier.size(Ui.Padding.M))
		TextField(
			value = contentTextValue,
			onValueChange = { contentTextValue = it },
			placeholder = { Text(Res.string.sync_conflict_title_note_field_name.get()) },
			label = { Text(Res.string.sync_conflict_title_note_field_name.get()) },
			isError = (contentError != null),
			modifier = Modifier.fillMaxWidth().weight(1f)
		)
		if (contentError != null) {
			Text(
				contentError ?: "",
				style = MaterialTheme.typography.bodySmall,
				fontStyle = FontStyle.Italic,
				color = MaterialTheme.colorScheme.error
			)
		}
	}
}

@Composable
private fun RemoteNote(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<ApiProjectEntity.NoteEntity>,
	component: ProjectSynchronization
) {
	Column(modifier = modifier.padding(Ui.Padding.M)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = Res.string.sync_conflict_title_note_remote.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
				Text(Res.string.sync_conflict_remote_use_button.get())
			}
		}
		Spacer(Modifier.size(Ui.Padding.M))
		Text(
			Res.string.sync_conflict_title_note_field_name.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Bold
		)
		SelectionContainer(modifier = Modifier.weight(1f)) {
			Text(
				entityConflict.serverEntity.content,
				modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
			)
		}
	}
}