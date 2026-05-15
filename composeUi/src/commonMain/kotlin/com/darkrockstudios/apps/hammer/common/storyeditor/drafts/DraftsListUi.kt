package com.darkrockstudios.apps.hammer.common.storyeditor.drafts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import com.darkrockstudios.apps.hammer.draft_list_empty
import com.darkrockstudios.apps.hammer.draft_list_header

private val DialogMaxWidth = 600.dp
private val DialogBodyMinHeight = 280.dp

@Composable
fun DraftsListUi(
	component: DraftsList,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()

	var headerText by remember { mutableStateOf("") }
	LaunchedEffect(state.sceneItem) {
		component.loadDrafts()
		headerText = strRes.get(Res.string.draft_list_header, state.sceneItem.name)
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
				Masthead(count = state.drafts.size, onClose = component::cancel)
				HdFolioDivider()

				TitleRow(headerText = headerText)
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

				FooterBar()
			}
		}
	}
}

@Composable
private fun Masthead(count: Int, onClose: () -> Unit) {
	val meta = when (count) {
		0 -> "EMPTY"
		1 -> "1 DRAFT"
		else -> "$count DRAFTS"
	}
	HdMasthead(
		section = "DRAFTS",
		leadingMeta = listOf(meta),
		trailing = { HdMastheadAction(label = "× CLOSE", onClick = onClose) },
	)
}

@Composable
private fun TitleRow(headerText: String) {
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
			text = headerText,
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
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
			label = "OPEN",
			emphasised = true,
			onClick = onSelect,
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
			text = "DRAFTS · EMPTY",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = Res.string.draft_list_empty.get(),
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
