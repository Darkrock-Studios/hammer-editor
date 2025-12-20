package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
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
internal fun EncyclopediaEntryConflict(
	entityConflict: ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict,
	component: ProjectSynchronization,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalEntry(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<ApiProjectEntity.EncyclopediaEntryEntity>,
	component: ProjectSynchronization
) {
	val strRes = rememberStrRes()
	val scope = rememberCoroutineScope()
	val entity = component.state.value.entityConflict?.clientEntity as? ApiProjectEntity.EncyclopediaEntryEntity
	var nameTextValue by rememberSaveable(entity) { mutableStateOf(entity?.name ?: "") }
	var nameError by rememberSaveable(entity) { mutableStateOf<String?>(null) }
	var contentTextValue by rememberSaveable(entity) { mutableStateOf(entity?.text ?: "") }
	var contentError by rememberSaveable(entity) { mutableStateOf<String?>(null) }

	Column(modifier = modifier.padding(Ui.Padding.M)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = Res.string.sync_conflict_local_entry.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = {
				val error = component.resolveConflict(
					entityConflict.clientEntity.copy(
						name = nameTextValue,
						text = contentTextValue
					)
				)
				if (error is ProjectSynchronization.EntityMergeError.EncyclopediaEntryMergeError) {
					scope.launch {
						nameError = error.nameError?.text(strRes)
						contentError = error.contentError?.text(strRes)
					}
				}
			}) {
				Text(Res.string.sync_conflict_local_use_button.get())
			}
		}
		Spacer(Modifier.size(Ui.Padding.M))

		Row {
			Text(entityConflict.clientEntity.entryType, modifier = Modifier.padding(end = Ui.Padding.M))
			Text(
				if (entityConflict.clientEntity.image != null)
					Res.string.sync_conflict_encyclopedia_has_image.get()
				else
					Res.string.sync_conflict_encyclopedia_no_image.get(),
				style = MaterialTheme.typography.bodyLarge
			)
		}
		Spacer(Modifier.size(Ui.Padding.M))
		TextField(
			value = nameTextValue,
			onValueChange = { nameTextValue = it },
			placeholder = { Text(Res.string.sync_conflict_encyclopedia_label_name.get()) },
			label = { Text(Res.string.sync_conflict_encyclopedia_label_name.get()) },
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
			placeholder = { Text(Res.string.sync_conflict_encyclopedia_label_content.get()) },
			label = { Text(Res.string.sync_conflict_encyclopedia_label_content.get()) },
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
		Spacer(Modifier.size(Ui.Padding.M))
		Text(
			Res.string.sync_conflict_encyclopedia_label_tags.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Bold
		)
		FlowRow {
			entityConflict.clientEntity.tags.forEach { tag ->
				InputChip(
					onClick = {},
					label = { Text(tag) },
					leadingIcon = {
						Icon(
							Icons.Filled.Tag,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurface
						)
					},
					enabled = true,
					selected = false
				)
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RemoteEntry(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<ApiProjectEntity.EncyclopediaEntryEntity>,
	component: ProjectSynchronization
) {
	Column(modifier = modifier.padding(Ui.Padding.M)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = Res.string.sync_conflict_remote_entry.get(),
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
				Text(Res.string.sync_conflict_remote_use_button.get())
			}
		}
		Spacer(Modifier.size(Ui.Padding.M))
		Row {
			Text(
				modifier = Modifier.padding(end = Ui.Padding.M),
				text = Res.string.sync_conflict_encyclopedia_label_name.get(),
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
				text = Res.string.sync_conflict_encyclopedia_label_type.get(),
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
				text = Res.string.sync_conflict_encyclopedia_label_image.get(),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold
			)
			Text(
				if (entityConflict.serverEntity.image != null)
					Res.string.sync_conflict_encyclopedia_has_image.get()
				else
					Res.string.sync_conflict_encyclopedia_no_image.get(),
				style = MaterialTheme.typography.bodyLarge
			)
		}
		Spacer(Modifier.size(Ui.Padding.M))
		Text(
			text = Res.string.sync_conflict_encyclopedia_label_content.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Bold
		)
		SelectionContainer(modifier = Modifier.weight(1f)) {
			Text(
				entityConflict.serverEntity.text,
				modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
			)
		}
		Spacer(Modifier.size(Ui.Padding.M))
		Text(
			Res.string.sync_conflict_encyclopedia_label_tags.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Bold
		)
		FlowRow {
			entityConflict.serverEntity.tags.forEach { tag ->
				InputChip(
					onClick = {},
					label = { Text(tag) },
					leadingIcon = {
						Icon(
							Icons.Filled.Tag,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurface
						)
					},
					enabled = true,
					selected = false
				)
			}
		}
	}
}