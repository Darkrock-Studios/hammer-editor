package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.archived_scenes_dialog_title
import com.darkrockstudios.apps.hammer.archived_scenes_empty
import com.darkrockstudios.apps.hammer.archived_scenes_restore_button
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun ArchivedScenesDialog(
	archivedScenes: List<SceneItem>,
	onUnarchive: (SceneItem) -> Unit,
	onDismiss: () -> Unit
) {
	SimpleDialog(
		onCloseRequest = onDismiss,
		visible = true,
		title = Res.string.archived_scenes_dialog_title.get()
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = Ui.Padding.XL, max = Ui.Padding.XL * 20)
				.padding(Ui.Padding.L)
		) {
			if (archivedScenes.isEmpty()) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = Ui.Padding.XL * 2),
					contentAlignment = Alignment.Center
				) {
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)
					) {
						Icon(
							Icons.Default.Archive,
							contentDescription = null,
							modifier = Modifier.size(48.dp),
							tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
						)
						Text(
							Res.string.archived_scenes_empty.get(),
							style = MaterialTheme.typography.bodyLarge,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			} else {
				Text(
					"${archivedScenes.size} archived scene${if (archivedScenes.size != 1) "s" else ""}",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(bottom = Ui.Padding.M)
				)
				LazyColumn(
					modifier = Modifier.fillMaxWidth(),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)
				) {
					items(archivedScenes, key = { it.id }) { scene ->
						ArchivedSceneItem(
							scene = scene,
							onUnarchive = { onUnarchive(scene) }
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ArchivedSceneItem(
	scene: SceneItem,
	onUnarchive: () -> Unit
) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
		)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(Ui.Padding.L),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				Icons.Default.Description,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(24.dp)
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				Text(
					scene.name,
					style = MaterialTheme.typography.bodyLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					"ID: ${scene.id}",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			FilledTonalIconButton(onClick = onUnarchive) {
				Icon(
					Icons.Default.Unarchive,
					contentDescription = Res.string.archived_scenes_restore_button.get()
				)
			}
		}
	}
}
