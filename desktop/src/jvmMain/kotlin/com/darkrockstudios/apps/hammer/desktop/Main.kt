package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.lifecycle.LifecycleController
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootUi
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.darkrockstudios.apps.hammer.common.projectselection.getLocationIcon
import com.jthemedetecor.OsThemeDetector
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.get
import javax.swing.UIManager

@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@ExperimentalComposeApi
fun main() {
	Napier.base(DebugAntilog())

	UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

	GlobalContext.startKoin {
		logger(NapierLogger())
		modules(mainModule, imageLoadingModule)
	}

	val osThemeDetector = OsThemeDetector.getDetector()
	application {
		val applicationState = remember { ApplicationState() }
		val imageLoader: ImageLoader = get(ImageLoader::class.java)

		var darkMode by remember { mutableStateOf(osThemeDetector.isDark) }
		osThemeDetector.registerListener { isDarkModeEnabled ->
			darkMode = isDarkModeEnabled
		}

		AppTheme(useDarkTheme = darkMode) {
			CompositionLocalProvider(
				LocalImageLoader provides imageLoader,
			) {
				when (val windowState = applicationState.windows.value) {
					is WindowState.ProjectSectionWindow -> {
						ProjectSelectionWindow { project ->
							applicationState.openProject(project)
						}
					}

					is WindowState.ProjectWindow -> {
						ProjectEditorWindow(applicationState, windowState.projectDef)
					}
				}
			}
		}
	}
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@ExperimentalDecomposeApi
@Composable
private fun ApplicationScope.ProjectSelectionWindow(
	onProjectSelected: (projectDef: ProjectDef) -> Unit
) {
	val lifecycle = remember { LifecycleRegistry() }
	val compContext = remember { DefaultComponentContext(lifecycle) }
	val windowState = rememberWindowState()
	val component = remember(onProjectSelected) {
		ProjectSelectionComponent(
			componentContext = compContext,
			showProjectDirectory = true,
			onProjectSelected = onProjectSelected
		)
	}
	LifecycleController(lifecycle, windowState)

	Window(
		title = "Project Selection",
		state = windowState,
		onCloseRequest = ::exitApplication,
	) {
		val state by component.state.subscribeAsState()

		Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
			NavigationRail(modifier = Modifier.padding(top = Ui.Padding.M)) {
				ProjectSelection.Locations.values().forEach { item ->
					NavigationRailItem(
						icon = { Icon(imageVector = getLocationIcon(item), contentDescription = item.text) },
						label = { Text(item.text) },
						selected = state.location == item,
						onClick = { component.showLocation(item) }
					)
				}
			}

			ProjectSelectionUi(component)
		}
	}
}

@ExperimentalComposeApi
@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@Composable
private fun ApplicationScope.ProjectEditorWindow(
	app: ApplicationState,
	projectDef: ProjectDef
) {
	val lifecycle = remember { LifecycleRegistry() }
	val compContext = remember { DefaultComponentContext(lifecycle) }
	val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
	LifecycleController(lifecycle, windowState)

	val closeDialog = app.shouldShowConfirmClose.subscribeAsState()

	val component = remember<ProjectRoot> {
		ProjectRootComponent(
			componentContext = compContext,
			projectDef = projectDef,
			addMenu = { menu ->
				app.addMenu(menu)
			},
			removeMenu = { menuId ->
				app.removeMenu(menuId)
			}
		)
	}

	val menu by app.menu.subscribeAsState()

	Window(
		title = "Hammer - ${projectDef.name}",
		state = windowState,
		onCloseRequest = { onRequestClose(component, app, ApplicationState.CloseType.Application) }
	) {
		Column {
			MenuBar {
				Menu("File") {
					Item("Close Project", onClick = {
						onRequestClose(component, app, ApplicationState.CloseType.Project)
					})
					Item("Exit", onClick = {
						onRequestClose(component, app, ApplicationState.CloseType.Application)
					})
				}

				menu.forEach { menuDescriptor ->
					Menu(menuDescriptor.label) {
						menuDescriptor.items.forEach { itemDescriptor ->
							Item(
								itemDescriptor.label,
								onClick = { itemDescriptor.action(itemDescriptor.id) },
								shortcut = itemDescriptor.shortcut?.toDesktopShortcut()
							)
						}
					}
				}
			}
			Surface(modifier = Modifier.fillMaxSize()) {
				ProjectRootUi(component)
			}
		}

		if (closeDialog.value != ApplicationState.CloseType.None) {
			confirmCloseDialog(closeDialog.value) { result, closeType ->
				if (result == ConfirmCloseResult.SaveAll) {
					component.storeDirtyBuffers()
				}

				app.dismissConfirmProjectClose()

				if (result != ConfirmCloseResult.Cancel) {
					performClose(app, closeType)
				}
			}
		}
	}
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
private fun confirmCloseDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	AlertDialog(
		title = { Text("Unsaved Scenes") },
		text = { Text("Save unsaved scenes?") },
		onDismissRequest = { /* Noop */ },
		buttons = {
			Column(
				modifier = Modifier.fillMaxWidth(),
			) {
				Button(onClick = { dismissDialog(ConfirmCloseResult.SaveAll, closeType) }) {
					Text("Save and close")
				}
				Button(onClick = {
					dismissDialog(
						ConfirmCloseResult.Cancel,
						ApplicationState.CloseType.None
					)
				}) {
					Text("Cancel")
				}
				Button(onClick = { dismissDialog(ConfirmCloseResult.Discard, closeType) }) {
					Text("Discard and close")
				}
			}
		},
		modifier = Modifier.width(300.dp).padding(Ui.Padding.XL)
	)
}

private enum class ConfirmCloseResult {
	SaveAll,
	Discard,
	Cancel
}

private fun ApplicationScope.performClose(
	app: ApplicationState,
	closeType: ApplicationState.CloseType
) {
	when (closeType) {
		ApplicationState.CloseType.Application -> exitApplication()
		ApplicationState.CloseType.Project -> app.closeProject()
		ApplicationState.CloseType.None -> {
			/* noop */
		}
	}
}

private fun ApplicationScope.onRequestClose(
	component: AppCloseManager,
	app: ApplicationState,
	closeType: ApplicationState.CloseType
) {
	if (component.hasUnsavedBuffers()) {
		app.showConfirmProjectClose(closeType)
	} else {
		performClose(app, closeType)
	}
}
