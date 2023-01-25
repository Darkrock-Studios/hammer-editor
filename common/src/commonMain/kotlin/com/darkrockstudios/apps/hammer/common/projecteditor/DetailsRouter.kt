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
import com.darkrockstudios.apps.hammer.common.projecteditor.drafts.DraftsList
import com.darkrockstudios.apps.hammer.common.projecteditor.drafts.DraftsListComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorComponent
import kotlinx.coroutines.flow.MutableSharedFlow

internal class DetailsRouter(
	componentContext: ComponentContext,
	private val selectedSceneItem: MutableSharedFlow<SceneItem?>,
	private val removeMenu: (id: String) -> Unit,
	private val closeDetails: () -> Unit,
	private val addMenu: (menu: MenuDescriptor) -> Unit,
) {

	private val navigation = StackNavigation<Config>()

	private val stack = componentContext.childStack(
			source = navigation,
			initialConfiguration = Config.None,
			key = "DetailsRouter",
			childFactory = ::createChild
	)

	val state: Value<ChildStack<Config, ProjectEditor.ChildDestination.Detail>>
		get() = stack

	private fun createChild(
		config: Config,
		componentContext: ComponentContext
	): ProjectEditor.ChildDestination.Detail =
		when (config) {
			is Config.None -> ProjectEditor.ChildDestination.Detail.None
			is Config.SceneEditor -> ProjectEditor.ChildDestination.Detail.EditorDestination(
				sceneEditor(componentContext = componentContext, sceneDef = config.sceneDef)
			)

			is Config.DraftsList -> ProjectEditor.ChildDestination.Detail.DraftsDestination(
				draftsList(componentContext = componentContext, sceneDef = config.sceneDef)
			)
		}

	private fun sceneEditor(componentContext: ComponentContext, sceneDef: SceneItem): SceneEditor =
		SceneEditorComponent(
			componentContext = componentContext,
			originalSceneItem = sceneDef,
			addMenu = addMenu,
			removeMenu = removeMenu,
			closeSceneEditor = closeDetails,
			showDraftsList = ::showDraftsList
		)

	private fun draftsList(componentContext: ComponentContext, sceneDef: SceneItem): DraftsList =
		DraftsListComponent(
			componentContext = componentContext,
			sceneItem = sceneDef,
			closeDrafts = ::closeDrafts
		)

	fun showScene(sceneDef: SceneItem) {
		navigation.navigate(
			transformer = { stack ->
				stack.dropLastWhile { it !is Config.None }
					.plus(Config.SceneEditor(sceneDef = sceneDef))
			},
			onComplete = { _, _ -> }
		)
	}

	fun showDraftsList(sceneDef: SceneItem) {
		navigation.navigate(
			transformer = { stack ->
				stack.plus(Config.DraftsList(sceneDef = sceneDef))
			},
			onComplete = { _, _ -> }
		)
	}

	private fun closeDrafts() {
		navigation.popWhile { it is Config.DraftsList }
	}

	fun closeScene() {
		navigation.popWhile { it !is Config.None }
		selectedSceneItem.tryEmit(null)
	}

	fun isShown(): Boolean =
		when (stack.value.active.configuration) {
			is Config.None -> false
			else -> true
		}

	sealed class Config : Parcelable {
		@Parcelize
		object None : Config()

		@Parcelize
		data class SceneEditor(val sceneDef: SceneItem) : Config()

		@Parcelize
		data class DraftsList(val sceneDef: SceneItem) : Config()
	}
}