package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.getScopeId
import org.koin.core.component.getScopeName
import org.koin.core.scope.Scope

data class ProjectDefScope(val projectDef: ProjectDef) : KoinScopeComponent {
	override val scope: Scope by lazy {
		val scopeId = getScopeId()
		val qualifier = getScopeName()
		getKoin().getOrCreateScope(scopeId, qualifier)
	}
}