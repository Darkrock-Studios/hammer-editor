package com.darkrockstudios.apps.hammer.common.projectroot

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdBottomBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRail
import com.darkrockstudios.apps.hammer.common.compose.theme.ProjectThemeOverride
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString

// Locale-independent nav testTags; values match "nav-${DestinationTypes.name}".
const val NAV_HOME_TAG = "nav-Home"
const val NAV_EDITOR_TAG = "nav-Editor"
const val NAV_NOTES_TAG = "nav-Notes"
const val NAV_ENCYCLOPEDIA_TAG = "nav-Encyclopedia"
const val NAV_TIMELINE_TAG = "nav-TimeLine"

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProjectRootScaffold(
	component: ProjectRoot,
	onCloseRequest: () -> Unit,
) {
	val shouldConfirmClose by component.closeRequestHandlers.subscribeAsState()
	val themeState by component.projectTheme.subscribeAsState()
	val rootSnackbar = rememberRootSnackbarHostState()
	val coroutineScope = rememberCoroutineScope()

	ProjectThemeOverride(themeState.theme) {
		val windowSizeClass = calculateWindowSizeClass()
		when (windowSizeClass.widthSizeClass) {
			WindowWidthSizeClass.Compact -> CompactNavigation(component, rootSnackbar)
			WindowWidthSizeClass.Medium,
			WindowWidthSizeClass.Expanded -> RailNavigation(component, rootSnackbar)
		}

		if (shouldConfirmClose.isNotEmpty()) {
			when (shouldConfirmClose.first()) {
				CloseConfirm.Scenes -> ConfirmUnsavedScenesDialog(component, coroutineScope)
				CloseConfirm.Notes -> ConfirmCloseUnsavedNotesDialog(component)
				CloseConfirm.Encyclopedia -> ConfirmCloseUnsavedEncyclopediaDialog(component)
				CloseConfirm.Sync -> component.showProjectSync()
				CloseConfirm.Complete -> onCloseRequest()
			}
		}
	}
}

@Composable
private fun CompactNavigation(
	component: ProjectRoot,
	rootSnackbar: RootSnackbarHostState,
) {
	val router by component.routerState.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		snackbarHost = { SnackbarHost(rootSnackbar.snackbarHostState) },
		content = { scaffoldPadding ->
			ProjectRootUi(
				component,
				rootSnackbar,
				modifier = Modifier.rootElement(scaffoldPadding),
				navWidth = 0.dp,
			)
		},
		bottomBar = {
			val destinations = ProjectRoot.DestinationTypes.entries.map { it.toHdBottomBarDestination() }
			HdBottomBar(
				destinations = destinations,
				selectedId = router.active.instance.getLocationType(),
				onSelect = { component.showDestination(it) },
				itemTestTag = { "nav-${it.name}" },
			)
		},
		floatingActionButton = {
			ProjectRootFab(component, Modifier.fab())
		}
	)
}

@Composable
private fun RailNavigation(
	component: ProjectRoot,
	rootSnackbar: RootSnackbarHostState,
) {
	val router by component.routerState.subscribeAsState()
	val navRailState by component.navRailState.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		snackbarHost = { SnackbarHost(rootSnackbar.snackbarHostState) },
		content = { scaffoldPadding ->
			Row(modifier = Modifier.rootElement(scaffoldPadding)) {
				val density = LocalDensity.current
				var navRailWidth by remember { mutableStateOf<Dp>(0.dp) }

				val destinations = ProjectRoot.DestinationTypes.entries.map { it.toHdNavRailDestination() }
				HdNavRail(
					destinations = destinations,
					selectedId = router.active.instance.getLocationType(),
					onSelect = { component.showDestination(it) },
					expanded = navRailState.expanded,
					onToggleExpanded = { component.toggleNavRailExpanded() },
					itemTestTag = { "nav-${it.name}" },
					modifier = Modifier.onSizeChanged {
						navRailWidth = density.run { it.width.toDp() }
					},
					footer = {
						HdMonoLabel(
							text = getAppVersionString(),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					},
				)

				ProjectRootUi(component, rootSnackbar, navRailWidth, Modifier.padding(scaffoldPadding))
			}
		},
		floatingActionButton = {
			ProjectRootFab(component, Modifier.fab())
		}
	)
}
