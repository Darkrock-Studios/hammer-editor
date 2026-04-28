package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.CollapsableSection
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.encyclopedia.EntryRefChipLabel

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
					modifier = Modifier.padding(end = Ui.Padding.M),
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
		if (state.confirmedRefs.isEmpty() && state.dismissedRefs.isEmpty()) {
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
						label = { EntryRefChipLabel(type = ref.type, name = ref.name) },
						trailingIcon = {
							ChipAction(
								onClick = { component.dismissReference(ref.id) },
								icon = Icons.Filled.Close,
								contentDescription = Res.string.scene_editor_metadata_references_dismiss.get(),
								tint = MaterialTheme.colorScheme.onSurface,
							)
						},
						selected = true,
					)
				}
			}
		}

		SpacerL()
		AddReferenceField(component = component)

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
								label = { EntryRefChipLabel(type = ref.type, name = ref.name) },
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
private fun AddReferenceField(component: SceneMetadataPanel) {
	var query by rememberSaveable { mutableStateOf("") }
	var hasFocus by remember { mutableStateOf(false) }
	var fieldSize by remember { mutableStateOf(IntSize.Zero) }
	val density = LocalDensity.current
	val suggestions by remember(query) {
		derivedStateOf { component.searchEntriesForAdd(query) }
	}
	val dropdownOpen = hasFocus && query.isNotBlank()

	fun pick(suggestion: SceneMetadataPanel.AddSuggestion) {
		component.addConfirmedReference(suggestion.entryDef.id)
		query = ""
	}

	// Non-focusable Popup (not ExposedDropdownMenuBox) so the field keeps
	// focus and continues receiving keystrokes while suggestions update.
	Box(modifier = Modifier.fillMaxWidth()) {
		OutlinedTextField(
			value = query,
			onValueChange = { query = it },
			modifier = Modifier
				.fillMaxWidth()
				.onSizeChanged { fieldSize = it }
				.onFocusChanged { hasFocus = it.isFocused },
			singleLine = true,
			placeholder = { Text(Res.string.scene_editor_metadata_references_add_placeholder.get()) },
			leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
			trailingIcon = if (query.isNotEmpty()) {
				{
					IconButton(onClick = { query = "" }) {
						Icon(Icons.Filled.Close, contentDescription = null)
					}
				}
			} else null,
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
			keyboardActions = KeyboardActions(onDone = {
				suggestions.firstOrNull()?.let(::pick)
			}),
		)
		if (dropdownOpen) {
			val fieldWidthDp = with(density) { fieldSize.width.toDp() }
			Popup(
				alignment = Alignment.TopStart,
				offset = IntOffset(0, fieldSize.height),
				properties = PopupProperties(focusable = false),
				onDismissRequest = {},
			) {
				Surface(
					modifier = Modifier.width(fieldWidthDp),
					shadowElevation = 8.dp,
					tonalElevation = Ui.ToneElevation.MEDIUM,
					shape = MaterialTheme.shapes.medium,
				) {
					Column {
						if (suggestions.isEmpty()) {
							Text(
								text = Res.string.scene_editor_metadata_references_add_no_matches.get(),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.padding(Ui.Padding.L),
							)
						} else {
							suggestions.forEach { suggestion ->
								DropdownMenuItem(
									text = {
										Row(verticalAlignment = Alignment.CenterVertically) {
											EntryRefChipLabel(
												type = suggestion.entryDef.type,
												name = suggestion.entryDef.name,
											)
											if (suggestion.isDismissed) {
												Spacer(Modifier.size(8.dp))
												Text(
													Res.string.scene_editor_metadata_references_add_dismissed_hint.get(),
													style = MaterialTheme.typography.labelSmall,
													color = MaterialTheme.colorScheme.onSurfaceVariant,
												)
											}
										}
									},
									onClick = { pick(suggestion) },
								)
							}
						}
					}
				}
			}
		}
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