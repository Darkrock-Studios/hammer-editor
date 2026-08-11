package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.compose.defaultScaffold
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdBottomBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRail
import com.darkrockstudios.apps.hammer.common.compose.rootElement
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import com.darkrockstudios.cairn.CairnAboutOverlay
import com.darkrockstudios.cairn.CairnAppId
import com.darkrockstudios.cairn.CairnConfig

@OptIn(
	ExperimentalMaterial3WindowSizeClassApi::class,
	ExperimentalMaterialApi::class,
	ExperimentalComposeApi::class,
)
@Composable
fun ProjectSelectScaffold(component: ProjectSelection) {
	val windowSizeClass = calculateWindowSizeClass()
	var showStudio by remember { mutableStateOf(false) }

	// The overlay is hoisted above the nav rail/bottom bar so its ceremony
	// covers the whole window, not just the content pane.
	Box(modifier = Modifier.fillMaxSize()) {
		when (windowSizeClass.widthSizeClass) {
			WindowWidthSizeClass.Compact -> CompactNavigation(
				component = component,
				onShowStudio = { showStudio = true },
			)

			WindowWidthSizeClass.Medium,
			WindowWidthSizeClass.Expanded -> RailNavigation(
				component = component,
				onShowStudio = { showStudio = true },
			)
		}

		CairnAboutOverlay(
			visible = showStudio,
			config = CairnConfig(
				currentAppId = CairnAppId.Hammer,
				// Cairn stamps its own "v", ours already carries one.
				versionName = getAppVersionString().removePrefix("v"),
			),
			onDismissed = { showStudio = false },
		)
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun CompactNavigation(component: ProjectSelection, onShowStudio: () -> Unit) {
	val stackState by component.stack.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			ProjectSelectionUi(
				component,
				onShowStudio = onShowStudio,
				modifier = Modifier.rootElement(scaffoldPadding),
			)
		},
		bottomBar = {
			val destinations = ProjectSelection.Locations.entries.map { it.toHdBottomBarDestination() }
			HdBottomBar(
				destinations = destinations,
				selectedId = stackState.active.configuration.location,
				onSelect = { component.showLocation(it) },
			)
		},
	)
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun RailNavigation(component: ProjectSelection, onShowStudio: () -> Unit) {
	val stackState by component.stack.subscribeAsState()
	val navRailState by component.navRailState.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			Row(modifier = Modifier.rootElement(scaffoldPadding)) {
				val destinations = ProjectSelection.Locations.entries.map { it.toHdNavRailDestination() }
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

				ProjectSelectionUi(component, onShowStudio = onShowStudio)
			}
		},
	)
}
