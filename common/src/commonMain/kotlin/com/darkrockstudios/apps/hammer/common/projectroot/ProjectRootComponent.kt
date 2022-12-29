package com.darkrockstudios.apps.hammer.common.projectroot

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
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
    private val addMenu: (menu: MenuDescriptor) -> Unit,
    private val removeMenu: (id: String) -> Unit,
) : ComponentBase(componentContext), ProjectRoot {

    private val projectRepository: ProjectRepository by inject()
    private val projectEditor = projectRepository.getProjectEditor(projectDef)

    private val router = ProjectRootRouter(
        componentContext,
        projectDef,
        addMenu,
        removeMenu
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

    override fun onDestroy() {
        super.onDestroy()
        Napier.i { "ProjectRootComponent closing Project Editor" }
        projectRepository.closeEditor(projectDef)
    }
}