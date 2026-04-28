package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.encyclopedia.EntryRefChipLabel
import com.darkrockstudios.apps.hammer.common.encyclopedia.UnknownEntryRefChipLabel
import kotlinx.coroutines.launch

private enum class SceneConflictTab {
	CONTENT,
	METADATA,
	REFERENCES
}

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
	var outlineTextValue by rememberSaveable(entity) { mutableStateOf(entity?.outline ?: "") }
	var notesTextValue by rememberSaveable(entity) { mutableStateOf(entity?.notes ?: "") }
	var selectedTab by rememberSaveable { mutableStateOf(SceneConflictTab.CONTENT) }

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
						content = contentTextValue,
						outline = outlineTextValue,
						notes = notesTextValue
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

		// Content area with tabs
		Column(modifier = Modifier.weight(1f)) {
			when (selectedTab) {
				SceneConflictTab.CONTENT -> {
					TextField(
						value = contentTextValue,
						onValueChange = { contentTextValue = it },
						placeholder = { Text(Res.string.sync_conflict_title_scene_field_content.get()) },
						label = { Text(Res.string.sync_conflict_title_scene_field_content.get()) },
						modifier = Modifier.fillMaxWidth().weight(1f)
					)
				}
				SceneConflictTab.METADATA -> {
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
						value = outlineTextValue,
						onValueChange = { outlineTextValue = it },
						placeholder = { Text(Res.string.sync_conflict_title_scene_field_outline.get()) },
						label = { Text(Res.string.sync_conflict_title_scene_field_outline.get()) },
						modifier = Modifier.fillMaxWidth().weight(1f)
					)
					Spacer(Modifier.size(Ui.Padding.M))
					TextField(
						value = notesTextValue,
						onValueChange = { notesTextValue = it },
						placeholder = { Text(Res.string.sync_conflict_title_scene_field_notes.get()) },
						label = { Text(Res.string.sync_conflict_title_scene_field_notes.get()) },
						modifier = Modifier.fillMaxWidth().weight(1f)
					)
				}
				SceneConflictTab.REFERENCES -> {
					ReferencesTabBody(
						confirmedIds = entityConflict.clientEntity.confirmedReferences,
						dismissedIds = entityConflict.clientEntity.dismissedReferences,
						component = component,
					)
				}
			}
		}

		SceneConflictTabRow(selectedTab) { selectedTab = it }
	}
}

@Composable
private fun RemoteScene(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<ApiProjectEntity.SceneEntity>,
	component: ProjectSynchronization
) {
	var selectedTab by rememberSaveable { mutableStateOf(SceneConflictTab.CONTENT) }

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

		// Content area with tabs
		Column(modifier = Modifier.weight(1f)) {
			when (selectedTab) {
				SceneConflictTab.CONTENT -> {
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
				SceneConflictTab.METADATA -> {
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
						Res.string.sync_conflict_title_scene_field_outline.get(),
						style = MaterialTheme.typography.bodyLarge,
						fontWeight = FontWeight.Bold
					)
					SelectionContainer(modifier = Modifier.weight(1f)) {
						Text(
							entityConflict.serverEntity.outline,
							modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
						)
					}
					Spacer(Modifier.size(Ui.Padding.M))
					Text(
						Res.string.sync_conflict_title_scene_field_notes.get(),
						style = MaterialTheme.typography.bodyLarge,
						fontWeight = FontWeight.Bold
					)
					SelectionContainer(modifier = Modifier.weight(1f)) {
						Text(
							entityConflict.serverEntity.notes,
							modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
						)
					}
				}
				SceneConflictTab.REFERENCES -> {
					ReferencesTabBody(
						confirmedIds = entityConflict.serverEntity.confirmedReferences,
						dismissedIds = entityConflict.serverEntity.dismissedReferences,
						component = component,
					)
				}
			}
		}

		SceneConflictTabRow(selectedTab) { selectedTab = it }
	}
}

@Composable
private fun SceneConflictTabRow(
	selected: SceneConflictTab,
	onSelect: (SceneConflictTab) -> Unit,
) {
	TabRow(selectedTabIndex = selected.ordinal) {
		Tab(
			text = { Text(Res.string.sync_conflict_scene_tab_content.get()) },
			selected = selected == SceneConflictTab.CONTENT,
			onClick = { onSelect(SceneConflictTab.CONTENT) }
		)
		Tab(
			text = { Text(Res.string.sync_conflict_scene_tab_metadata.get()) },
			selected = selected == SceneConflictTab.METADATA,
			onClick = { onSelect(SceneConflictTab.METADATA) }
		)
		Tab(
			text = { Text(Res.string.sync_conflict_scene_tab_references.get()) },
			selected = selected == SceneConflictTab.REFERENCES,
			onClick = { onSelect(SceneConflictTab.REFERENCES) }
		)
	}
}

/**
 * Read-only chip lists of confirmed and dismissed reference IDs. Renders the
 * same way on both Local and Remote panels - reference sets ride along with
 * whichever side the user picks (consistent with how `tags` already work).
 *
 * Unresolvable IDs (entry not yet delivered to this client, or deleted
 * locally) render as `Unknown #id` with a broken-link icon so the user can
 * still see something exists, and won't crash the UI by trying to look up
 * a name that isn't there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferencesTabBody(
	confirmedIds: Set<Int>,
	dismissedIds: Set<Int>,
	component: ProjectSynchronization,
) {
	Column(modifier = Modifier.padding(top = Ui.Padding.M)) {
		Text(
			Res.string.sync_conflict_references_confirmed_label.get(),
			style = MaterialTheme.typography.titleSmall,
		)
		SpacerL()
		ReferenceChipFlow(ids = confirmedIds, component = component)

		Spacer(Modifier.size(Ui.Padding.L))

		Text(
			Res.string.sync_conflict_references_dismissed_label.get(),
			style = MaterialTheme.typography.titleSmall,
		)
		SpacerL()
		ReferenceChipFlow(ids = dismissedIds, component = component)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferenceChipFlow(
	ids: Set<Int>,
	component: ProjectSynchronization,
) {
	if (ids.isEmpty()) {
		Text(
			Res.string.sync_conflict_references_empty.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		return
	}
	FlowRow(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
		verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
	) {
		for (id in ids) {
			val def = component.resolveEntryRef(id)
			AssistChip(
				onClick = {},
				enabled = false,
				label = {
					if (def != null) {
						EntryRefChipLabel(type = def.type, name = def.name)
					} else {
						UnknownEntryRefChipLabel(id = id)
					}
				},
			)
		}
	}
}
