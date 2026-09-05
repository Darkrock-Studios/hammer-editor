package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectDictionaryService
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.getScopeId
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.TypeQualifier
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

// Scopes an editor has opened. A temporary task that created the scope must not close it
// once an editor owns it, and an editor opening an existing temporary scope must still
// start the editor-only services.
private val editorScopes = atomic(emptySet<ScopeID>())

suspend fun KoinComponent.temporaryProjectTask(projectDef: ProjectDef, block: suspend (projectScope: Scope) -> Unit) {
	val scopeId = ProjectDefScope(projectDef).getScopeId()
	val hadToCreate = getKoin().getScopeOrNull(scopeId) == null
	val projScope = openProjectScope(projectDef, temporary = true)

	block(projScope)

	if (hadToCreate && scopeId !in editorScopes.value) {
		closeProjectScope(projScope, projectDef)
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
	val scopeId = defScope.getScopeId()

	val needsInit = getKoin().getScopeOrNull(scopeId) == null
	val projScope = getKoin().getOrCreateScope<ProjectDefScope>(scopeId, source = defScope)

	if (needsInit) {
		initializeProjectScope(projectDef, temporary)
	} else if (!temporary && markOpenedForEditing(scopeId)) {
		initializeEditorServices(projScope)
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
		if (!temporary && markOpenedForEditing(defScope.getScopeId())) {
			initializeEditorServices(projScope)
		}
	} ?: throw IllegalStateException("No scope found for $projectDef")
}

private fun initializeEditorServices(projScope: Scope) {
	projScope.get<ProjectDictionaryService>().initialize()
}

/** Returns true the first time [scopeId] is marked. */
private fun markOpenedForEditing(scopeId: ScopeID): Boolean {
	val before = editorScopes.getAndUpdate { it + scopeId }
	return scopeId !in before
}

fun closeProjectScope(projectScope: Scope, projectDef: ProjectDef) {
	Napier.d { "closeProjectScope: ${projectDef.name}" }
	editorScopes.update { it - ProjectDefScope(projectDef).getScopeId() }
	projectScope.close()
}

private inline fun <reified T : Any> Koin.getOrCreateScope(scopeId: ScopeID, source: Any? = null): Scope {
	val qualifier = TypeQualifier(T::class)
	return getScopeOrNull(scopeId) ?: createScope(scopeId, qualifier, source)
}
