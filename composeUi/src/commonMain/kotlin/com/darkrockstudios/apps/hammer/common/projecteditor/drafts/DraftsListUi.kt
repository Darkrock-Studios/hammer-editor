package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecteditor.drafts.DraftsList
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.util.formatLocal

@Composable
fun DraftsListUi(
	component: DraftsList,
) {
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()

	LaunchedEffect(state.sceneItem) {
		component.loadDrafts()
	}

	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.TopCenter
	) {
		Card(modifier = Modifier.padding(Ui.Padding.XL).widthIn(128.dp, 600.dp)) {
			Column {
				Column(modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth()) {
					if (LocalScreenCharacteristic.current.needsExplicitClose) {
						IconButton(
							onClick = { component.cancel() },
							modifier = Modifier.align(Alignment.End)
						) {
							Icon(Icons.Default.Close, MR.strings.draft_list_close_button.get())
						}
					}

					Text(
						strRes.get(MR.strings.draft_list_header, state.sceneItem.name),
						style = MaterialTheme.typography.headlineLarge,
						color = MaterialTheme.colorScheme.onSurface
					)
				}

				LazyColumn(
					modifier = Modifier.fillMaxWidth(),
					contentPadding = PaddingValues(Ui.Padding.XL)
				) {
					state.apply {
						if (drafts.isEmpty()) {
							item {
								Text(MR.strings.draft_list_empty.get())
							}
						} else {
							items(drafts.size) { index ->
								DraftItem(draftDef = drafts[index], onDraftSelected = component::selectDraft)
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
	Card(
		modifier = modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = { onDraftSelected(draftDef) },
			)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.XL)) {
			Text(
				draftDef.draftName,
				style = MaterialTheme.typography.titleLarge
			)

			val date = remember(draftDef.draftTimestamp) {
				draftDef.draftTimestamp.formatLocal("dd MMM `yy")
			}
			Text(
				MR.strings.draft_list_item_created.get(date),
				style = MaterialTheme.typography.bodySmall
			)
		}
	}
}