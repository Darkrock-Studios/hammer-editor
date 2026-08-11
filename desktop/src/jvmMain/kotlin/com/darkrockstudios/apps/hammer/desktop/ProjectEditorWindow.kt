package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
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
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRail
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.compose.theme.ProjectThemeOverride
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootFab
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootUi
import com.darkrockstudios.apps.hammer.common.projectroot.toHdNavRailDestination
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalComposeApi
@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@Composable
internal fun NucleusApplicationScope.ProjectEditorWindow(
	app: ApplicationState,
	projectDef: ProjectDef,
	settings: GlobalSettings,
	darkMode: Boolean,
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
			addMenu = { /* No-op: desktop now shows menu items in-UI like mobile */ },
			removeMenu = { /* No-op: desktop now shows menu items in-UI like mobile */ },
			onCloseProject = { app.showConfirmProjectClose(ApplicationState.CloseType.Project) },
			initialDeepLink = app.consumePendingDeepLink(),
		)
	}

	val shouldConfirmClose by component.closeRequestHandlers.subscribeAsState()

	LifecycleController(lifecycle, windowState)

	fun cancelClose() {
		app.dismissConfirmProjectClose()
		component.cancelCloseRequest()
	}

	val windowTitle = Res.string.project_window_title.get(projectDef.name)
	val shortcutHost = remember { ProjectShortcutHost() }

	MaterialDecoratedWindow(
		title = windowTitle,
		state = windowState,
		icon = painterResource(Res.drawable.hammer_icon),
		onCloseRequest = { onRequestClose(component, app, ApplicationState.CloseType.Application) },
		// These two run pre-focus so a focused editor can't swallow them.
		onPreviewKeyEvent = { event ->
			when {
				event.matchesShortcut(Key.F3) -> shortcutHost.startProjectSync()
				event.matchesShortcut(Key.S, ctrl = true, alt = true) -> shortcutHost.saveAllBuffers()
				else -> false
			}
		},
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
				event.type == KeyEventType.KeyDown &&
					event.key == Key.W &&
					(event.isCtrlPressed || event.isMetaPressed) -> {
					onRequestClose(component, app, ApplicationState.CloseType.Project)
					true
				}

				event.type == KeyEventType.KeyDown &&
					event.key == Key.Q &&
					(event.isCtrlPressed || event.isMetaPressed) -> {
					onRequestClose(component, app, ApplicationState.CloseType.Application)
					true
				}
				else -> false
			}
		}
	) {
		val scope = rememberCoroutineScope()
		val mainDispatcher = rememberMainDispatcher()

		MaterialTitleBar {
			Text(
				text = windowTitle,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.align(Alignment.CenterHorizontally),
			)
		}

		// Tao windows are their own ComposeScene: locals provided outside the
		// window (AppTheme in Main.kt) don't reach this content, so re-apply.
		AppTheme(useDarkTheme = darkMode, settings = settings) {
			AppContent(component, shortcutHost)

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

					CloseConfirm.Timeline -> {
						confirmCloseUnsavedTimelineDialog(closeRequest) { result, closeType ->
							when (result) {
								ConfirmCloseResult.SaveAll -> error("Unhandled close type: $closeType")
								ConfirmCloseResult.Discard -> component.closeRequestDealtWith(
									CloseConfirm.Timeline
								)
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
private fun AppContent(component: ProjectRoot, shortcutHost: ProjectShortcutHost) {
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

				ProjectRootUi(component, rootSnackbar, shortcutHost = shortcutHost)
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
