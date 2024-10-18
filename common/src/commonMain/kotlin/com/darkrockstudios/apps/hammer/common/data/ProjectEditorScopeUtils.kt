package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
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
	val projScope = openProjectScope(projectDef)

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

suspend fun openProjectScope(projectDef: ProjectDef): Scope {
	val defScope = ProjectDefScope(projectDef)

	val needsInit = getKoin().getScopeOrNull(ProjectDefScope(projectDef).getScopeId()) == null
	val projScope = getKoin().getOrCreateScope<ProjectDefScope>(defScope.getScopeId(), source = defScope)

	if (needsInit) {
		initializeProjectScope(projectDef)
	}

	return projScope
}

suspend fun initializeProjectScope(projectDef: ProjectDef) {
	val defScope = ProjectDefScope(projectDef)
	getKoin().getScopeOrNull(defScope.getScopeId())?.let { projScope ->
		val projectEditor: SceneEditorRepository = projScope.get { parametersOf(projectDef) }
		projectEditor.initializeSceneEditor()

		val timeLineRepository: TimeLineRepository = projScope.get { parametersOf(projectDef) }
		timeLineRepository.initialize()
	} ?: throw IllegalStateException("No scope found for $projectDef")
}

fun closeProjectScope(projectScope: Scope, projectDef: ProjectDef) {
	Napier.d { "closeProjectScope: ${projectDef.name}" }

	val sceneEditorRepository: SceneEditorRepository = projectScope.get { parametersOf(projectDef) }
	val notesRepository: NotesRepository = projectScope.get { parametersOf(projectDef) }
	val timeLineRepository: TimeLineRepository = projectScope.get { parametersOf(projectDef) }

	sceneEditorRepository.close()
	notesRepository.close()
	projectScope.close()
	timeLineRepository.close()
}

private inline fun <reified T : Any> Koin.getOrCreateScope(scopeId: ScopeID, source: Any? = null): Scope {
	val qualifier = TypeQualifier(T::class)
	return getScopeOrNull(scopeId) ?: createScope(scopeId, qualifier, source)
}