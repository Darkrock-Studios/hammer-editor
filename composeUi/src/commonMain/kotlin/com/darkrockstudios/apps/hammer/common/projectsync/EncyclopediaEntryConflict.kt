package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
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
	val strRes = rememberStrRes()
	val entity = component.state.value.entityConflict?.clientEntity as? ApiProjectEntity.EncyclopediaEntryEntity
	var nameTextValue by rememberSaveable(entity) { mutableStateOf(entity?.name ?: "") }
	var nameError by rememberSaveable(entity) { mutableStateOf<String?>(null) }
	var contentTextValue by rememberSaveable(entity) { mutableStateOf(entity?.text ?: "") }
	var contentError by rememberSaveable(entity) { mutableStateOf<String?>(null) }

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
			Button(onClick = {
				val error = component.resolveConflict(
					entityConflict.clientEntity.copy(
						name = nameTextValue,
						text = contentTextValue
					)
				)
				if (error is ProjectSync.EntityMergeError.EncyclopediaEntryMergeError) {
					nameError = error.nameError?.text(strRes)
					contentError = error.contentError?.text(strRes)
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
		Spacer(Modifier.size(Ui.Padding.XL))
		TextField(
			value = nameTextValue,
			onValueChange = { nameTextValue = it },
			placeholder = { Text(MR.strings.sync_conflict_encyclopedia_label_name.get()) },
			label = { Text(MR.strings.sync_conflict_encyclopedia_label_name.get()) },
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
			placeholder = { Text(MR.strings.sync_conflict_encyclopedia_label_content.get()) },
			label = { Text(MR.strings.sync_conflict_encyclopedia_label_content.get()) },
			isError = (contentError != null),
		)
		Text(
			contentError ?: "",
			style = MaterialTheme.typography.bodySmall,
			fontStyle = FontStyle.Italic,
			color = MaterialTheme.colorScheme.error
		)
		Spacer(Modifier.size(Ui.Padding.XL))
		Text(
			MR.strings.sync_conflict_encyclopedia_label_tags.get(),
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

		Spacer(Modifier.size(Ui.Padding.XL))
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

		Spacer(Modifier.size(Ui.Padding.XL))
		Text(
			MR.strings.sync_conflict_encyclopedia_label_tags.get(),
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