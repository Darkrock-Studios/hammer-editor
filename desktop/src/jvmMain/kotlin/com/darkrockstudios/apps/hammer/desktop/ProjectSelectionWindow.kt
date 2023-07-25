package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.lifecycle.LifecycleController
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionFab
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.darkrockstudios.apps.hammer.common.projectselection.getLocationIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@ExperimentalComposeApi
@ExperimentalDecomposeApi
@Composable
internal fun ApplicationScope.ProjectSelectionWindow(
	onProjectSelected: (projectDef: ProjectDef) -> Unit
) {
	val lifecycle = remember { LifecycleRegistry() }
	val compContext = remember { DefaultComponentContext(lifecycle) }
	val windowState = rememberWindowState()
	val component = remember {
		ProjectSelectionComponent(
			componentContext = compContext,
			showProjectDirectory = true,
			onProjectSelected = onProjectSelected
		)
	}
	LifecycleController(lifecycle, windowState)

	Window(
		title = DR.strings.account_window_title.get(),
		state = windowState,
		onCloseRequest = ::exitApplication,
		icon = painterResource("icon.png"),
	) {
		Content(component)
	}
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun Content(component: ProjectSelection) {
	val windowSizeClass = calculateWindowSizeClass()

	when (windowSizeClass.widthSizeClass) {
		WindowWidthSizeClass.Compact, WindowWidthSizeClass.Medium -> {
			MediumNavigation(component)
		}

		WindowWidthSizeClass.Expanded -> {
			ExpandedNavigation(component)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun MediumNavigation(
	component: ProjectSelection
) {
	val slot by component.slot.subscribeAsState()
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
				NavigationRail(modifier = Modifier.padding(top = Ui.Padding.M)) {
					ProjectSelection.Locations.values().forEach { item ->
						NavigationRailItem(
							icon = { Icon(imageVector = getLocationIcon(item), contentDescription = item.text.get()) },
							label = { Text(item.text.get()) },
							selected = item == slot.child?.configuration?.location,
							onClick = { component.showLocation(item) }
						)
					}

					Spacer(modifier = Modifier.weight(1f))

					val versionText = remember { getVersionText() }

					Text(
						versionText,
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Thin,
						modifier = Modifier.align(Alignment.Start).padding(Ui.Padding.L)
					)
				}

				ProjectSelectionUi(component)
			}
		},
		floatingActionButton = {
			ProjectSelectionFab(component)
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun ExpandedNavigation(
	component: ProjectSelection
) {
	val slot by component.slot.subscribeAsState()
	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
		content = { innerPadding ->
			val drawerState = rememberDrawerState(DrawerValue.Closed)
			val scope = rememberCoroutineScope()
			PermanentNavigationDrawer(
				modifier = Modifier.padding(innerPadding),
				drawerContent = {
					PermanentDrawerSheet(modifier = Modifier.width(Ui.NavDrawer.widthExpanded)) {
						NavigationDrawerContents(component, scope, slot, drawerState)
					}
				},
				content = {
					ProjectSelectionUi(component, Modifier)
				}
			)
		},
		floatingActionButton = {
			ProjectSelectionFab(component)
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.NavigationDrawerContents(
	component: ProjectSelection,
	scope: CoroutineScope,
	slot: ChildSlot<ProjectSelection.Config, ProjectSelection.Destination>,
	drawerState: DrawerState,
) {
	Spacer(Modifier.height(12.dp))
	ProjectSelection.Locations.values().forEach { item ->
		NavigationDrawerItem(
			icon = { Icon(getLocationIcon(item), contentDescription = item.text.get()) },
			label = { Text(item.name) },
			selected = item == slot.child?.configuration?.location,
			onClick = {
				scope.launch { drawerState.close() }
				component.showLocation(item)
			},
			modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
		)
	}

	Spacer(modifier = Modifier.weight(1f))

	val versionText = remember { getVersionText() }

	Text(
		versionText,
		modifier = Modifier
			.padding(Ui.Padding.L)
			.align(Alignment.Start),
		style = MaterialTheme.typography.labelSmall,
	)
}

private fun getVersionText() = if (getInDevelopmentMode()) {
	"v${BuildMetadata.APP_VERSION}-dev"
} else {
	"v${BuildMetadata.APP_VERSION}"
}