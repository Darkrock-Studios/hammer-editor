package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.getScopeId
import org.koin.core.component.getScopeName
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.ext.getFullName

/**
 * Keyed on the project's path alone: Koin's [getScopeId] is the class name plus [hashCode]. A def
 * whose id was healed from a lost `project.toml` while the project is open must still find the
 * open scope, or the project would read as not in use and get a second scope.
 */
data class ProjectDefScope(val projectDef: ProjectDef) : KoinScopeComponent {
	override val scope: Scope by lazy {
		val scopeId = getScopeId()
		val qualifier = getScopeName()
		getKoin().getOrCreateScope(scopeId, qualifier)
	}

	override fun equals(other: Any?): Boolean =
		other is ProjectDefScope && other.projectDef.path == projectDef.path

	override fun hashCode(): Int = projectDef.path.hashCode()

	companion object {
		/** [getScopeId] for the project at [projectDir], for a caller with no def for it yet. Mirrors Koin's formula. */
		fun scopeIdFor(projectDir: HPath): ScopeID = ProjectDefScope::class.getFullName() + "@" + projectDir.hashCode()
	}
}
