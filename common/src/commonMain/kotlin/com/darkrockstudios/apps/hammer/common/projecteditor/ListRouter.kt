package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneListComponent
import kotlinx.coroutines.flow.SharedFlow

internal class ListRouter(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef,
	private val selectedSceneItem: SharedFlow<SceneItem?>,
	private val onSceneSelected: (sceneDef: SceneItem) -> Unit
) {
	private val navigation = StackNavigation<Config>()

	private val stack = componentContext.childStack(
		source = navigation,
		initialConfiguration = Config.List,
		key = "MainRouter",
		childFactory = ::createChild
	)

	val state: Value<ChildStack<Config, ProjectEditor.ChildDestination.List>>
		get() = stack

	private fun createChild(config: Config, componentContext: ComponentContext): ProjectEditor.ChildDestination.List =
		when (config) {
			is Config.List -> ProjectEditor.ChildDestination.List.Scenes(sceneList(componentContext))
			is Config.None -> ProjectEditor.ChildDestination.List.None
		}

	private fun sceneList(componentContext: ComponentContext): SceneListComponent =
		SceneListComponent(
			componentContext = componentContext,
			projectDef = projectDef,
			selectedSceneItem = selectedSceneItem,
			sceneSelected = onSceneSelected
		)

	fun moveToBackStack() {
		if (stack.value.active.configuration !is Config.None) {
			navigation.push(Config.None)
		}
	}

	fun show() {
		if (stack.value.active.configuration !is Config.List) {
			navigation.pop()
		}
	}

	sealed class Config : Parcelable {
		@Parcelize
		object List : Config()

		@Parcelize
		object None : Config()
	}
}