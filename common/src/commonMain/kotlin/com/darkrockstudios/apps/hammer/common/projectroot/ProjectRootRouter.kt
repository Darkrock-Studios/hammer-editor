package com.darkrockstudios.apps.hammer.common.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import com.darkrockstudios.apps.hammer.common.notes.Notes
import com.darkrockstudios.apps.hammer.common.notes.NotesComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ProjectRootRouter(
    componentContext: ComponentContext,
    private val projectDef: ProjectDef,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
    private val updateShouldClose: () -> Unit,
    private val scope: CoroutineScope
) {
    private val navigation = StackNavigation<Config>()

    private val stack = componentContext.childStack(
        source = navigation,
        initialConfiguration = Config.EditorConfig(projectDef),
        key = "ProjectRootRouter",
        childFactory = ::createChild
    )

    val state: Value<ChildStack<Config, ProjectRoot.Destination>>
        get() = stack

    private fun createChild(
        config: Config,
        componentContext: ComponentContext
    ): ProjectRoot.Destination =
        when (config) {
            is Config.EditorConfig -> ProjectRoot.Destination.EditorDestination(
                editorComponent(config, componentContext)
            )

            is Config.NotesConfig -> ProjectRoot.Destination.NotesDestination(
                notes(config, componentContext)
            )
        }

    private fun editorComponent(config: Config.EditorConfig, componentContext: ComponentContext): ProjectEditor {
        val editor = ProjectEditorComponent(
            componentContext = componentContext,
            projectDef = config.projectDef,
            addMenu = addMenu,
            removeMenu = removeMenu
        )

        scope.launch {
            editor.shouldCloseRoot.collect {
                withContext(mainDispatcher) {
                    updateShouldClose()
                }
            }
        }

        return editor
    }

    private fun notes(config: Config.NotesConfig, componentContext: ComponentContext): Notes {
        return NotesComponent(
            componentContext = componentContext,
            projectDef = config.projectDef,
            addMenu = addMenu,
            removeMenu = removeMenu
        )
    }

    fun showEditor() {
        navigation.navigate(
            transformer = { stack ->
                stack.dropWhile { true }
                    .plus(Config.EditorConfig(projectDef = projectDef))
            },
            onComplete = { _, _ -> }
        )
    }

    fun showNotes() {
        navigation.navigate(
            transformer = { stack ->
                stack.dropWhile { true }
                    .plus(Config.NotesConfig(projectDef = projectDef))
            },
            onComplete = { _, _ -> }
        )
    }

    fun isAtRoot(): Boolean {
        return when (val destination = state.value.active.instance) {
            is ProjectRoot.Destination.EditorDestination -> {
                !destination.component.isDetailShown()
            }

            is ProjectRoot.Destination.NotesDestination -> {
                true
            }
        }
    }

    init {
        navigation.subscribe { updateShouldClose() }
    }

    sealed class Config : Parcelable {
        @Parcelize
        data class EditorConfig(val projectDef: ProjectDef) : Config()

        @Parcelize
        data class NotesConfig(val projectDef: ProjectDef) : Config()
    }
}