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
import com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview.OutlineOverviewComponent
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
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
			removeMenu = removeMenu
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
				StoryEditor.ChildDestination.DialogDestination.Outline(
					OutlineOverviewComponent(componentContext, projectDef, ::dismissDialog)
				)
		}
	}

	override val listRouterState: Value<ChildStack<*, StoryEditor.ChildDestination.List>> = listRouter.state
	override val detailsRouterState: Value<ChildStack<*, StoryEditor.ChildDestination.Detail>> = detailsRouter.state
	override val dialogState: Value<ChildSlot<*, StoryEditor.ChildDestination.DialogDestination>> = dialogRouter

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

		if (isMultiPane) {
			switchToMultiPane()
		} else {
			switchToSinglePane()
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

	init {
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
	}
}