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
import com.darkrockstudios.apps.hammer.common.notes.Notes
import com.darkrockstudios.apps.hammer.common.notes.NotesComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorComponent

internal class ProjectRootRouter(
    componentContext: ComponentContext,
    private val projectDef: ProjectDef,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
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
        return ProjectEditorComponent(
            componentContext = componentContext,
            projectDef = config.projectDef,
            addMenu = addMenu,
            removeMenu = removeMenu
        )
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

    sealed class Config : Parcelable {
        @Parcelize
        data class EditorConfig(val projectDef: ProjectDef) : Config()

        @Parcelize
        data class NotesConfig(val projectDef: ProjectDef) : Config()
    }
}