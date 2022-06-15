// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.di.NapierLogger
import com.darkrockstudios.apps.hammer.common.di.mainModule
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorUi
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
                val lifecycle = LifecycleRegistry()
                val compContext = DefaultComponentContext(lifecycle)

                ProjectSelectionWindow(compContext, lifecycle) { project ->
                    applicationState.openProject(project)
                }
            }
            is WindowState.ProjectWindow -> {
                val lifecycle = LifecycleRegistry()
                val compContext = DefaultComponentContext(lifecycle)

                ProjectEditorWindow(compContext, applicationState, lifecycle, windowState.project)
            }
        }
    }
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@ExperimentalDecomposeApi
@Composable
private fun ApplicationScope.ProjectSelectionWindow(
    compContext: DefaultComponentContext,
    lifecycle: LifecycleRegistry,
    onProjectSelected: (project: Project) -> Unit
) {
    val windowState = rememberWindowState()
    LifecycleController(lifecycle, windowState)

    Window(
        title = "Project Selection",
        state = windowState,
        onCloseRequest = ::exitApplication,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MaterialTheme {
                ProjectSelectionUi(ProjectSelectionComponent(compContext, onProjectSelected))
            }
        }
    }
}

@ExperimentalDecomposeApi
@Composable
private fun ApplicationScope.ProjectEditorWindow(
    compContext: DefaultComponentContext,
    app: ApplicationState,
    lifecycle: LifecycleRegistry,
    project: Project
) {
    val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
    LifecycleController(lifecycle, windowState)

    Window(
        title = "Hammer",
        state = windowState,
        onCloseRequest = ::exitApplication
    ) {
        Column {
            val menu by remember { app.menu }
            MenuBar {
                Menu("File") {
                    Item("Close Project", onClick = app::closeProject)
                    Item("Exit", onClick = ::exitApplication)
                }

                menu.forEach { menuDescriptor ->
                    Menu(menuDescriptor.label) {
                        menuDescriptor.items.forEach { itemDescriptor ->
                            Item(
                                itemDescriptor.label,
                                onClick = { itemDescriptor.action(itemDescriptor.id) }
                            )
                        }
                    }
                }
            }
            Surface(modifier = Modifier.fillMaxSize()) {
                MaterialTheme {
                    ProjectEditorUi(ProjectEditorComponent(
                        componentContext = compContext,
                        project = project,
                        addMenu = { menu ->
                            app.addMenu(menu)
                        },
                        removeMenu = { menuId ->
                            app.removeMenu(menuId)
                        }
                    ))
                }
            }
        }
    }
}

private class ApplicationState {
    val windows = mutableStateOf<WindowState>(WindowState.ProjectSectionWindow())

    private val _menu = mutableStateOf<Set<MenuDescriptor>>(emptySet())
    val menu: State<Set<MenuDescriptor>> = _menu

    fun addMenu(menuDescriptor: MenuDescriptor) {
        _menu.value = mutableSetOf(menuDescriptor).apply { add(menuDescriptor) }
    }

    fun removeMenu(menuId: String) {
        _menu.value = _menu.value.filter { it.id != menuId }.toSet()
    }

    fun openProject(project: Project) {
        windows.value = WindowState.ProjectWindow(project)
    }

    fun closeProject() {
        windows.value = WindowState.ProjectSectionWindow()
    }
}

private sealed class WindowState {
    data class ProjectSectionWindow(private val _data: Boolean = true) : WindowState()

    data class ProjectWindow(val project: Project) : WindowState()
}