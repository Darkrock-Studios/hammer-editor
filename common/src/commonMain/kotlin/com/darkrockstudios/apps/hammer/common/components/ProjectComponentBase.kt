package com.darkrockstudios.apps.hammer.common.components

import com.arkivanov.decompose.ComponentContext
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import org.koin.core.component.get
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.Qualifier
import org.koin.mp.KoinPlatformTools

abstract class ProjectComponentBase(
	protected val projectDef: ProjectDef,
	componentContext: ComponentContext
) : ComponentBase(componentContext) {
	val projectScope = ProjectDefScope(projectDef)
}

/**
 * Injects objects from the Project Scope which all require a ProjectDef
 * parameter during injection.
 */
inline fun <reified T : Any> ProjectComponentBase.projectInject(
	qualifier: Qualifier? = null,
	mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
	noinline parameters: ParametersDefinition? = null
): Lazy<T> =
	lazy(mode) {
		val newParameters = if (parameters != null) {
			val params = parameters()
			parametersOf(arrayOf(params.values.toMutableList().add(projectScope.projectDef)))
		} else {
			parametersOf(projectScope.projectDef)
		}
		projectScope.get<T>(qualifier) { newParameters }
	}