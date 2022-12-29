package com.darkrockstudios.apps.hammer.common.notes

import com.arkivanov.decompose.ComponentContext
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

class NotesComponent(
    componentContext: ComponentContext,
    private val projectDef: ProjectDef,
    addMenu: (menu: MenuDescriptor) -> Unit,
    removeMenu: (id: String) -> Unit,
) : Notes {

}