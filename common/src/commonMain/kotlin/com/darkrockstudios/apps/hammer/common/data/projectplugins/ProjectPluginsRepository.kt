package com.darkrockstudios.apps.hammer.common.data.projectplugins

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

/** The installed plugins each project turned off on this device. Every other enabled plugin is on. */
class ProjectPluginsRepository(
	private val datasource: ProjectPluginsDatasource,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()

	// Deletes are reported from blocking code, so their cleanup runs here.
	private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }
	private val mutex = Mutex()
	private val state = MutableStateFlow<Map<ProjectId, Set<String>>?>(null)

	/** [project]'s turned-off plugins, following every change made here. */
	fun disabledPlugins(project: ProjectId): Flow<Set<String>> = flow {
		loaded()
		emitAll(state.map { it?.get(project).orEmpty() })
	}.distinctUntilChanged()

	/**
	 * [project]'s turned-off plugins as the file has them now, for a process, such as a long-running
	 * CLI one, that another process may have changed them under.
	 */
	suspend fun readDisabledPlugins(project: ProjectId): Set<String> = mutex.withLock {
		val read = withContext(ioDispatcher) { datasource.load() }
		state.value = read
		read[project].orEmpty()
	}

	suspend fun setPluginEnabled(project: ProjectId, pluginId: String, enabled: Boolean) = update { current ->
		val plugins = current[project].orEmpty()
		current + (project to if (enabled) plugins - pluginId else plugins + pluginId)
	}

	/**
	 * Keeps each project's choices with it as [projectsRepository] changes its id or deletes it. A
	 * copy given a new id while another project keeps the old one starts with the same choices.
	 */
	fun followProjectChanges(projectsRepository: ProjectsRepository) {
		projectsRepository.addProjectIdChangeListener { _, oldId, newId ->
			val oldIdKept = projectsRepository.findProject(oldId) != null
			update { current ->
				val plugins = current[oldId] ?: return@update current
				val moved = current + (newId to plugins)
				if (oldIdKept) moved else moved - oldId
			}
		}
		projectsRepository.addProjectDeletedListener { projectDef ->
			scope.launch {
				if (projectsRepository.findProject(projectDef.id) == null) update { it - projectDef.id }
			}
		}
	}

	private suspend fun update(transform: (Map<ProjectId, Set<String>>) -> Map<ProjectId, Set<String>>) =
		mutex.withLock {
			val current = state.value ?: withContext(ioDispatcher) { datasource.load() }
			val next = transform(current)
			if (next == current) return@withLock
			withContext(ioDispatcher) { datasource.store(next) }
			state.value = next
		}

	private suspend fun loaded() = mutex.withLock {
		if (state.value == null) state.value = withContext(ioDispatcher) { datasource.load() }
	}
}
