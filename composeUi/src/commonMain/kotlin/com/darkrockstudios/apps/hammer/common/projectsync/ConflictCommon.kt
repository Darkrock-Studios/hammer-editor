package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.sync_conflict_tab_local
import com.darkrockstudios.apps.hammer.sync_conflict_tab_remote

typealias EntityUi<T> = @Composable (
	modifier: Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<T>,
	component: ProjectSynchronization
) -> Unit

@Composable
fun <T : ApiProjectEntity> EntityConflict(
	entityConflict: ProjectSynchronization.EntityConflict<T>,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass,
	LocalEntity: EntityUi<T>,
	RemoteEntity: EntityUi<T>,
) {
	when (screenCharacteristics.widthSizeClass) {
		WindowWidthSizeClass.Compact -> {
			CompactConflictUi(Modifier.fillMaxSize(), entityConflict, component, LocalEntity, RemoteEntity)
		}

		else -> {
			ExpandedConflictUi(Modifier.fillMaxSize(), entityConflict, component, LocalEntity, RemoteEntity)
		}
	}
}

@Composable
private fun <T : ApiProjectEntity> CompactConflictUi(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<T>,
	component: ProjectSynchronization,
	LocalEntity: EntityUi<T>,
	RemoteEntity: EntityUi<T>,
) {
	var tabState by rememberSaveable { mutableStateOf(0) }
	val titles = remember {
		listOf(Res.string.sync_conflict_tab_remote, Res.string.sync_conflict_tab_local)
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
				Modifier.weight(1f),
				entityConflict,
				component
			)
		} else if (tabState == 1) {
			LocalEntity(
				Modifier.weight(1f),
				entityConflict,
				component
			)
		}
	}
}

@Composable
private fun <T : ApiProjectEntity> ExpandedConflictUi(
	modifier: Modifier = Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<T>,
	component: ProjectSynchronization,
	LocalEntity: EntityUi<T>,
	RemoteEntity: EntityUi<T>,
) {
	Row(modifier = modifier) {
		RemoteEntity(
			Modifier.weight(1f),
			entityConflict,
			component
		)

		VerticalDivider(modifier = Modifier.fillMaxHeight())

		LocalEntity(
			Modifier.weight(1f),
			entityConflict,
			component
		)
	}
}