package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.getScopeName
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID

/**
 * The scope is keyed on the project's path alone, so a def whose id was healed while the project
 * is open still finds the open scope.
 */
class ProjectDefScope(val projectDef: ProjectDef) : KoinScopeComponent {
	val scopeId: ScopeID = scopeIdFor(projectDef.path)

	override val scope: Scope by lazy {
		getKoin().getOrCreateScope(scopeId, getScopeName())
	}

	companion object {
		fun scopeIdFor(projectDir: HPath): ScopeID = "project:${projectDir.path}"
	}
}
