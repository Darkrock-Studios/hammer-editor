package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectDictionaryService
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.getScopeId
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.TypeQualifier
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

private val temporaryScopeLock = reentrantLock()
private val temporaryScopeUsers = mutableMapOf<ScopeID, Int>()
private val temporaryScopesToClose = mutableSetOf<ScopeID>()

/**
 * Opens a project scope for [block], closing it afterwards only if this call is what brought it
 * into existence and nothing else is still using it.
 *
 * Thar be dragons: concurrent temporary tasks on one project must be counted, not each decide for
 * themselves. Two overlapping tasks both see a scope they did not create, and whichever finishes
 * first would otherwise close it out from under the other, which then fails with
 * `ClosedScopeException` partway through. A background sync and a capture racing on the same
 * project is the ordinary case, not a rare one.
 */
suspend fun KoinComponent.temporaryProjectTask(projectDef: ProjectDef, block: suspend (projectScope: Scope) -> Unit) {
	val scopeId = ProjectDefScope(projectDef).getScopeId()

	temporaryScopeLock.withLock {
		val users = temporaryScopeUsers[scopeId] ?: 0
		// Only the first temporary user can be the one creating the scope. A scope that was already
		// open for real belongs to whoever opened it and is never closed here.
		if (users == 0 && getKoin().getScopeOrNull(scopeId) == null) {
			temporaryScopesToClose += scopeId
		}
		temporaryScopeUsers[scopeId] = users + 1
	}

	val projScope = openProjectScope(projectDef, temporary = true)

	try {
		block(projScope)
	} finally {
		val shouldClose = temporaryScopeLock.withLock {
			val remaining = (temporaryScopeUsers[scopeId] ?: 1) - 1
			if (remaining > 0) {
				temporaryScopeUsers[scopeId] = remaining
				false
			} else {
				temporaryScopeUsers.remove(scopeId)
				temporaryScopesToClose.remove(scopeId)
			}
		}
		if (shouldClose) {
			closeProjectScope(projScope, projectDef)
		}
	}
}

fun createProjectScope(projectDef: ProjectDef): Scope {
	val alreadyCreated = getKoin().getScopeOrNull(ProjectDefScope(projectDef).getScopeId()) != null
	if (alreadyCreated) error("Scope was already created")

	val defScope = ProjectDefScope(projectDef)
	val projScope = getKoin().createScope<ProjectDefScope>(defScope.getScopeId(), source = defScope)

	return projScope
}

suspend fun openProjectScope(projectDef: ProjectDef, temporary: Boolean = false): Scope {
	val defScope = ProjectDefScope(projectDef)

	val needsInit = getKoin().getScopeOrNull(ProjectDefScope(projectDef).getScopeId()) == null
	val projScope = getKoin().getOrCreateScope<ProjectDefScope>(defScope.getScopeId(), source = defScope)

	if (needsInit) {
		initializeProjectScope(projectDef, temporary)
	}

	return projScope
}

suspend fun initializeProjectScope(projectDef: ProjectDef, temporary: Boolean = false) {
	val defScope = ProjectDefScope(projectDef)
	getKoin().getScopeOrNull(defScope.getScopeId())?.let { projScope ->
		// Creates the service (activating its autosave side-effect subscription from project open)
		// and runs the scene-editor init sequence: tree, then content (autosave), then metadata.
		val sceneEditorService: SceneEditorService = projScope.get()
		sceneEditorService.initialize()

		val timeLineRepository: TimeLineRepository = projScope.get { parametersOf(projectDef) }
		timeLineRepository.initialize()

		// Skipped for temporary scopes (background sync, import): loading session words
		// there only churns the shared checker while the sync rewrites entries.
		if (!temporary) {
			projScope.get<ProjectDictionaryService>().initialize()
		}
	} ?: throw IllegalStateException("No scope found for $projectDef")
}

fun closeProjectScope(projectScope: Scope, projectDef: ProjectDef) {
	Napier.d { "closeProjectScope: ${projectDef.name}" }
	projectScope.close()
}

private inline fun <reified T : Any> Koin.getOrCreateScope(scopeId: ScopeID, source: Any? = null): Scope {
	val qualifier = TypeQualifier(T::class)
	return getScopeOrNull(scopeId) ?: createScope(scopeId, qualifier, source)
}