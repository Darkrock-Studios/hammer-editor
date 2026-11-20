package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.archived_scenes_close
import com.darkrockstudios.apps.hammer.archived_scenes_dialog_title
import com.darkrockstudios.apps.hammer.archived_scenes_empty
import com.darkrockstudios.apps.hammer.archived_scenes_restore_button
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntityId
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem

private val DialogMaxWidth = 580.dp
private val DialogBodyMinHeight = 160.dp

@Composable
internal fun ArchivedScenesDialog(
	archivedScenes: List<SceneItem>,
	onUnarchive: (SceneItem) -> Unit,
	onDismiss: () -> Unit
) {
	var isOpen by remember { mutableStateOf(true) }
	val requestClose = { isOpen = false }

	AnimatedDialogContainer(
		isOpen = isOpen,
		onDismissRequest = requestClose,
		onClosed = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		ArchivedScenesCard(
			archivedScenes = archivedScenes,
			onUnarchive = onUnarchive,
			onClose = requestClose,
			modifier = Modifier.predictiveBackTransform(),
		)
	}
}

@Composable
internal fun ArchivedScenesCard(
	archivedScenes: List<SceneItem>,
	onUnarchive: (SceneItem) -> Unit,
	onClose: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		modifier = modifier
			.padding(Ui.Padding.M)
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(
			width = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		),
	) {
		Column {
			TitleRow(onClose = onClose)
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = DialogBodyMinHeight),
			) {
				if (archivedScenes.isEmpty()) {
					EmptyState()
				} else {
					SceneList(scenes = archivedScenes, onUnarchive = onUnarchive)
				}
			}
		}
	}
}

@Composable
private fun TitleRow(onClose: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = Ui.Padding.XL, end = Ui.Padding.M, top = Ui.Padding.L, bottom = Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = Res.string.archived_scenes_dialog_title.get(),
			style = MaterialTheme.typography.titleLarge,
			modifier = Modifier.weight(1f),
		)
		IconButton(onClick = onClose) {
			Icon(
				imageVector = Icons.Filled.Close,
				contentDescription = Res.string.archived_scenes_close.get(),
			)
		}
	}
}

@Composable
private fun SceneList(
	scenes: List<SceneItem>,
	onUnarchive: (SceneItem) -> Unit,
) {
	LazyColumn(
		modifier = Modifier.fillMaxWidth(),
		contentPadding = PaddingValues(vertical = Ui.Padding.S),
	) {
		items(scenes, key = { it.id }) { scene ->
			SceneRow(
				scene = scene,
				onUnarchive = { onUnarchive(scene) },
				isLast = scene.id == scenes.last().id,
			)
		}
	}
}

@Composable
private fun SceneRow(
	scene: SceneItem,
	onUnarchive: () -> Unit,
	isLast: Boolean,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Row(
			modifier = Modifier.weight(1f),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			Text(
				text = scene.name,
				style = MaterialTheme.typography.bodyLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f, fill = false),
			)
			HdEntityId(prefix = "SCN", id = scene.id, padTo = 4)
		}
		HdHairlineButton(
			label = Res.string.archived_scenes_restore_button.get(),
			onClick = onUnarchive,
		)
	}
	if (!isLast) {
		HorizontalDivider(
			modifier = Modifier.padding(horizontal = Ui.Padding.XL),
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
}

@Composable
private fun EmptyState() {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = DialogBodyMinHeight)
			.padding(Ui.Padding.XL),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = Res.string.archived_scenes_empty.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
