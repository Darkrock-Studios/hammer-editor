package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSync
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get

typealias EntityUi<T> = @Composable (
	modifier: Modifier,
	entityConflict: ProjectSync.EntityConflict<T>,
	component: ProjectSync
) -> Unit

@Composable
fun <T : ApiProjectEntity> EntityConflict(
	entityConflict: ProjectSync.EntityConflict<T>,
	component: ProjectSync,
	screenCharacteristics: WindowSizeClass,
	LocalEntity: EntityUi<T>,
	RemoteEntity: EntityUi<T>,
) {
	Column(modifier = Modifier.fillMaxSize()) {
		Spacer(modifier = Modifier.size(Ui.Padding.L))

		when (screenCharacteristics.widthSizeClass) {
			WindowWidthSizeClass.Compact -> {
				CompactConflictUi(Modifier.fillMaxSize(), entityConflict, component, LocalEntity, RemoteEntity)
			}

			else -> {
				ExpandedConflictUi(Modifier.fillMaxSize(), entityConflict, component, LocalEntity, RemoteEntity)
			}
		}
	}
}

@Composable
private fun <T : ApiProjectEntity> CompactConflictUi(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict<T>,
	component: ProjectSync,
	LocalEntity: EntityUi<T>,
	RemoteEntity: EntityUi<T>,
) {
	var tabState by rememberSaveable { mutableStateOf(0) }
	val titles = remember {
		listOf(MR.strings.sync_conflict_tab_remote, MR.strings.sync_conflict_tab_local)
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
			RemoteEntity(
				modifier = Modifier.weight(1f),
				entityConflict = entityConflict,
				component = component
			)
		} else if (tabState == 1) {
			LocalEntity(
				modifier = Modifier.weight(1f),
				entityConflict = entityConflict,
				component = component
			)
		}
	}
}

@Composable
private fun <T : ApiProjectEntity> ExpandedConflictUi(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSync.EntityConflict<T>,
	component: ProjectSync,
	LocalEntity: EntityUi<T>,
	RemoteEntity: EntityUi<T>,
) {
	Row(modifier = modifier) {
		RemoteEntity(
			modifier = Modifier.weight(1f),
			entityConflict = entityConflict,
			component = component
		)

		LocalEntity(
			modifier = Modifier.weight(1f),
			entityConflict = entityConflict,
			component = component
		)
	}
}