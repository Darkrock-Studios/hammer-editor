package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext

/**
 * Repository responsible for persisting statistics and tracking dirty state.
 * Does NOT perform calculations - that's the responsibility of StatisticsService.
 */
class StatisticsRepository(
	projectDef: ProjectDef,
	private val statisticsDatasource: StatisticsDatasource,
) : ScopeCallback, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val repositoryScope = CoroutineScope(dispatcherDefault)

	private val _statsFlow = MutableSharedFlow<ProjectStatistics>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val statsFlow: SharedFlow<ProjectStatistics> = _statsFlow

	private val _isDirty = MutableStateFlow(false)
	val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

	init {
		projectScope.scope.registerCallback(this)
	}

	/**
	 * Load statistics from cache.
	 * Returns null if cache doesn't exist or is corrupted.
	 */
	suspend fun loadStatistics(): ProjectStatistics? {
		val stats = statisticsDatasource.loadStatistics()
		if (stats != null) {
			_isDirty.value = stats.isDirty
			_statsFlow.emit(stats)
		}
		return stats
	}

	/**
	 * Save statistics to cache.
	 */
	suspend fun saveStatistics(stats: ProjectStatistics) {
		statisticsDatasource.saveStatistics(stats)
		_isDirty.value = stats.isDirty
		_statsFlow.emit(stats)
		Napier.d("Statistics saved to cache")
	}

	/**
	 * Mark the statistics cache as dirty.
	 * This persists the dirty flag so it survives app restarts.
	 */
	fun markDirty() {
		if (_isDirty.value) return // Already dirty, no need to update

		_isDirty.value = true
		repositoryScope.launch {
			val currentStats = statisticsDatasource.loadStatistics()
			if (currentStats != null && !currentStats.isDirty) {
				statisticsDatasource.saveStatistics(currentStats.copy(isDirty = true))
				Napier.d("Statistics marked as dirty")
			}
		}
	}

	/**
	 * Clear the dirty flag (called after recalculation).
	 */
	fun clearDirty() {
		_isDirty.value = false
	}

	/**
	 * Check if cache exists.
	 */
	fun cacheExists(): Boolean = statisticsDatasource.exists()

	override fun onScopeClose(scope: Scope) {
		repositoryScope.cancel("StatisticsRepository Closed")
	}
}
