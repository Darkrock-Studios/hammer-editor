package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

@Composable
internal fun SceneConflict(
	entityConflict: ProjectSync.EntityConflict.SceneConflict,
	component: ProjectSync
) {
	val screenCharacteristics = LocalScreenCharacteristic.current

	Column(modifier = Modifier.fillMaxSize()) {
		Spacer(modifier = Modifier.size(Ui.Padding.L))

		when (screenCharacteristics.windowWidthClass) {
			WindowWidthSizeClass.Compact -> {
				Compact(Modifier.fillMaxSize(), entityConflict, component)
			}

			else -> {
				Expanded(Modifier.fillMaxSize(), entityConflict, component)
			}
		}
	}
}

@Composable
private fun Compact(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict.SceneConflict,
	component: ProjectSync
) {
	var tabState by rememberSaveable { mutableStateOf(0) }
	val titles = remember {
		listOf(MR.strings.sync_conflict_tab_local, MR.strings.sync_conflict_tab_remote)
	}

	Column(modifier = modifier) {
		TabRow(selectedTabIndex = tabState) {
			titles.forEachIndexed { index, title ->
				Tab(
					text = { Text(title.get()) },
					selected = tabState == index,
					onClick = { tabState = index }
				)
			}
		}

		if (tabState == 0) {
			LocalScene(
				modifier = Modifier.weight(1f),
				entityConflict = entityConflict,
				component = component
			)
		} else if (tabState == 1) {
			RemoteScene(
				modifier = Modifier.weight(1f),
				entityConflict = entityConflict,
				component = component
			)
		}
	}
}

@Composable
private fun Expanded(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict.SceneConflict,
	component: ProjectSync
) {
	Row(modifier = modifier) {
		LocalScene(
			modifier = Modifier.weight(1f),
			entityConflict = entityConflict,
			component = component
		)

		RemoteScene(
			modifier = Modifier.weight(1f),
			entityConflict = entityConflict,
			component = component
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalScene(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict.SceneConflict,
	component: ProjectSync
) {
	Column(modifier = modifier.padding(Ui.Padding.M)) {
		FlowRow(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = "Local Scene:",
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = { component.resolveConflict(entityConflict.clientEntity) }) {
				Text("Use Local")
			}
		}
		SelectionContainer {
			Text(
				entityConflict.clientEntity.name,
				style = MaterialTheme.typography.bodyLarge
			)
		}
		SelectionContainer {
			Text(
				entityConflict.clientEntity.content,
				modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteScene(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict.SceneConflict,
	component: ProjectSync
) {
	Column(modifier = modifier.padding(Ui.Padding.L)) {
		FlowRow(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = "Remote Scene:",
				style = MaterialTheme.typography.headlineSmall
			)
			Button(onClick = { component.resolveConflict(entityConflict.serverEntity) }) {
				Text("Use Remote")
			}
		}
		SelectionContainer {
			Text(
				entityConflict.serverEntity.name,
				style = MaterialTheme.typography.bodyLarge
			)
		}
		SelectionContainer {
			Text(
				entityConflict.serverEntity.content,
				modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
			)
		}
	}
}