package com.darkrockstudios.apps.hammer.common.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepository
import io.github.aakira.napier.Napier
import org.koin.core.component.inject

class ProjectRootComponent(
    componentContext: ComponentContext,
    private val projectDef: ProjectDef,
    addMenu: (menu: MenuDescriptor) -> Unit,
    removeMenu: (id: String) -> Unit,
) : ComponentBase(componentContext), ProjectRoot {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(projectDef)

    private val _backEnabled = MutableValue(true)
    override val backEnabled = _backEnabled

    private val _shouldConfirmClose = MutableValue(false)
    override val shouldConfirmClose = _shouldConfirmClose

    private val router = ProjectRootRouter(
        componentContext,
        projectDef,
        addMenu,
        removeMenu,
        ::updateCloseConfirmRequirement,
        scope
    )

    override val routerState: Value<ChildStack<*, ProjectRoot.Destination>> = router.state
    override fun showEditor() {
        router.showEditor()
    }

    override fun showNotes() {
        router.showNotes()
    }

    override fun hasUnsavedBuffers(): Boolean {
        return projectEditor.hasDirtyBuffers()
    }

    override fun storeDirtyBuffers() {
        projectEditor.storeAllBuffers()
    }

    private fun updateCloseConfirmRequirement() {
        val isAtRoot = router.isAtRoot()
        val unsaved = hasUnsavedBuffers()
        Napier.d { "isAtRoot: $isAtRoot unsaved: $unsaved" }
        _shouldConfirmClose.value = hasUnsavedBuffers() && router.isAtRoot()
        _backEnabled.value = router.isAtRoot()
    }

    override fun onDestroy() {
        super.onDestroy()
        Napier.i { "ProjectRootComponent closing Project Editor" }
        projectRepository.closeEditor(projectDef)
    }

    init {
        projectEditor.subscribeToBufferUpdates(null, scope) {
            Napier.d { "subscribeToBufferUpdates" }
            updateCloseConfirmRequirement()
        }
    }
}