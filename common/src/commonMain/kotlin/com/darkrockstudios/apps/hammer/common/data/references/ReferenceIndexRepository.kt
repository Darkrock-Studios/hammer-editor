package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext

class ReferenceIndexRepository(
	projectDef: ProjectDef,
	private val datasource: ReferenceIndexDatasource,
) : ScopeCallback, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val repositoryScope = CoroutineScope(dispatcherDefault)

	private val _indexFlow = MutableSharedFlow<ReferenceIndex>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val indexFlow: SharedFlow<ReferenceIndex> = _indexFlow

	private val _isDirty = MutableStateFlow(false)
	val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

	private val mutex = reentrantLock()
	private var current: ReferenceIndex? = null

	init {
		projectScope.scope.registerCallback(this)
	}

	suspend fun loadIndex(): ReferenceIndex? {
		val index = datasource.loadIndex()
		if (index != null) {
			mutex.withLock { current = index }
			_isDirty.value = index.isDirty
			_indexFlow.emit(index)
		}
		return index
	}

	suspend fun saveIndex(index: ReferenceIndex) {
		datasource.saveIndex(index)
		mutex.withLock { current = index }
		_isDirty.value = index.isDirty
		_indexFlow.emit(index)
	}

	fun markDirty() {
		if (_isDirty.value) return
		_isDirty.value = true
		repositoryScope.launch {
			val cached = datasource.loadIndex()
			if (cached != null && !cached.isDirty) {
				val updated = cached.copy(isDirty = true)
				datasource.saveIndex(updated)
				mutex.withLock { current = updated }
				_indexFlow.emit(updated)
				Napier.d("Reference index marked as dirty")
			}
		}
	}

	fun clearDirty() {
		_isDirty.value = false
	}

	fun cacheExists(): Boolean = datasource.exists()

	suspend fun applySceneDelta(sceneId: Int, added: Set<Int>, removed: Set<Int>) {
		if (added.isEmpty() && removed.isEmpty()) return

		val snapshot = mutex.withLock { current } ?: datasource.loadIndex()
		if (snapshot == null || snapshot.isDirty) {
			markDirty()
			return
		}

		val map = snapshot.entryToScenes.toMutableMap()
		for (entryId in added) {
			val scenes = map[entryId].orEmpty()
			map[entryId] = scenes + sceneId
		}
		for (entryId in removed) {
			val scenes = map[entryId].orEmpty() - sceneId
			if (scenes.isEmpty()) {
				map.remove(entryId)
			} else {
				map[entryId] = scenes
			}
		}

		saveIndex(snapshot.copy(entryToScenes = map))
	}

	suspend fun markEntryDeleted(entryId: Int) {
		val snapshot = mutex.withLock { current } ?: datasource.loadIndex()
		if (snapshot == null || snapshot.isDirty) {
			markDirty()
			return
		}
		if (entryId !in snapshot.entryToScenes) return

		val map = snapshot.entryToScenes.toMutableMap().also { it.remove(entryId) }
		saveIndex(snapshot.copy(entryToScenes = map))
	}

	suspend fun markSceneDeleted(sceneId: Int) {
		val snapshot = mutex.withLock { current } ?: datasource.loadIndex()
		if (snapshot == null || snapshot.isDirty) {
			markDirty()
			return
		}

		val map = snapshot.entryToScenes.toMutableMap()
		var changed = false
		val iter = map.entries.iterator()
		while (iter.hasNext()) {
			val (entryId, scenes) = iter.next()
			if (sceneId in scenes) {
				val updated = scenes - sceneId
				if (updated.isEmpty()) {
					iter.remove()
				} else {
					map[entryId] = updated
				}
				changed = true
			}
		}
		if (changed) saveIndex(snapshot.copy(entryToScenes = map))
	}

	override fun onScopeClose(scope: Scope) {
		repositoryScope.cancel("ReferenceIndexRepository Closed")
	}
}
