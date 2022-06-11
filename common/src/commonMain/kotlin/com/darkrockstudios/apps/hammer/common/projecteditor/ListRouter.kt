package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.data.Scene
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneListComponent
import kotlinx.coroutines.flow.SharedFlow

internal class ListRouter(
    componentContext: ComponentContext,
    private val project: Project,
    private val selectedScene: SharedFlow<Scene?>,
    private val onSceneSelected: (scene: Scene) -> Unit
) {
    private val router =
        componentContext.router<Config, ProjectEditor.Child.List>(
            initialConfiguration = Config.List,
            key = "MainRouter",
            childFactory = ::createChild
        )

    val state: Value<RouterState<Config, ProjectEditor.Child.List>> = router.state

    private fun createChild(config: Config, componentContext: ComponentContext): ProjectEditor.Child.List =
        when (config) {
            is Config.List -> ProjectEditor.Child.List.Scenes(sceneList(componentContext))
            is Config.None -> ProjectEditor.Child.List.None
        }

    private fun sceneList(componentContext: ComponentContext): SceneListComponent =
        SceneListComponent(
            componentContext = componentContext,
            project = project,
            selectedScene = selectedScene,
            sceneSelected = onSceneSelected
        )

    fun moveToBackStack() {
        if (router.activeChild.configuration !is Config.None) {
            router.push(Config.None)
        }
    }

    fun show() {
        if (router.activeChild.configuration !is Config.List) {
            router.pop()
        }
    }

    sealed class Config : Parcelable {
        @Parcelize
        object List : Config()

        @Parcelize
        object None : Config()
    }
}