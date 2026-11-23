package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.archived_scenes_dialog_title
import com.darkrockstudios.apps.hammer.archived_scenes_empty
import com.darkrockstudios.apps.hammer.archived_scenes_restore_button
import com.darkrockstudios.apps.hammer.common.compose.SelfOwnedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogFrame
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDialogTitleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEmptyNote
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntityId
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem


@Composable
internal fun ArchivedScenesDialog(
	archivedScenes: List<SceneItem>,
	onUnarchive: (SceneItem) -> Unit,
	onDismiss: () -> Unit
) {
	SelfOwnedDialog(onClosed = onDismiss) {
		ArchivedScenesCard(
			archivedScenes = archivedScenes,
			onUnarchive = onUnarchive,
			onClose = ::requestDismiss,
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
	HdDialogFrame(modifier = modifier) {
		HdDialogTitleRow(title = Res.string.archived_scenes_dialog_title.get(), onClose = onClose)
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)

		if (archivedScenes.isEmpty()) {
			HdEmptyNote(text = Res.string.archived_scenes_empty.get())
		} else {
			SceneList(scenes = archivedScenes, onUnarchive = onUnarchive)
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
