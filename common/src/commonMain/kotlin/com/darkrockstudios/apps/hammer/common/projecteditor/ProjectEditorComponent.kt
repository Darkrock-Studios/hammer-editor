package com.darkrockstudios.apps.hammer.common.projecteditor

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.RouterState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.observe
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.data.Scene
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class ProjectEditorComponent(
    componentContext: ComponentContext,
    private val project: Project,
    addMenu: (menu: MenuDescriptor) -> Unit,
    removeMenu: (id: String) -> Unit,
) : ProjectEditor, ComponentContext by componentContext {

    //private val isDetailsToolbarVisible = BehaviorSubject(!_models.value.isMultiPane)
    private val selectedSceneFlow = MutableSharedFlow<Scene?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val listRouter =
        ListRouter(
            componentContext = this,
            project = project,
            selectedScene = selectedSceneFlow,
            onSceneSelected = ::onSceneSelected
        )

    override val listRouterState: Value<RouterState<*, ProjectEditor.Child.List>> =
        listRouter.state

    private val detailsRouter =
        DetailsRouter(
            componentContext = this,
            onFinished = {},
            addMenu = addMenu,
            closeDetails = ::closeDetails,
            removeMenu = removeMenu
        )

    override val detailsRouterState: Value<RouterState<*, ProjectEditor.Child.Detail>> =
        detailsRouter.state

    private val _state = MutableValue(ProjectEditor.State(project))
    override val state: Value<ProjectEditor.State> = _state

    init {
        backPressedHandler.register {
            closeDetails()
        }

        detailsRouter.state.observe(lifecycle) {
            (it.activeChild.configuration as? DetailsRouter.Config.SceneEditor)?.let { sceneEditor ->
                selectedSceneFlow.tryEmit(sceneEditor.scene)
            }
        }
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

    private fun onSceneSelected(scene: Scene) {
        detailsRouter.showScene(scene)

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
}