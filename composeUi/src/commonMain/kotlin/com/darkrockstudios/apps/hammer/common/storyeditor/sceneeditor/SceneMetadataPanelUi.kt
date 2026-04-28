package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.CollapsableSection
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.encyclopedia.getEntryTypeIcon

val SCENE_METADATA_MIN_WIDTH = 300.dp
val SCENE_METADATA_MAX_WIDTH = 600.dp

@Composable
fun SceneMetadataPanelUi(
	component: SceneMetadataPanel,
	modifier: Modifier = Modifier,
	closeMetadata: () -> Unit
) {
	val state by component.state.subscribeAsState()

	Card(
		modifier = modifier.widthIn(min = SCENE_METADATA_MIN_WIDTH),
		elevation = CardDefaults.cardElevation(Ui.ToneElevation.MEDIUM)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.XL).verticalScroll(rememberScrollState())) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				IconButton(onClick = closeMetadata) {
					Icon(
						imageVector = Icons.Default.Close,
						contentDescription = Res.string.scene_editor_metadata_hide_button.get(),
						tint = MaterialTheme.colorScheme.onBackground
					)
				}

				Text(
					text = Res.string.scene_editor_metadata_title.get(),
					style = MaterialTheme.typography.headlineMedium
				)
			}

			SpacerL()

			Text(
				state.sceneItem.name,
				style = MaterialTheme.typography.titleLarge,
			)

			Row(modifier = Modifier.align(Alignment.End)) {
				Text(
					Res.string.scene_editor_metadata_word_count_label.get(),
					style = MaterialTheme.typography.headlineSmall,
				)

				Text(
					"${state.wordCount}",
					style = MaterialTheme.typography.headlineSmall,
				)
			}

			SpacerXL()

			OutlinedTextField(
				value = state.metadata.outline,
				onValueChange = component::updateOutline,
				modifier = Modifier.heightIn(128.dp).fillMaxWidth(),
				maxLines = 4,
				label = { Text(Res.string.scene_editor_metadata_outline_label.get()) },
				placeholder = {
					Text(
						Res.string.scene_editor_metadata_outline_placeholder.get(),
						style = MaterialTheme.typography.bodyLarge,
					)
				},
				textStyle = MaterialTheme.typography.bodyLarge,
			)

			SpacerXL()

			OutlinedTextField(
				value = state.metadata.notes,
				onValueChange = component::updateNotes,
				modifier = Modifier.heightIn(128.dp).fillMaxWidth(),
				maxLines = 4,
				label = { Text(Res.string.scene_editor_metadata_notes_label.get()) },
				placeholder = {
					Text(
						Res.string.scene_editor_metadata_notes_placeholder.get(),
						style = MaterialTheme.typography.bodyLarge,
					)
				},
				textStyle = MaterialTheme.typography.bodyLarge,
			)

			SpacerXL()

			var isDraftNameValid by remember {
				mutableStateOf(component.validateDraftName(state.metadata.currentDraftName))
			}

			OutlinedTextField(
				value = state.metadata.currentDraftName,
				onValueChange = { newName ->
					isDraftNameValid = component.validateDraftName(newName)
					component.updateDraftName(newName)
				},
				modifier = Modifier.fillMaxWidth(),
				maxLines = 1,
				label = { Text(Res.string.scene_editor_metadata_draft_name_label.get()) },
				textStyle = MaterialTheme.typography.bodyLarge,
				isError = !isDraftNameValid,
				supportingText = if (!isDraftNameValid) {
					{ Text(Res.string.scene_draft_invalid_name.get()) }
				} else null
			)

			SpacerXL()

			CollapsableSection(
				header = {
					Text(
						Res.string.scene_editor_metadata_references_header.get(),
						style = MaterialTheme.typography.titleMedium,
					)
				},
				body = { ReferencesSection(state, component) }
			)

			SpacerXL()

			CollapsableSection(
				header = {
					Text(
						Res.string.scene_editor_metadata_advanced_header.get(),
						style = MaterialTheme.typography.titleMedium,
					)
				},
				body = { AdvancedSection(state) }
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferencesSection(
	state: SceneMetadataPanel.State,
	component: SceneMetadataPanel,
) {
	Column(modifier = Modifier.padding(Ui.Padding.L)) {
		Text(
			Res.string.scene_editor_metadata_references_confirmed_label.get(),
			style = MaterialTheme.typography.titleSmall,
		)
		SpacerL()
		if (state.confirmedRefs.isEmpty() && state.suggestedRefs.isEmpty() && state.dismissedRefs.isEmpty()) {
			Text(
				Res.string.scene_editor_metadata_references_empty.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		} else {
			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
				verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
			) {
				for (ref in state.confirmedRefs) {
					InputChip(
						onClick = { component.navigateToEntry(ref) },
						label = { ChipLabel(type = ref.type, name = ref.name) },
						trailingIcon = {
							ChipAction(
								onClick = { component.unconfirmReference(ref.id) },
								icon = Icons.Filled.Close,
								contentDescription = Res.string.scene_editor_metadata_references_unconfirm.get(),
								tint = MaterialTheme.colorScheme.onSurface,
							)
						},
						selected = true,
					)
				}
			}
		}

		if (state.suggestedRefs.isNotEmpty()) {
			SpacerXL()
			Text(
				Res.string.scene_editor_metadata_references_suggested_label.get(),
				style = MaterialTheme.typography.titleSmall,
			)
			SpacerL()
			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
				verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
			) {
				for (ref in state.suggestedRefs) {
					AssistChip(
						onClick = { component.navigateToEntry(ref.entryDef) },
						label = { ChipLabel(type = ref.entryDef.type, name = ref.entryDef.name) },
						leadingIcon = {
							ChipAction(
								onClick = { component.confirmReference(ref.entryDef.id) },
								icon = Icons.Filled.Check,
								contentDescription = Res.string.scene_editor_metadata_references_confirm.get(),
								tint = MaterialTheme.colorScheme.primary,
							)
						},
						trailingIcon = {
							ChipAction(
								onClick = { component.dismissReference(ref.entryDef.id) },
								icon = Icons.Filled.Close,
								contentDescription = Res.string.scene_editor_metadata_references_dismiss.get(),
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						},
					)
				}
			}
		}

		if (state.dismissedRefs.isNotEmpty()) {
			SpacerXL()
			CollapsableSection(
				header = {
					Text(
						Res.string.scene_editor_metadata_references_dismissed_label.get(),
						style = MaterialTheme.typography.titleSmall,
					)
				},
				body = {
					FlowRow(
						modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.M),
						horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
						verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
					) {
						for (ref in state.dismissedRefs) {
							AssistChip(
								onClick = { component.navigateToEntry(ref) },
								label = { ChipLabel(type = ref.type, name = ref.name) },
								trailingIcon = {
									ChipAction(
										onClick = { component.restoreDismissedReference(ref.id) },
										icon = Icons.Filled.Add,
										contentDescription = Res.string.scene_editor_metadata_references_restore.get(),
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
									)
								},
							)
						}
					}
				}
			)
		}
	}
}

