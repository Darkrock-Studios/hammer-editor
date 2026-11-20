package com.darkrockstudios.apps.hammer.common.storyeditor.drafts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftsList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import com.darkrockstudios.apps.hammer.draft_list_close
import com.darkrockstudios.apps.hammer.draft_list_empty
import com.darkrockstudios.apps.hammer.draft_list_header
import com.darkrockstudios.apps.hammer.draft_list_open

private val DialogMaxWidth = 600.dp
private val DialogBodyMinHeight = 160.dp

@Composable
fun DraftsListUi(
	component: DraftsList,
) {
	val state by component.state.subscribeAsState()

	LaunchedEffect(state.sceneItem) {
		component.loadDrafts()
	}

	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.TopCenter,
	) {
		Surface(
			modifier = Modifier
				.padding(Ui.Padding.XL)
				.widthIn(min = 128.dp, max = DialogMaxWidth)
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
				TitleRow(
					title = Res.string.draft_list_header.get(state.sceneItem.name),
					onClose = component::cancel,
				)
				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = DialogBodyMinHeight),
				) {
					if (state.drafts.isEmpty()) {
						EmptyState()
					} else {
						DraftList(
							drafts = state.drafts,
							onSelect = component::selectDraft,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun TitleRow(title: String, onClose: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = Ui.Padding.XL, end = Ui.Padding.M, top = Ui.Padding.L, bottom = Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleLarge,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)
		IconButton(onClick = onClose) {
			Icon(
				imageVector = Icons.Filled.Close,
				contentDescription = Res.string.draft_list_close.get(),
			)
		}
	}
}

@Composable
private fun DraftList(
	drafts: List<DraftDef>,
	onSelect: (DraftDef) -> Unit,
) {
	LazyColumn(modifier = Modifier.fillMaxWidth()) {
		items(drafts, key = { it.id }) { draft ->
			val isLast = draft.id == drafts.last().id
			DraftRow(
				draftDef = draft,
				onSelect = { onSelect(draft) },
				isLast = isLast,
			)
		}
	}
}

@Composable
private fun DraftRow(
	draftDef: DraftDef,
	onSelect: () -> Unit,
	isLast: Boolean,
) {
	val date = remember(draftDef.draftTimestamp) {
		draftDef.draftTimestamp.formatLocal("dd MMM yy · HH:mm")
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				text = draftDef.draftName,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				HdEntityId(prefix = "DRF", id = draftDef.id, padTo = 3)
				HdMonoLabel(text = date)
			}
		}
		HdHairlineButton(
			label = Res.string.draft_list_open.get(),
			onClick = onSelect,
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
			text = Res.string.draft_list_empty.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
