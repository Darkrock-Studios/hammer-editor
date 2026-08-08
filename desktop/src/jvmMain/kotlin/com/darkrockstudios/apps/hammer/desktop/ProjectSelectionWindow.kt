package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.account_window_title
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRail
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.darkrockstudios.apps.hammer.common.projectselection.toHdNavRailDestination
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar

@ExperimentalMaterialApi
@ExperimentalComposeApi
@ExperimentalDecomposeApi
@Composable
internal fun NucleusApplicationScope.ProjectSelectionWindow(
	settings: GlobalSettings,
	darkMode: Boolean,
	minimized: Boolean = false,
	onProjectSelected: (projectDef: ProjectDef) -> Unit
) {
	val backDispatcher = BackDispatcher()
	val lifecycle = remember { LifecycleRegistry() }
	val compContext = remember { DefaultComponentContext(lifecycle = lifecycle, backHandler = backDispatcher) }
	val windowState = rememberPersistedWindowState(
		WindowGeometryStore.Window.ProjectSelect,
		defaultSize = coerceWindowSize(900.dp, 800.dp),
		startMinimized = minimized,
	)

	// Restore once the splash is done. One-way on purpose: a later user-initiated
	// minimize must stick.
	LaunchedEffect(minimized) {
		if (!minimized) windowState.isMinimized = false
	}
	val component = remember {
		ProjectSelectionComponent(
			componentContext = compContext,
			onProjectSelected = onProjectSelected,
		)
	}
	LifecycleController(lifecycle, windowState)

	val title = Res.string.account_window_title.get()
	MaterialDecoratedWindow(
		title = title,
		state = windowState,
		onCloseRequest = ::exitApplication,
		icon = painterResource("icon.png"),
		onKeyEvent = { event ->
			when {
				event.key == Key.Escape && event.type == KeyEventType.KeyUp -> {
					backDispatcher.back()
					true
				}

				event.type == KeyEventType.KeyDown &&
					event.key == Key.Q &&
					(event.isCtrlPressed || event.isMetaPressed) -> {
					exitApplication()
					true
				}

				else -> false
			}
		}
	) {
		MaterialTitleBar {
			Text(
				text = title,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.align(Alignment.CenterHorizontally),
			)
		}
		// Tao windows are their own ComposeScene: locals provided outside the
		// window (AppTheme in Main.kt) don't reach this content, so re-apply.
		AppTheme(useDarkTheme = darkMode, settings = settings) {
			Content(component)
		}
	}
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun Content(component: ProjectSelection) {
	val stackState by component.stack.subscribeAsState()
	val navRailState by component.navRailState.subscribeAsState()

	val destinations = ProjectSelection.Locations.entries.map { it.toHdNavRailDestination() }

	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
		content = { innerPadding ->
			Row(
				modifier = Modifier
					.padding(innerPadding)
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background)
			) {
				HdNavRail(
					destinations = destinations,
					selectedId = stackState.active.configuration.location,
					onSelect = { component.showLocation(it) },
					expanded = navRailState.expanded,
					onToggleExpanded = { component.toggleNavRailExpanded() },
					footer = {
						val versionText = remember { getAppVersionString() }
						HdMonoLabel(
							text = versionText,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					},
				)

				ProjectSelectionUi(
					component,
					Modifier.padding(start = Ui.Padding.XL, top = Ui.Padding.XL)
				)
			}
		},
	)
}