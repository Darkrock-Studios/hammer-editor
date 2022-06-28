package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.RouterState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.observe
import com.arkivanov.decompose.value.reduce
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.SceneDef
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.core.component.inject

class ProjectEditorComponent(
    componentContext: ComponentContext,
    private val projectDef: ProjectDef,
    addMenu: (menu: MenuDescriptor) -> Unit,
    removeMenu: (id: String) -> Unit,
) : ProjectEditor, ComponentContext by componentContext, Lifecycle.Callbacks {

    private val scope = CoroutineScope(defaultDispatcher)

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(projectDef)

    //private val isDetailsToolbarVisible = BehaviorSubject(!_models.value.isMultiPane)
    private val selectedSceneDefFlow = MutableSharedFlow<SceneDef?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val listRouter =
        ListRouter(
            componentContext = this,
            projectDef = projectDef,
            selectedSceneDef = selectedSceneDefFlow,
            onSceneSelected = ::onSceneSelected
        )

    override val listRouterState: Value<RouterState<*, ProjectEditor.Child.List>> =
        listRouter.state

    private val detailsRouter =
        DetailsRouter(
            componentContext = this,
            addMenu = addMenu,
            closeDetails = ::closeDetails,
            removeMenu = removeMenu
        )

    private val _shouldConfirmClose = MutableValue(false)
    override val shouldConfirmClose = _shouldConfirmClose

    override val detailsRouterState: Value<RouterState<*, ProjectEditor.Child.Detail>> =
        detailsRouter.state

    override fun isDetailShown(): Boolean {
        return detailsRouterState.value.activeChild.instance !is ProjectEditor.Child.Detail.None
    }

    private val _state = MutableValue(ProjectEditor.State(projectDef))
    override val state: Value<ProjectEditor.State> = _state

    init {
        lifecycle.subscribe(this)

        backPressedHandler.register {
            closeDetails()
        }

        detailsRouter.state.observe(lifecycle) {
            (it.activeChild.configuration as? DetailsRouter.Config.SceneEditor)?.let { sceneEditor ->
                selectedSceneDefFlow.tryEmit(sceneEditor.sceneDef)
            }
            updateCloseConfirmRequirement()
        }

        scope.launch {
            projectEditor.subscribeToBufferUpdates(null) {
                updateCloseConfirmRequirement()
            }
        }
    }

    private fun updateCloseConfirmRequirement() {
        _shouldConfirmClose.value = !isDetailShown() && hasUnsavedBuffers()
    }

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

    private fun onSceneSelected(sceneDef: SceneDef) {
        detailsRouter.showScene(sceneDef)

        if (isMultiPaneMode()) {
            listRouter.show()
        } else {
            listRouter.moveToBackStack()
        }
    }

    override fun setMultiPane(isMultiPane: Boolean) {
        _state.reduce { it.copy(isMultiPane = isMultiPane) }
        //isDetailsToolbarVisible.onNext(!isMultiPane)

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
        return projectEditor.hasDirtyBuffers()
    }

    override fun storeDirtyBuffers() {
        projectEditor.storeAllBuffers()
    }

    override fun onDestroy() {
        scope.cancel("ProjectEditor destroyed")
    }
}