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
internal fun SceneConflict(
	entityConflict: ProjectSynchronization.EntityConflict.SceneConflict,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass
) {
	EntityConflict(
		entityConflict = entityConflict,
		component = component,
		screenCharacteristics = screenCharacteristics,
		LocalEntity = { m, c, p -> LocalScene(m, c, p) },
		RemoteEntity = { m, c, p -> RemoteScene(m, c, p) },
	)
}

@Composable
private fun LocalScene(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<ApiProjectEntity.SceneEntity>,
	component: ProjectSynchronization
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val entity = component.state.value.entityConflict?.clientEntity as? ApiProjectEntity.SceneEntity
	var nameTextValue by rememberSaveable(entity) { mutableStateOf(entity?.name ?: "") }
	var nameError by rememberSaveable(entity) { mutableStateOf<String?>(null) }
	var contentTextValue by rememberSaveable(entity) { mutableStateOf(entity?.content ?: "") }

	Column(modifier = modifier.padding(Ui.Padding.M)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = Res.string.sync_conflict_title_scene_local.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = {
				val error = component.resolveConflict(
					entityConflict.clientEntity.copy(
						name = nameTextValue,
						content = contentTextValue
					)
				)

				if (error is ProjectSynchronization.EntityMergeError.SceneMergeError) {
					scope.launch {
						nameError = error.nameError?.text(strRes)
					}
				}
			}) {
				Text(Res.string.sync_conflict_local_use_button.get())
			}
		}
		Spacer(Modifier.size(Ui.Padding.M))
		TextField(
			value = nameTextValue,
			onValueChange = { nameTextValue = it },
			placeholder = { Text(Res.string.sync_conflict_title_scene_field_name.get()) },
			label = { Text(Res.string.sync_conflict_title_scene_field_name.get()) },
			isError = (nameError != null),
			modifier = Modifier.fillMaxWidth(),
			singleLine = true
		)
		if (nameError != null) {
			Text(
				nameError ?: "",
				style = MaterialTheme.typography.bodySmall,
				fontStyle = FontStyle.Italic,
				color = MaterialTheme.colorScheme.error
			)
		}
		Spacer(Modifier.size(Ui.Padding.M))
		TextField(
			value = contentTextValue,
			onValueChange = { contentTextValue = it },
			placeholder = { Text(Res.string.sync_conflict_title_scene_field_content.get()) },
			label = { Text(Res.string.sync_conflict_title_scene_field_content.get()) },
			modifier = Modifier.fillMaxWidth().weight(1f)
		)
	}
}

@Composable
private fun RemoteScene(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<ApiProjectEntity.SceneEntity>,
	component: ProjectSynchronization
) {
	Column(modifier = modifier.padding(Ui.Padding.M)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = Res.string.sync_conflict_title_scene_remote.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
				Text(Res.string.sync_conflict_remote_use_button.get())
			}
		}
		Spacer(Modifier.size(Ui.Padding.M))
		Text(
			Res.string.sync_conflict_title_scene_field_name.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Bold
		)
		SelectionContainer {
			Text(
				entityConflict.serverEntity.name,
				style = MaterialTheme.typography.bodyLarge
			)
		}
		Spacer(Modifier.size(Ui.Padding.M))
		Text(
			Res.string.sync_conflict_title_scene_field_content.get(),
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