@Composable
private fun ChipAction(
	onClick: () -> Unit,
	icon: ImageVector,
	contentDescription: String,
	tint: Color,
) {
	IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
		Icon(icon, contentDescription = contentDescription, tint = tint)
	}
}

@Composable
private fun ChipLabel(type: EntryType, name: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Icon(
			imageVector = getEntryTypeIcon(type),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(16.dp),
		)
		Spacer(modifier = Modifier.size(4.dp))
		Text(name)
	}
}

@Composable
fun AdvancedSection(state: SceneMetadataPanel.State) {
	Column(modifier = Modifier.padding(Ui.Padding.L)) {
		Text(
			Res.string.scene_editor_metadata_entity_id.get(),
			style = MaterialTheme.typography.titleMedium,
		)
		SelectionContainer {
			Text(
				state.sceneItem.id.toString(),
				style = MaterialTheme.typography.bodySmall,
			)
		}

		SpacerL()

		Text(
			Res.string.scene_editor_metadata_entity_filename.get(),
			style = MaterialTheme.typography.titleMedium,
		)
		SelectionContainer {
			Text(
				state.filename,
				style = MaterialTheme.typography.bodySmall,
			)
		}

		SpacerL()

		Text(
			Res.string.scene_editor_metadata_entity_path.get(),
			style = MaterialTheme.typography.titleMedium,
		)
		SelectionContainer {
			Text(
				state.path,
				style = MaterialTheme.typography.bodySmall,
			)
		}
	}
}