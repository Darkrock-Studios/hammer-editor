package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.CollapsableSection
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

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
						contentDescription = MR.strings.scene_editor_metadata_hide_button.get(),
						tint = MaterialTheme.colorScheme.onBackground
					)
				}

				Text(
					text = MR.strings.scene_editor_metadata_title.get(),
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
					MR.strings.scene_editor_metadata_word_count_label.get(),
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
				label = { Text(MR.strings.scene_editor_metadata_outline_label.get()) },
				placeholder = {
					Text(
						MR.strings.scene_editor_metadata_outline_placeholder.get(),
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
				label = { Text(MR.strings.scene_editor_metadata_notes_label.get()) },
				placeholder = {
					Text(
						MR.strings.scene_editor_metadata_notes_placeholder.get(),
						style = MaterialTheme.typography.bodyLarge,
					)
				},
				textStyle = MaterialTheme.typography.bodyLarge,
			)

			SpacerXL()

			CollapsableSection(
				header = {
					Text(
						MR.strings.scene_editor_metadata_advanced_header.get(),
						style = MaterialTheme.typography.titleMedium,
					)
				},
				body = { AdvancedSection(state) }
			)
		}
	}
}

@Composable
fun AdvancedSection(state: SceneMetadataPanel.State) {
	Column(modifier = Modifier.padding(Ui.Padding.L)) {
		Text(
			MR.strings.scene_editor_metadata_entity_id.get(),
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
			MR.strings.scene_editor_metadata_entity_filename.get(),
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
			MR.strings.scene_editor_metadata_entity_path.get(),
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