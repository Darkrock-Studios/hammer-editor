package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.lifecycle.LifecycleController
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootFab
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootUi
import com.darkrockstudios.apps.hammer.common.projectroot.getDestinationIcon
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalComposeApi
@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@Composable
internal fun ApplicationScope.ProjectEditorWindow(
	app: ApplicationState,
	projectDef: ProjectDef
) {
	val lifecycle = remember { LifecycleRegistry() }
	val compContext = remember { DefaultComponentContext(lifecycle) }
	val windowState = rememberWindowState(size = DpSize(1000.dp, 1200.dp))
	val closeRequest by app.closeRequest.subscribeAsState()

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

	val shouldConfirmClose by component.closeRequestHandlers.subscribeAsState()

	LifecycleController(lifecycle, windowState)

	fun cancelClose() {
		app.dismissConfirmProjectClose()
		component.cancelCloseRequest()
	}

	Window(
		title = DR.strings.project_window_title.get(projectDef.name),
		state = windowState,
		icon = painterResource("icon.png"),
		onCloseRequest = { onRequestClose(component, app, ApplicationState.CloseType.Application) }
	) {
		val scope = rememberCoroutineScope()
		val mainDispatcher = rememberMainDispatcher()

		Column {
			EditorMenuBar(component, app, ::onRequestClose)

			AppContent(component)
		}

		LaunchedEffect(closeRequest) {
			if (closeRequest != ApplicationState.CloseType.None) {
				component.requestClose()
			}
		}

		if (shouldConfirmClose.isNotEmpty()) {
			val item = shouldConfirmClose.first()
			when (item) {
				CloseConfirm.Scenes -> {
					confirmCloseUnsavedScenesDialog(closeRequest) { result, closeType ->
						scope.launch {
							if (result == ConfirmCloseResult.SaveAll) {
								component.storeDirtyBuffers()
							}

							withContext(mainDispatcher) {
								if (result != ConfirmCloseResult.Cancel) {
									component.closeRequestDealtWith(CloseConfirm.Scenes)
								} else {
									cancelClose()
								}
							}
						}
					}
				}

				CloseConfirm.Notes -> {
					confirmCloseUnsavedNotesDialog(closeRequest) { result, closeType ->
						when (result) {
							ConfirmCloseResult.SaveAll -> error("Unhandled close type: $closeType")
							ConfirmCloseResult.Discard -> component.closeRequestDealtWith(CloseConfirm.Notes)
							ConfirmCloseResult.Cancel -> cancelClose()
						}
					}
				}

				CloseConfirm.Encyclopedia -> {
					confirmCloseUnsavedEncyclopediaDialog(closeRequest) { result, closeType ->
						when (result) {
							ConfirmCloseResult.SaveAll -> error("Unhandled close type: $closeType")
							ConfirmCloseResult.Discard -> component.closeRequestDealtWith(CloseConfirm.Encyclopedia)
							ConfirmCloseResult.Cancel -> cancelClose()
						}
					}
				}

				CloseConfirm.Sync -> {
					component.showProjectSync()
				}

				CloseConfirm.Complete -> performClose(app, closeRequest)
			}
		}
	}
}

@Composable
private fun FrameWindowScope.EditorMenuBar(
	component: ProjectRoot,
	app: ApplicationState,
	onRequestClose: (AppCloseManager, ApplicationState, ApplicationState.CloseType) -> Unit
) {
	val menu by app.menu.subscribeAsState()

	MenuBar {
		Menu(DR.strings.project_window_menu_file.get()) {
			Item(DR.strings.project_window_menu_item_close.get(), onClick = {
				onRequestClose(component, app, ApplicationState.CloseType.Project)
			})
			Item(DR.strings.project_window_menu_item_exit.get(), onClick = {
				onRequestClose(component, app, ApplicationState.CloseType.Application)
			})
		}

		menu.forEach { menuDescriptor ->
			Menu(menuDescriptor.label.get()) {
				menuDescriptor.items.forEach { itemDescriptor ->
					Item(
						itemDescriptor.label.get(),
						onClick = { itemDescriptor.action(itemDescriptor.id) },
						shortcut = itemDescriptor.shortcut?.toDesktopShortcut()
					)
				}
			}
		}
	}
}

@Composable
private fun AppContent(component: ProjectRoot) {
	val destinations = remember { ProjectRoot.DestinationTypes.values() }
	val router by component.routerState.subscribeAsState()

	Box {
		Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
			NavigationRail(modifier = Modifier.padding(top = Ui.Padding.M)) {
				destinations.forEach { item ->
					NavigationRailItem(
						icon = { Icon(imageVector = getDestinationIcon(item), contentDescription = item.text.get()) },
						label = { Text(item.text.get()) },
						selected = router.active.instance.getLocationType() == item,
						onClick = { component.showDestination(item) }
					)
				}
			}

			ProjectRootUi(component)
		}

		Box(modifier = Modifier.align(Alignment.BottomEnd).padding(Ui.Padding.L)) {
			ProjectRootFab(
				component
			)
		}
	}
}