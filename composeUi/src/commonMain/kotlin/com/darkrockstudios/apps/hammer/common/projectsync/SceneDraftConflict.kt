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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes

@Composable
internal fun SceneDraftConflict(
	entityConflict: ProjectSync.EntityConflict.SceneDraftConflict,
	component: ProjectSync,
	screenCharacteristics: WindowSizeClass
) {
	EntityConflict(
		entityConflict = entityConflict,
		component = component,
		screenCharacteristics = screenCharacteristics,
		LocalEntity = { m, c, p -> LocalDraft(m, c, p) },
		RemoteEntity = { m, c, p -> RemoteDraft(m, c, p) },
	)
}

@Composable
private fun LocalDraft(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict<ApiProjectEntity.SceneDraftEntity>,
	component: ProjectSync
) {
	val strRes = rememberStrRes()
	val entity = component.state.value.entityConflict?.clientEntity as? ApiProjectEntity.SceneDraftEntity
	var nameTextValue by rememberSaveable(entity) { mutableStateOf(entity?.name ?: "") }
	var nameError by rememberSaveable(entity) { mutableStateOf<String?>(null) }
	var contentTextValue by rememberSaveable(entity) { mutableStateOf(entity?.content ?: "") }

	Column(modifier = modifier.padding(Ui.Padding.L)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = MR.strings.sync_conflict_title_scene_draft_local.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = {
				val error = component.resolveConflict(
					entityConflict.clientEntity.copy(
						name = nameTextValue,
						content = contentTextValue
					)
				)

				if (error is ProjectSync.EntityMergeError.SceneDraftMergeError) {
					nameError = error.nameError?.text(strRes)
				}
			}) {
				Text(MR.strings.sync_conflict_local_use_button.get())
			}
		}
		Text(
			text = MR.strings.sync_conflict_merge_explained.get(),
			style = MaterialTheme.typography.bodySmall,
			fontStyle = FontStyle.Italic
		)
		Spacer(Modifier.size(Ui.Padding.XL))
		TextField(
			value = nameTextValue,
			onValueChange = { nameTextValue = it },
			placeholder = { Text(MR.strings.sync_conflict_title_scene_draft_field_name.get()) },
			label = { Text(MR.strings.sync_conflict_title_scene_draft_field_name.get()) },
			isError = (nameError != null),
		)
		Text(
			nameError ?: "",
			style = MaterialTheme.typography.bodySmall,
			fontStyle = FontStyle.Italic,
			color = MaterialTheme.colorScheme.error
		)
		Spacer(Modifier.size(Ui.Padding.XL))
		TextField(
			value = contentTextValue,
			onValueChange = { contentTextValue = it },
			placeholder = { Text(MR.strings.sync_conflict_title_scene_draft_field_content.get()) },
			label = { Text(MR.strings.sync_conflict_title_scene_draft_field_content.get()) }
		)
	}
}

@Composable
private fun RemoteDraft(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict<ApiProjectEntity.SceneDraftEntity>,
	component: ProjectSync
) {
	Column(modifier = modifier.padding(Ui.Padding.L)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = MR.strings.sync_conflict_title_scene_draft_remote.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
				Text(MR.strings.sync_conflict_remote_use_button.get())
			}
		}
		Spacer(Modifier.size(Ui.Padding.XL))
		Text(
			MR.strings.sync_conflict_title_scene_draft_field_name.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Bold
		)
		SelectionContainer {
			Text(
				entityConflict.serverEntity.name,
				style = MaterialTheme.typography.bodyLarge
			)
		}
		Spacer(Modifier.size(Ui.Padding.XL))
		Text(
			MR.strings.sync_conflict_title_scene_draft_field_content.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Bold
		)
		SelectionContainer {
			Text(
				entityConflict.serverEntity.content,
				modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
			)
		}
	}
}