package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectDictionaryService
import io.github.aakira.napier.Napier
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.getScopeId
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.TypeQualifier
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

suspend fun KoinComponent.temporaryProjectTask(projectDef: ProjectDef, block: suspend (projectScope: Scope) -> Unit) {
	val hadToCreate = getKoin().getScopeOrNull(ProjectDefScope(projectDef).getScopeId()) == null
	val projScope = openProjectScope(projectDef, temporary = true)

	block(projScope)

	if (hadToCreate) {
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