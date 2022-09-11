package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.popWhile
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorComponent

internal class DetailsRouter(
		componentContext: ComponentContext,
		private val addMenu: (menu: MenuDescriptor) -> Unit,
		private val removeMenu: (id: String) -> Unit,
		private val closeDetails: () -> Unit,
							)
{

	private val navigation = StackNavigation<Config>()

	private val stack = componentContext.childStack(
			source = navigation,
			initialConfiguration = Config.None,
			key = "DetailsRouter",
			childFactory = ::createChild
												   )

	val state: Value<ChildStack<Config, ProjectEditor.Child.Detail>>
		get() = stack

	private fun createChild(
			config: Config,
			componentContext: ComponentContext
						   ): ProjectEditor.Child.Detail =
		when(config)
		{
			is Config.None        -> ProjectEditor.Child.Detail.None
			is Config.SceneEditor -> ProjectEditor.Child.Detail.Editor(
					sceneEditor(componentContext = componentContext, sceneDef = config.sceneDef)
																	  )
		}

	private fun sceneEditor(componentContext: ComponentContext, sceneDef: SceneItem): SceneEditor =
		SceneEditorComponent(
				componentContext = componentContext,
				originalSceneItem = sceneDef,
				addMenu = addMenu,
				removeMenu = removeMenu,
				closeSceneEditor = closeDetails
							)

	fun showScene(sceneDef: SceneItem)
	{
		navigation.navigate(
				transformer = { stack ->
					stack.dropLastWhile { it is Config.SceneEditor }
							.plus(Config.SceneEditor(sceneDef = sceneDef))
				},
				onComplete = { _, _ -> }
						   )
	}

	fun closeScene()
	{
		navigation.popWhile { it !is Config.None }
	}

	fun isShown(): Boolean =
		when(stack.value.active.configuration)
		{
			is Config.None        -> false
			is Config.SceneEditor -> true
		}

	sealed class Config: Parcelable
	{
		@Parcelize
		object None: Config()

		@Parcelize
		data class SceneEditor(val sceneDef: SceneItem): Config()
	}
}