package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

val SCENE_METADATA_MIN_WIDTH = 256.dp
@Composable
fun SceneMetadataUi(
	component: SceneMetadata,
	modifier: Modifier = Modifier,
	closeMetadata: () -> Unit
) {
	val state by component.state.subscribeAsState()

	Card(modifier = modifier.widthIn(min = SCENE_METADATA_MIN_WIDTH),
		elevation = CardDefaults.cardElevation(Ui.ToneElevation.MEDIUM)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.XL)) {
			Row {
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

			Spacer(modifier = Modifier.size(Ui.Padding.L))

			Row {
				Text(
					MR.strings.scene_editor_metadata_word_count_label.get(),
					style = MaterialTheme.typography.labelLarge,
				)

				Text(
					"${state.wordCount}",
					style = MaterialTheme.typography.labelLarge,
				)
			}

			Spacer(modifier = Modifier.size(Ui.Padding.XL))

			Text(
				MR.strings.scene_editor_metadata_advanced_header.get(),
				style = MaterialTheme.typography.titleSmall,
			)

			Text(
				MR.strings.scene_editor_metadata_entity_id.get(state.sceneItem.id),
				style = MaterialTheme.typography.bodySmall,
			)
		}
	}
}