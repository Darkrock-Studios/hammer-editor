package com.darkrockstudios.apps.hammer.common.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.encyclopedia.EncyclopediaComponent
import com.darkrockstudios.apps.hammer.common.notes.Notes
import com.darkrockstudios.apps.hammer.common.notes.NotesComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class ProjectRootRouter(
    componentContext: ComponentContext,
    private val projectDef: ProjectDef,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
    private val updateShouldClose: () -> Unit,
    private val scope: CoroutineScope,
    private val dispatcherMain: CoroutineContext,
) : Router {
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

            is Config.EncyclopediaConfig -> ProjectRoot.Destination.EncyclopediaDestination(
                encyclopedia(config, componentContext)
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
                withContext(dispatcherMain) {
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

    private fun encyclopedia(config: Config.EncyclopediaConfig, componentContext: ComponentContext): Encyclopedia {
        return EncyclopediaComponent(
            componentContext = componentContext,
            projectDef = config.projectDef,
            updateShouldClose = updateShouldClose,
            addMenu = addMenu,
            removeMenu = removeMenu
        )
    }

    fun showEditor() {
        navigation.bringToFront(Config.EditorConfig(projectDef = projectDef))
    }

    fun showNotes() {
        navigation.bringToFront(Config.NotesConfig(projectDef = projectDef))
    }

    fun showEncyclopedia() {
        navigation.bringToFront(Config.EncyclopediaConfig(projectDef = projectDef))
    }

    override fun isAtRoot(): Boolean {
        val router = state.value.active.instance as? Router
        return router?.isAtRoot() ?: true
    }

    init {
        navigation.subscribe { updateShouldClose() }
    }

    sealed class Config : Parcelable {
        @Parcelize
        data class EditorConfig(val projectDef: ProjectDef) : Config()

        @Parcelize
        data class NotesConfig(val projectDef: ProjectDef) : Config()

        @Parcelize
        data class EncyclopediaConfig(val projectDef: ProjectDef) : Config()
    }
}