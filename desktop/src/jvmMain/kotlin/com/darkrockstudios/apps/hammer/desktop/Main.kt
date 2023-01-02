package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.darkrockstudios.apps.hammer.common.ProjectRootUi
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.GlobalContext

@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@ExperimentalComposeApi
fun main() {
    Napier.base(DebugAntilog())

    GlobalContext.startKoin {
        logger(NapierLogger())
        modules(mainModule)
    }

    application {
        val applicationState = remember { ApplicationState() }
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
    LifecycleController(lifecycle, windowState)

    Window(
        title = "Project Selection",
        state = windowState,
        onCloseRequest = ::exitApplication,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MaterialTheme {
                ProjectSelectionUi(
                    ProjectSelectionComponent(
                        componentContext = compContext,
                        showProjectDirectory = true,
                        onProjectSelected = onProjectSelected
                    )
                )
            }
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
                MaterialTheme {
                    ProjectRootUi(component)
                }
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
        modifier = Modifier.width(300.dp).padding(Ui.PADDING)
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
