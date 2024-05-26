package com.darkrockstudios.apps.hammer.common.storyeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview.OutlineOverview
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
fun OutlineOverviewUi(component: OutlineOverview) {
	Dialog(onDismissRequest = component::dismiss) {
		val state by component.state.subscribeAsState()

		Card(
			modifier = Modifier.widthIn(min = 512.dp),
			elevation = CardDefaults.cardElevation(Ui.Elevation.SMALL),
		) {
			Column(modifier = Modifier.padding(Ui.Padding.XL)) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Text(
						MR.strings.scene_list_outline_overview_title.get(),
						style = MaterialTheme.typography.displaySmall
					)

					IconButton(
						onClick = component::dismiss,
					) {
						Icon(
							Icons.Default.Close,
							contentDescription = MR.strings.scene_list_outline_dismiss.get(),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
				}

				LazyColumn {
					items(state.overview.size) { index ->
						val sceneOutline = state.overview[index]
						when (sceneOutline) {
							is OutlineOverview.OutlineItem.ChapterOutline -> {
								ChapterOutlineUi(sceneOutline)
							}

							is OutlineOverview.OutlineItem.SceneOutline -> {
								SceneOutlineUi(sceneOutline)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ChapterOutlineUi(sceneOutline: OutlineOverview.OutlineItem.ChapterOutline) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			sceneOutline.sceneItem.name,
			style = MaterialTheme.typography.headlineSmall,
		)

		Divider(
			modifier = Modifier.fillMaxWidth(),
			color = MaterialTheme.colorScheme.outline,
		)
	}
}

@Composable
private fun SceneOutlineUi(sceneOutline: OutlineOverview.OutlineItem.SceneOutline) {
	Column(
		modifier = Modifier.padding(
			start = Ui.Padding.XL,
			top = Ui.Padding.S,
			end = Ui.Padding.S,
		)
	) {
		Text(
			sceneOutline.sceneItem.name,
			style = MaterialTheme.typography.labelLarge
		)

		val outline = remember(sceneOutline.outline) { sceneOutline.outline }
		if (outline?.isNotBlank() == true) {
			Text(
				outline,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(Ui.Padding.L)
			)
		} else {
			Text(
				MR.strings.scene_list_outline_overview_none.get(),
				style = MaterialTheme.typography.bodyMedium,
				fontStyle = FontStyle.Italic,
				modifier = Modifier.padding(Ui.Padding.L),
			)
		}
	}
}
