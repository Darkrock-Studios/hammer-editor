package com.darkrockstudios.apps.hammer.common.storyeditor.drafts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts.DraftsList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import com.darkrockstudios.apps.hammer.draft_list_close_button
import com.darkrockstudios.apps.hammer.draft_list_empty
import com.darkrockstudios.apps.hammer.draft_list_header

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
		contentAlignment = Alignment.TopCenter
	) {
		Card(
			modifier = Modifier
				.padding(Ui.Padding.XL)
				.widthIn(min = 128.dp, max = 600.dp)
		) {
			Column {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = headerText,
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.weight(1f)
					)
					IconButton(
						onClick = { component.cancel() }
					) {
						Icon(Icons.Default.Close, Res.string.draft_list_close_button.get())
					}
				}

				HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

				LazyColumn(
					modifier = Modifier.fillMaxWidth(),
					contentPadding = PaddingValues(bottom = Ui.Padding.XL)
				) {
					state.apply {
						if (drafts.isEmpty()) {
							item {
								Box(modifier = Modifier.padding(Ui.Padding.XL)) {
									Text(
										text = Res.string.draft_list_empty.get(),
										style = MaterialTheme.typography.bodyLarge,
										color = MaterialTheme.colorScheme.onSurfaceVariant
									)
								}
							}
						} else {
							items(drafts.size) { index ->
								DraftItem(
									draftDef = drafts[index],
									onDraftSelected = component::selectDraft
								)
								if (index < drafts.size - 1) {
									HorizontalDivider(
										modifier = Modifier.padding(start = 56.dp),
										thickness = Dp.Hairline,
										color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
									)
								}
							}
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraftItem(
	draftDef: DraftDef,
	modifier: Modifier = Modifier,
	onDraftSelected: (DraftDef) -> Unit
) {
	val date = remember(draftDef.draftTimestamp) {
		draftDef.draftTimestamp.formatLocal("dd MMM `yy - HH:mm")
	}

	ListItem(
		modifier = modifier
			.fillMaxWidth()
			.clickable { onDraftSelected(draftDef) },
		headlineContent = {
			Text(
				text = draftDef.draftName,
				style = MaterialTheme.typography.titleMedium
			)
		},
		supportingContent = {
			Text(
				text = date,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		},
		leadingContent = {
			Icon(
				imageVector = Icons.Outlined.Description,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary
			)
		},
		trailingContent = {
			Icon(
				imageVector = Icons.Default.ChevronRight,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		},
		colors = ListItemDefaults.colors(
			containerColor = Color.Transparent
		)
	)
}