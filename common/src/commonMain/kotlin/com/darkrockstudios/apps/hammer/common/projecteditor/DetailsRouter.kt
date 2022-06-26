package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.RouterState
import com.arkivanov.decompose.router.activeChild
import com.arkivanov.decompose.router.popWhile
import com.arkivanov.decompose.router.router
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.SceneDef
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorComponent

internal class DetailsRouter(
    componentContext: ComponentContext,
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
    private val closeDetails: () -> Unit,
    private val onFinished: () -> Unit
) {

    private val router =
        componentContext.router<Config, ProjectEditor.Child.Detail>(
            initialConfiguration = Config.None,
            key = "DetailsRouter",
            childFactory = ::createChild
        )

    val state: Value<RouterState<Config, ProjectEditor.Child.Detail>> = router.state

    private fun createChild(
        config: Config,
        componentContext: ComponentContext
    ): ProjectEditor.Child.Detail =
        when (config) {
            is Config.None -> ProjectEditor.Child.Detail.None
            is Config.SceneEditor -> ProjectEditor.Child.Detail.Editor(
                sceneEditor(componentContext = componentContext, sceneDef = config.sceneDef)
            )
        }

    private fun sceneEditor(componentContext: ComponentContext, sceneDef: SceneDef): SceneEditor =
        SceneEditorComponent(
            componentContext = componentContext,
            sceneDef = sceneDef,
            addMenu = addMenu,
            removeMenu = removeMenu,
            closeSceneEditor = closeDetails
        )

    fun showScene(sceneDef: SceneDef) {
        router.navigate(
            transformer = { stack ->
                stack.dropLastWhile { it is Config.SceneEditor }
                    .plus(Config.SceneEditor(sceneDef = sceneDef))
            },
            onComplete = { _, _ -> }
        )
    }

    fun closeScene() {
        router.popWhile { it !is Config.None }
    }

    fun isShown(): Boolean =
        when (router.activeChild.configuration) {
            is Config.None -> false
            is Config.SceneEditor -> true
        }

    sealed class Config : Parcelable {
        @Parcelize
        object None : Config()

        @Parcelize
        data class SceneEditor(val sceneDef: SceneDef) : Config()
    }
}