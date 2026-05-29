package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.debounceUntilQuiescentBy
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.time.Duration.Companion.milliseconds

/**
 * Signal emitted when a scene buffer is persisted to its temp (autosave) file, so callers can
 * hang side-effects (writing-activity timestamps, stats) off saves without this repository
 * reaching up into those collaborators. Full-save side-effects are handled inline by the
 * caller of [persistBuffer].
 */
data class BufferPersistedEvent(
	val sceneId: Int,
	val source: UpdateSource,
)

/**
 * The buffer / editing engine (responsibilities C + D of the old SceneEditorRepository).
 *
 * Owns the in-memory scene buffers, the content debounce pipeline, temp-buffer autosave jobs,
 * dirty tracking, the buffer-update flow, and its own [editorScope] + [ScopeCallback] for temp
 * cleanup on project close.
 *
 * It is path-agnostic for full load/store: callers (SceneEditorService) resolve the on-disk path
 * via SceneRepository and pass it in. Temp autosave uses the flat buffer directory and needs no
 * tree path, so the debounce engine runs autonomously here.
 *
 * Deps: [SceneDatasource] only.
 */
class SceneContentRepository(
	private val projectDef: ProjectDef,
	private val sceneDatasource: SceneDatasource,
) : ScopeCallback, ProjectScoped, KoinComponent {

	override val projectScope = ProjectDefScope(projectDef)

	init {
		projectScope.scope.registerCallback(this)
	}

	private val dispatcherMain by injectMainDispatcher()
	private val dispatcherDefault by injectDefaultDispatcher()
	private val editorScope = CoroutineScope(dispatcherDefault)

	private val _contentFlow = MutableSharedFlow<SceneContentUpdate>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	private val contentFlow: SharedFlow<SceneContentUpdate> = _contentFlow
	private var contentUpdateJob: Job? = null

	private val _bufferUpdateFlow = MutableSharedFlow<SceneBuffer>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	private val bufferUpdateFlow: SharedFlow<SceneBuffer> = _bufferUpdateFlow

	private val _bufferPersistedFlow = MutableSharedFlow<BufferPersistedEvent>(
		extraBufferCapacity = 8,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val bufferPersistedFlow: SharedFlow<BufferPersistedEvent> = _bufferPersistedFlow

	private val sceneBuffersLock = reentrantLock()
	private val sceneBuffers = mutableMapOf<Int, SceneBuffer>()

	private val storeTempJobs = mutableMapOf<Int, Job>()

	/** Loads any temp (unsaved) buffers from disk and starts the content debounce pipeline. */
	suspend fun initialize() {
		val tempContent = sceneDatasource.getSceneTempBufferContents()
		for (content in tempContent) {
			val buffer = SceneBuffer(content, true, UpdateSource.Repository)
			updateSceneBuffer(buffer)
		}

		contentUpdateJob = editorScope.launch {
			contentFlow.debounceUntilQuiescentBy({ it.content.scene.id }, BUFFER_COOL_DOWN)
				.collect { contentUpdate ->
					if (updateSceneBufferContent(contentUpdate.content, contentUpdate.source)) {
						launchSaveJob(contentUpdate.content.scene)
					}
				}
		}
	}

	fun subscribeToBufferUpdates(
		sceneDef: SceneItem?,
		scope: CoroutineScope,
		onBufferUpdate: suspend (SceneBuffer) -> Unit
	): Job {
		return scope.launch {
			bufferUpdateFlow.collect { newBuffer ->
				if (sceneDef == null || newBuffer.content.scene.id == sceneDef.id) {
					withContext(dispatcherMain) {
						onBufferUpdate(newBuffer)
					}
				}
			}
		}
	}

	fun onContentChanged(content: SceneContent, source: UpdateSource) {
		editorScope.launch {
			val update = SceneContentUpdate(content, source)
			_contentFlow.emit(update)
		}
	}

	private fun updateSceneBufferContent(content: SceneContent, source: UpdateSource): Boolean {
		if (source == UpdateSource.Editor) {
			val newBuffer = SceneBuffer(content, dirty = true, source = source)
			updateSceneBuffer(newBuffer)
			return true
		}

		val oldBuffer = sceneBuffersLock.withLock { sceneBuffers[content.scene.id] }
		return if (content != oldBuffer?.content || content.platformRepresentation?.stateCompare(oldBuffer.content.platformRepresentation) == true) {
			val newBuffer = SceneBuffer(content, source != UpdateSource.Sync, source)
			updateSceneBuffer(newBuffer)
			true
		} else {
			false
		}
	}

	private fun updateSceneBuffer(newBuffer: SceneBuffer) {
		sceneBuffersLock.withLock {
			sceneBuffers[newBuffer.content.scene.id] = newBuffer
		}
		_bufferUpdateFlow.tryEmit(newBuffer)
	}

	fun getSceneBuffer(sceneDef: SceneItem): SceneBuffer? = getSceneBuffer(sceneDef.id)
	fun getSceneBuffer(sceneId: Int): SceneBuffer? = sceneBuffersLock.withLock { sceneBuffers[sceneId] }

	fun hasDirtyBuffer(sceneId: Int): Boolean =
		getSceneBuffer(sceneId)?.dirty == true

	fun hasDirtyBuffers(): Boolean = sceneBuffersLock.withLock {
		sceneBuffers.any { it.value.dirty }
	}

	fun getDirtyBufferIds(): Set<Int> = sceneBuffersLock.withLock {
		sceneBuffers
			.filter { it.value.dirty }
			.map { it.key }
			.toSet()
	}

	private fun getDirtyBufferScenes(): List<SceneItem> = sceneBuffersLock.withLock {
		sceneBuffers.filter { it.value.dirty }.map { it.value.content.scene }
	}

	/** Loads a buffer from disk at the given path and caches it. Does not set a writing baseline. */
	fun loadBuffer(sceneItem: SceneItem, scenePath: HPath): SceneBuffer {
		val cachedBuffer = getSceneBuffer(sceneItem)
		return if (cachedBuffer != null) {
			cachedBuffer
		} else {
			val content = sceneDatasource.loadSceneBuffer(scenePath)
			val newBuffer = SceneBuffer(
				SceneContent(sceneItem, content),
				source = UpdateSource.Repository
			)
			updateSceneBuffer(newBuffer)
			newBuffer
		}
	}

	/**
	 * The scene's current content: the in-memory buffer if present, otherwise the on-disk
	 * content resolved from the filesystem. Does not cache or set a baseline — for fire-and-forget
	 * readers (e.g. draft creation) that just need the text.
	 */
	fun getCurrentSceneContent(sceneItem: SceneItem): String {
		val buffered = getSceneBuffer(sceneItem.id)
		return if (buffered != null) {
			buffered.content.coerceMarkdown()
		} else {
			val scenePath = sceneDatasource.resolveScenePathFromFilesystem(sceneItem.id)
				?: error("Scene file not found for ID ${sceneItem.id}")
			sceneDatasource.loadSceneBuffer(scenePath)
		}
	}

	/** Persists a buffer to its on-disk path, marks it clean, and clears its temp file. */
	suspend fun persistBuffer(buffer: SceneBuffer, scenePath: HPath): Boolean {
		val success = sceneDatasource.storeSceneBuffer(buffer, scenePath)
		if (success) {
			val cleanBuffer = buffer.copy(dirty = false)
			updateSceneBuffer(cleanBuffer)
			clearTempScene(buffer.content.scene)
		}
		return success
	}

	/**
	 * Drops the in-memory buffer + temp file and reloads from disk. Returns the reloaded buffer
	 * (so the caller can re-establish a writing baseline), or null if nothing was buffered.
	 */
	fun discardBuffer(sceneItem: SceneItem, scenePath: HPath): SceneBuffer? {
		val wasPresent = sceneBuffersLock.withLock {
			sceneBuffers.remove(sceneItem.id) != null
		}
		return if (wasPresent) {
			clearTempScene(sceneItem)
			loadBuffer(sceneItem, scenePath)
		} else {
			null
		}
	}

	/** Stores all currently-dirty buffers to disk via [persist], one per dirty scene. */
	suspend fun forEachDirtyBuffer(persist: suspend (SceneItem) -> Unit) {
		getDirtyBufferScenes().forEach { persist(it) }
	}

	private fun launchSaveJob(sceneDef: SceneItem) {
		val job = storeTempJobs[sceneDef.id]
		job?.cancel("Starting a new one")
		storeTempJobs[sceneDef.id] = editorScope.launch {
			storeTempSceneBuffer(sceneDef)
			storeTempJobs.remove(sceneDef.id)
		}
	}

	private suspend fun storeTempSceneBuffer(sceneItem: SceneItem): Boolean {
		val buffer = getSceneBuffer(sceneItem)
		if (buffer == null) {
			Napier.e { "Failed to store scene: ${sceneItem.id} - ${sceneItem.name}, no buffer present" }
			return false
		}
		val success = sceneDatasource.storeTempSceneBuffer(buffer)
		if (success) {
			_bufferPersistedFlow.tryEmit(BufferPersistedEvent(sceneItem.id, buffer.source))
		}
		return success
	}

	private fun clearTempScene(sceneItem: SceneItem) = sceneDatasource.clearTempScene(sceneItem)

	override fun onScopeClose(scope: Scope) {
		contentUpdateJob?.cancel("Editor Closed")
		runBlocking {
			storeTempJobs.forEach { it.value.join() }
		}
		editorScope.cancel("Editor Closed")
		// During a proper shutdown, we clear any remaining temp buffers that haven't been saved yet
		sceneDatasource.getSceneTempBufferContents().forEach {
			clearTempScene(it.scene)
		}
		Napier.i("SceneContentRepository Closed.")
	}

	companion object {
		val BUFFER_COOL_DOWN = 500.milliseconds
	}
}
