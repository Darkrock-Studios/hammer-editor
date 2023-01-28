package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui

@Composable
fun DraftCompareUi(component: DraftCompare) {
	val state by component.state.subscribeAsState()

	val sceneText by remember(state.sceneContent) { mutableStateOf(state.sceneContent?.markdown ?: "") }
	val draftText by remember(state.draftContent) { mutableStateOf(state.draftContent?.markdown ?: "") }

	LaunchedEffect(component.sceneItem) {
		component.loadContents()
	}

	Column(modifier = Modifier.fillMaxSize()) {
		if (LocalScreenCharacteristic.current.needsExplicitClose) {
			IconButton(
				onClick = { component.cancel() },
				modifier = Modifier.align(Alignment.End)
			) {
				Icon(
					Icons.Default.Cancel,
					contentDescription = "Cancel",
					tint = MaterialTheme.colorScheme.onBackground
				)
			}
		}

		if (LocalScreenCharacteristic.current.isWide) {
			Row(modifier = Modifier.fillMaxSize()) {
				CurrentContent(
					modifier = Modifier.weight(1f),
					sceneText = sceneText
				)

				DraftContent(
					modifier = Modifier.weight(1f),
					component = component,
					draftText = draftText
				)
			}
		} else {
			Column(modifier = Modifier.fillMaxSize()) {
				CurrentContent(
					modifier = Modifier.weight(1f),
					sceneText = sceneText
				)

				DraftContent(
					modifier = Modifier.weight(1f),
					component = component,
					draftText = draftText
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrentContent(modifier: Modifier, sceneText: String) {
	Card(modifier = modifier.padding(Ui.Padding.L)) {
		Column(modifier = Modifier.padding(Ui.Padding.L)) {
			Text(
				"Current",
				style = MaterialTheme.typography.headlineLarge
			)
			Button(
				onClick = {},
				enabled = false
			) {
				Text("Keep Current")
			}

			TextField(
				value = sceneText,
				onValueChange = {
					//sceneText = it
				},
				modifier = Modifier.fillMaxSize()
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftContent(
	modifier: Modifier,
	component: DraftCompare,
	draftText: String,
) {
	Card(modifier = modifier.padding(Ui.Padding.L)) {
		Column(modifier = Modifier.padding(Ui.Padding.L)) {
			Text(
				"Draft: ${component.draftDef.draftName}",
				style = MaterialTheme.typography.headlineLarge
			)
			Button(onClick = { component.pickDraft() }) {
				Text("Use This One")
			}

			TextField(
				value = draftText,
				onValueChange = {
					//sceneText = it
				},
				modifier = Modifier.fillMaxSize()
			)
		}
	}
}