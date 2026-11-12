package com.darkrockstudios.apps.hammer.common.data.projectplugins

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The plugins a project has off: those it turned off, and every plugin of a kind but the one it runs
 * ([oneOfEachKind]), which follows the project's language until the writer picks one.
 */
class ProjectDisabledPluginsUseCase(
	private val projectDef: ProjectDef,
	private val projectPlugins: ProjectPluginsRepository,
	private val projectData: ProjectDataRepository,
	kindSources: List<PluginKindSource>,
) {
	private val kinded: Flow<List<KindedPlugin>> =
		if (kindSources.isEmpty()) flowOf(emptyList())
		else combine(kindSources.map { it.kindedPlugins }) { lists -> lists.flatMap { it } }

	// Without the project data, plugins of a kind fall back to the first installed.
	private val language: Flow<String?> = flow {
		runCatching { projectData.load() }.onFailure { Napier.w(it) { "Project data unreadable for its plugins' language" } }
		emitAll(projectData.state.map { it?.data?.language })
	}.distinctUntilChanged()

	/** Follows every change to what the project turned off, its language, and the plugins. */
	val disabledPlugins: Flow<Set<String>> =
		combine(projectPlugins.disabledPlugins(projectDef.id), kinded, language, ::oneOfEachKind)
			.distinctUntilChanged()

	/** [disabledPlugins] with what the project turned off as the file has it now; see [ProjectPluginsRepository.readDisabledPlugins]. */
	suspend fun read(): Set<String> =
		oneOfEachKind(projectPlugins.readDisabledPlugins(projectDef.id), kinded.first(), projectData.load().data.language)
}
