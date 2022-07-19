package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneDef
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneListComponent
import kotlinx.coroutines.flow.SharedFlow

internal class ListRouter(
    componentContext: ComponentContext,
    private val projectDef: ProjectDef,
    private val selectedSceneDef: SharedFlow<SceneDef?>,
    private val onSceneSelected: (sceneDef: SceneDef) -> Unit,
    private val detailsRouter: DetailsRouter
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
            detailsRouter = detailsRouter,
            projectDef = projectDef,
            selectedSceneDef = selectedSceneDef,
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