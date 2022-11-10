package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.observe
import com.arkivanov.decompose.value.reduce
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepository
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.core.component.inject

class ProjectEditorComponent(
    componentContext: ComponentContext,
    private val projectDef: ProjectDef,
    addMenu: (menu: MenuDescriptor) -> Unit,
    removeMenu: (id: String) -> Unit,
) : ComponentBase(componentContext), ProjectEditor {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(projectDef)

    //private val isDetailsToolbarVisible = BehaviorSubject(!_models.value.isMultiPane)
    private val selectedSceneItemFlow = MutableSharedFlow<SceneItem?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val detailsRouter =
        DetailsRouter(
            componentContext = this,
            addMenu = addMenu,
            closeDetails = ::closeDetails,
            removeMenu = removeMenu
        )

    private val listRouter =
        ListRouter(
            componentContext = this,
            projectDef = projectDef,
            selectedSceneItem = selectedSceneItemFlow,
            onSceneSelected = ::onSceneSelected
        )

    override val listRouterState: Value<ChildStack<*, ProjectEditor.Child.List>> =
        listRouter.state

    private val _shouldConfirmClose = MutableValue(false)
    override val shouldConfirmClose = _shouldConfirmClose

    override val detailsRouterState: Value<ChildStack<*, ProjectEditor.Child.Detail>> =
        detailsRouter.state

    override fun isDetailShown(): Boolean {
        return detailsRouterState.value.active.instance !is ProjectEditor.Child.Detail.None
    }

    private val _state = MutableValue(ProjectEditor.State(projectDef))
    override val state: Value<ProjectEditor.State> = _state

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

    private fun onSceneSelected(sceneItem: SceneItem) {
        detailsRouter.showScene(sceneItem)

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
        super.onDestroy()
        projectRepository.closeEditor(projectDef)
    }

    private val backButtonHandler = object : BackCallback() {
        override fun onBack() {
            closeDetails()
        }
    }

    init {
        backHandler.register(backButtonHandler)

        detailsRouter.state.observe(lifecycle) {
            (it.active.configuration as? DetailsRouter.Config.SceneEditor)?.let { sceneEditor ->
                selectedSceneItemFlow.tryEmit(sceneEditor.sceneDef)
            }
            updateCloseConfirmRequirement()
        }

        projectEditor.subscribeToBufferUpdates(null, scope) {
            updateCloseConfirmRequirement()
        }
    }
}