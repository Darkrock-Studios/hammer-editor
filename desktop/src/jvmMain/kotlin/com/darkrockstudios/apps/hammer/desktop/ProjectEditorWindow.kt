package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRail
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.ProjectThemeOverride
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootFab
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootUi
import com.darkrockstudios.apps.hammer.common.projectroot.toHdNavRailDestination
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalComposeApi
@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@Composable
internal fun ApplicationScope.ProjectEditorWindow(
	app: ApplicationState,
	projectDef: ProjectDef,
) {
	val backDispatcher = BackDispatcher()
	val lifecycle = remember { LifecycleRegistry() }
	val compContext = remember { DefaultComponentContext(lifecycle = lifecycle, backHandler = backDispatcher) }
	val windowState = rememberPersistedWindowState(
		WindowGeometryStore.Window.ProjectRoot,
		defaultSize = coerceWindowSize(1400.dp, 1200.dp),
	)
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
		title = Res.string.project_window_title.get(projectDef.name),
		state = windowState,
		icon = painterResource("icon.png"),
		onCloseRequest = { onRequestClose(component, app, ApplicationState.CloseType.Application) },
		onKeyEvent = { event ->
			when {
				event.key == Key.Escape && event.type == KeyEventType.KeyUp -> {
					backDispatcher.back()
				}
				event.type == KeyEventType.KeyDown &&
					event.key == Key.F &&
					event.isShiftPressed &&
					(event.isCtrlPressed || event.isMetaPressed) -> {
					component.showGlobalSearch()
					true
				}
				else -> false
			}
		}
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
		Menu(Res.string.project_window_menu_file.get()) {
			Item(Res.string.project_window_menu_item_close.get(), onClick = {
				onRequestClose(component, app, ApplicationState.CloseType.Project)
			})
			Item(Res.string.project_window_menu_item_exit.get(), onClick = {
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

		LaunchedEffect(menu) {
			window.jMenuBar.invalidate()
			window.jMenuBar.repaint()
		}
	}
}

@Composable
private fun AppContent(component: ProjectRoot) {
	val router by component.routerState.subscribeAsState()
	val themeState by component.projectTheme.subscribeAsState()
	val navRailState by component.navRailState.subscribeAsState()
	val rootSnackbar = rememberRootSnackbarHostState()

	val destinations = ProjectRoot.DestinationTypes.entries.map { it.toHdNavRailDestination() }

	ProjectThemeOverride(themeState.theme) {
		Box {
			Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
				HdNavRail(
					destinations = destinations,
					selectedId = router.active.instance.getLocationType(),
					onSelect = { component.showDestination(it) },
					expanded = navRailState.expanded,
					onToggleExpanded = { component.toggleNavRailExpanded() },
					footer = {
						HdMonoLabel(
							text = "v${BuildMetadata.APP_VERSION}",
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					},
				)

				ProjectRootUi(component, rootSnackbar)
			}

			SnackbarHost(
				rootSnackbar.snackbarHostState,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = Ui.Padding.XL)
			)

			Box(modifier = Modifier.align(Alignment.BottomEnd).padding(Ui.Padding.L)) {
				ProjectRootFab(
					component
				)
			}
		}
	}
}