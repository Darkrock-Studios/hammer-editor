package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.mp.KoinPlatformTools

interface ProjectScoped : KoinComponent {
	val projectScope: ProjectDefScope
}

/**
 * Injects objects from the Project Scope which all require a ProjectDef
 * parameter during injection.
 */
inline fun <reified T : Any> ProjectScoped.projectInject(
	qualifier: Qualifier? = null,
	mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
	noinline parameters: ParametersDefinition? = null
): Lazy<T> =
	lazy(mode) {
		projectScope.get<T>(qualifier, parameters)
	}

/**
 * Resolves from the Project Scope immediately, at construction time. Use instead of
 * [projectInject] for any dependency a component touches in onDestroy: on project close the
 * scope is closed before child components are destroyed, so a first lazy resolution there
 * throws ClosedScopeException.
 */
inline fun <reified T : Any> ProjectScoped.projectGet(
	qualifier: Qualifier? = null,
	noinline parameters: ParametersDefinition? = null
): T = projectScope.get(qualifier, parameters)