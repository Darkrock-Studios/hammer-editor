package com.darkrockstudios.apps.hammer.common.components.storyeditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneListComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable

internal class ListRouter(
	componentContext: ComponentContext,
	private val projectDef: ProjectDef,
	private val selectedSceneItem: SharedFlow<SceneItem?>,
	private val onSceneSelected: (sceneDef: SceneItem) -> Unit,
	private val showOutlineOverview: () -> Unit,
) {
	private val navigation = StackNavigation<Config>()

	private val stack = componentContext.childStack(
		source = navigation,
		initialConfiguration = Config.List,
		key = "MainRouter",
		childFactory = ::createChild,
		serializer = Config.serializer(),
	)

	val state: Value<ChildStack<Config, StoryEditor.ChildDestination.List>> = stack

	private fun createChild(config: Config, componentContext: ComponentContext): StoryEditor.ChildDestination.List =
		when (config) {
			is Config.List -> StoryEditor.ChildDestination.List.Scenes(sceneList(componentContext))
			is Config.None -> StoryEditor.ChildDestination.List.None
		}

	private fun sceneList(componentContext: ComponentContext): SceneListComponent =
		SceneListComponent(
			componentContext = componentContext,
			projectDef = projectDef,
			selectedSceneItem = selectedSceneItem,
			sceneSelected = onSceneSelected,
			showOutlineOverviewDialog = showOutlineOverview,
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

	@Serializable
	sealed class Config {
		@Serializable
		data object List : Config()

		@Serializable
		data object None : Config()
	}
}