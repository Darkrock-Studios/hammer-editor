package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import com.darkrockstudios.apps.hammer.archived_scenes_dialog_title
import com.darkrockstudios.apps.hammer.archived_scenes_empty
import com.darkrockstudios.apps.hammer.archived_scenes_restore_button
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntityId
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem

private val DialogMaxWidth = 580.dp
private val DialogBodyMinHeight = 280.dp

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
		Surface(
			modifier = Modifier
				.padding(Ui.Padding.M)
				.widthIn(max = DialogMaxWidth)
				.fillMaxWidth()
				.predictiveBackTransform(),
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			border = BorderStroke(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			),
		) {
			Column {
				Masthead(count = archivedScenes.size, onClose = requestClose)
				HdFolioDivider()

				TitleRow()
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

				FooterBar()
			}
		}
	}
}

@Composable
private fun Masthead(count: Int, onClose: () -> Unit) {
	val meta = if (count == 0) "EMPTY" else "$count ARCHIVED"
	HdMasthead(
		section = "ARCHIVE",
		leadingMeta = listOf(meta),
		trailing = { HdMastheadAction(label = "× CLOSE", onClick = onClose) },
	)
}

@Composable
private fun TitleRow() {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				start = Ui.Padding.XL,
				end = Ui.Padding.XL,
				top = Ui.Padding.L,
				bottom = Ui.Padding.M,
			),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = Res.string.archived_scenes_dialog_title.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun SceneList(
	scenes: List<SceneItem>,
	onUnarchive: (SceneItem) -> Unit,
) {
	LazyColumn(modifier = Modifier.fillMaxWidth()) {
		items(scenes, key = { it.id }) { scene ->
			val isLast = scene.id == scenes.last().id
			SceneRow(
				scene = scene,
				onUnarchive = { onUnarchive(scene) },
				isLast = isLast,
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
			.padding(
				start = Ui.Padding.XL,
				end = Ui.Padding.XL,
				top = Ui.Padding.L,
				bottom = Ui.Padding.L,
			),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				text = scene.name,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			HdEntityId(prefix = "SCN", id = scene.id, padTo = 4)
		}
		HdHairlineButton(
			label = Res.string.archived_scenes_restore_button.get(),
			emphasised = true,
			onClick = onUnarchive,
		)
	}
	if (!isLast) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
}

@Composable
private fun EmptyState() {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(Ui.Padding.XL * 2),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.CenterVertically),
	) {
		HdMonoLabel(
			text = "ARCHIVE · EMPTY",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = Res.string.archived_scenes_empty.get(),
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun FooterBar() {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(
			text = "ESC CLOSE",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
