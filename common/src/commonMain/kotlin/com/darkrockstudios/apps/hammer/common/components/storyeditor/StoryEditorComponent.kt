package com.darkrockstudios.apps.hammer.common.components.storyeditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.decompose.value.observe
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusModeComponent
import com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview.OutlineOverviewComponent
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class StoryEditorComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	addMenu: (menu: MenuDescriptor) -> Unit,
	removeMenu: (id: String) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), StoryEditor {

	private val sceneEditor: SceneEditorRepository by projectInject()

	private val selectedSceneItemFlow = MutableSharedFlow<SceneItem?>(
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)

	private val _shouldCloseRoot = MutableSharedFlow<Boolean>(
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	override val shouldCloseRoot = _shouldCloseRoot

	private val detailsRouter =
		DetailsRouter(
			componentContext = this,
			selectedSceneItem = selectedSceneItemFlow,
			addMenu = addMenu,
			closeDetails = ::closeDetails,
			removeMenu = removeMenu,
			openFocusMode = ::enterFocusMode
		)

	private val listRouter =
		ListRouter(
			componentContext = this,
			projectDef = projectDef,
			selectedSceneItem = selectedSceneItemFlow,
			onSceneSelected = ::onSceneSelected,
			showOutlineOverview = ::showOutlineOverview
		)

	private val dialogNavigation = SlotNavigation<StoryEditor.DialogConfig>()
	private val dialogRouter = componentContext.childSlot(
		source = dialogNavigation,
		key = "dialogRouter",
		initialConfiguration = { StoryEditor.DialogConfig.None },
		handleBackButton = false,
	) { config, componentContext ->
		createDialogChild(config, componentContext)
	}

	private fun createDialogChild(
		dialogConfig: StoryEditor.DialogConfig,
		componentContext: ComponentContext
	): StoryEditor.ChildDestination.DialogDestination {
		return when (dialogConfig) {
			StoryEditor.DialogConfig.None ->
				StoryEditor.ChildDestination.DialogDestination.None

			StoryEditor.DialogConfig.OutlineOverview ->
				StoryEditor.ChildDestination.DialogDestination.OutlineDestination(
					OutlineOverviewComponent(componentContext, projectDef, ::dismissDialog)
				)
		}
	}

	private val fullscreenNavigation = SlotNavigation<StoryEditor.FullScreenConfig>()
	private val fullscreenRouter = componentContext.childSlot(
		source = fullscreenNavigation,
		key = "fullscreenRouter",
		initialConfiguration = { StoryEditor.FullScreenConfig.None },
		handleBackButton = false,
	) { config, componentContext ->
		createFullScreenChild(config, componentContext)
	}

	private fun createFullScreenChild(
		config: StoryEditor.FullScreenConfig,
		componentContext: ComponentContext
	): StoryEditor.ChildDestination.FullScreen {
		return when (config) {
			StoryEditor.FullScreenConfig.None ->
				StoryEditor.ChildDestination.FullScreen.None

			is StoryEditor.FullScreenConfig.FocusMode ->
				StoryEditor.ChildDestination.FullScreen.FocusModeDestination(
					FocusModeComponent(componentContext, projectDef, config.sceneItem, ::exitFocusMode)
				)
		}
	}

	override val listRouterState: Value<ChildStack<*, StoryEditor.ChildDestination.List>> = listRouter.state
	override val detailsRouterState: Value<ChildStack<*, StoryEditor.ChildDestination.Detail>> = detailsRouter.state
	override val dialogState: Value<ChildSlot<*, StoryEditor.ChildDestination.DialogDestination>> = dialogRouter
	override val fullscreenState: Value<ChildSlot<*, StoryEditor.ChildDestination.FullScreen>> = fullscreenRouter

	override fun isDetailShown(): Boolean {
		return detailsRouterState.value.active.instance !is StoryEditor.ChildDestination.Detail.None
	}

	private val _state = MutableValue(StoryEditor.State(projectDef))
	override val state: Value<StoryEditor.State> = _state

	override fun closeDetails(): Boolean {
		return if (isMultiPaneMode() && detailsRouter.isShown()) {
			closeDetailsInMultipane()
			true
		} else if (detailsRouter.isShown()) {
			closeDetailsAndShowListInSinglepane()
			true
		} else {
			false
		}
	}

	override fun enterFocusMode(sceneItem: SceneItem) {
		listRouter.moveToBackStack()
		detailsRouter.closeScene()
		fullscreenNavigation.activate(StoryEditor.FullScreenConfig.FocusMode(sceneItem))
	}

	override fun exitFocusMode() {
		val config = fullscreenRouter.value.child?.configuration as? StoryEditor.FullScreenConfig.FocusMode
		if (config != null) {
			fullscreenNavigation.activate(StoryEditor.FullScreenConfig.None)
			onSceneSelected(config.sceneItem)
		} else {
			error("Should not have been able to exitFocusMode() without being in FocusMode")
		}
	}

	private fun closeDetailsInMultipane() {
		detailsRouter.closeScene()
	}

	private fun closeDetailsAndShowListInSinglepane() {
		listRouter.show()
		detailsRouter.closeScene()
	}

	private fun onSceneSelected(sceneItem: SceneItem) {
		detailsRouter.showScene(sceneItem)

		if (isMultiPaneMode()) {
			listRouter.show()
		} else {
			listRouter.moveToBackStack()
		}
	}

	override fun setMultiPane(isMultiPane: Boolean) {
		_state.getAndUpdate { it.copy(isMultiPane = isMultiPane) }

		val fullScreenConfig = fullscreenState.value.child?.configuration
		val inFullScreen = (fullScreenConfig != null) && (fullScreenConfig is StoryEditor.FullScreenConfig.FocusMode)

		// Do nothing if we are showing a fullscreen component
		if (inFullScreen.not()) {
			if (isMultiPane) {
				switchToMultiPane()
			} else {
				switchToSinglePane()
			}
		}
	}

	private fun switchToMultiPane() {
		listRouter.show()
	}

	private fun switchToSinglePane() {
		if (detailsRouter.isShown()) {
			listRouter.moveToBackStack()
		} else {
			listRouter.show()
		}
	}

	private fun isMultiPaneMode(): Boolean = _state.value.isMultiPane

	override fun hasUnsavedBuffers(): Boolean {
		return sceneEditor.hasDirtyBuffers()
	}

	override fun isAtRoot(): Boolean {
		return !isDetailShown()
	}

	override suspend fun storeDirtyBuffers() {
		sceneEditor.storeAllBuffers()
	}

	override fun shouldConfirmClose() = emptySet<CloseConfirm>()

	private val backButtonHandler = object : BackCallback() {
		override fun onBack() {
			if (!detailsRouter.isAtRoot()) {
				detailsRouter.onBack()
			} else {
				closeDetails()
			}
		}
	}

	override fun showOutlineOverview() {
		dialogNavigation.activate(StoryEditor.DialogConfig.OutlineOverview)
	}

	override fun dismissDialog() {
		dialogNavigation.activate(StoryEditor.DialogConfig.None)
	}

	override fun onCreate() {
		super.onCreate()

		backHandler.register(backButtonHandler)

		detailsRouter.state.observe(lifecycle) {
			(it.active.configuration as? DetailsRouter.Config.SceneEditor)?.let { sceneEditor ->
				selectedSceneItemFlow.tryEmit(sceneEditor.sceneDef)
			}
			backButtonHandler.isEnabled = isDetailShown()

			_shouldCloseRoot.tryEmit(!isDetailShown())
		}

		sceneEditor.subscribeToBufferUpdates(null, scope) {
			backButtonHandler.isEnabled = isDetailShown()
		}

		sceneEditor.subscribeToSceneUpdates(scope, ::onSceneTreeUpdate)
	}

	private fun onSceneTreeUpdate(sceneSummary: SceneSummary) {
		// If we've got a scene detail showing, and the tree updated,
		// see if the scene is no longer in the tree, that means it was
		// deleted, and we need to close the scene details
		if (detailsRouter.isShown()) {
			val sceneItem = when (val config = detailsRouter.state.value.active.configuration) {
				is DetailsRouter.Config.DraftCompare -> config.sceneDef
				is DetailsRouter.Config.DraftsList -> config.sceneDef
				is DetailsRouter.Config.SceneEditor -> config.sceneDef
				DetailsRouter.Config.None -> null
			}

			if (sceneItem != null) {
				val foundNode = sceneSummary.sceneTree.findBy { it.id == sceneItem.id }
				if (foundNode == null) {
					Napier.i { "Scene was deleted, closing Scene detail" }
					closeDetails()
				}
			}
		}
	}
}