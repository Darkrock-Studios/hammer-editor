package com.darkrockstudios.apps.hammer.common.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.parcelable.Parcelable
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope

abstract class ProjectComponentBase(
	val projectDef: ProjectDef,
	componentContext: ComponentContext
) : ComponentBase(componentContext), ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)
}

abstract class SavableProjectComponentBase<S : Parcelable>(
	val projectDef: ProjectDef,
	componentContext: ComponentContext
) : SavableComponent<S>(componentContext), ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)
}