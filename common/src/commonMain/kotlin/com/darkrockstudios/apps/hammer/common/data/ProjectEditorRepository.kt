package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.numDigits
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.DurationUnit
import kotlin.time.toDuration

abstract class ProjectEditorRepository(
    val projectDef: ProjectDef,
    private val projectsRepository: ProjectsRepository
) {
    private var nextSceneId: Int = 0

    private val editorScope = CoroutineScope(defaultDispatcher)
    private val contentChannel = Channel<SceneContent>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _bufferUpdateChannel = MutableSharedFlow<SceneBuffer>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val bufferUpdateChannel: SharedFlow<SceneBuffer> = _bufferUpdateChannel

    fun subscribeToBufferUpdates(
        sceneDef: SceneDef?,
        scope: CoroutineScope,
        onBufferUpdate: (SceneBuffer) -> Unit
    ) {
        scope.launch {
            bufferUpdateChannel.collect { newBuffer ->
                if (sceneDef == null || newBuffer.content.sceneDef.id == sceneDef.id) {
                    onBufferUpdate(newBuffer)
                }
            }
        }
    }

    private val sceneBuffers = mutableMapOf<Int, SceneBuffer>()

    private val storeTempJobs = mutableMapOf<Int, Job>()
    private fun launchSaveJob(sceneDef: SceneDef) {
        val job = storeTempJobs[sceneDef.id]
        job?.cancel("Starting a new one")
        storeTempJobs[sceneDef.id] = editorScope.launch {
            delay(2.toDuration(DurationUnit.SECONDS))
            storeTempSceneBuffer(sceneDef)
            storeTempJobs.remove(sceneDef.id)
        }
    }

    protected fun cancelTempStoreJob(sceneDef: SceneDef) {
        storeTempJobs.remove(sceneDef.id)?.cancel("cancelTempStoreJob")
    }

    /**
     * This needs to be called after instantiation
     */
    fun initializeProjectEditor() {
        val lastSceneId = findLastSceneId()
        if (lastSceneId != null) {
            setLastSceneId(lastSceneId)
        } else {
            setLastSceneId(0)
        }

        editorScope.launch {
            while (isActive) {
                val result = contentChannel.receiveCatching()
                if (result.isSuccess) {
                    val content = result.getOrNull()
                    if (content == null) {
                        Napier.w { "ProjectEditorRepository failed to get content from contentChannel" }
                    } else {
                        updateSceneBufferContent(content)
                        launchSaveJob(content.sceneDef)
                    }
                }
            }
        }
    }

    /**
     * Returns null if there are no scenes yet
     */
    private fun findLastSceneId(): Int? = getScenes().maxByOrNull { it.id }?.id

    private fun setLastSceneId(lastSceneId: Int) {
        nextSceneId = lastSceneId + 1
    }

    protected fun claimNextSceneId(): Int {
        val newSceneId = nextSceneId
        nextSceneId += 1
        return newSceneId
    }

    abstract fun getSceneDirectory(): HPath
    abstract fun getScenePath(sceneDef: SceneDef, isNewScene: Boolean = false): HPath
    abstract fun getSceneTempPath(sceneDef: SceneDef): HPath
    abstract fun createScene(sceneName: String): SceneDef?
    abstract fun deleteScene(sceneDef: SceneDef): Boolean
    abstract fun getScenes(): List<SceneDef>
    abstract fun getSceneSummaries(): List<SceneSummary>
    abstract fun getSceneAtIndex(index: Int): SceneDef
    abstract fun getSceneFromPath(path: HPath): SceneDef
    abstract fun loadSceneBuffer(sceneDef: SceneDef): SceneBuffer
    abstract fun storeSceneBuffer(sceneDef: SceneDef): Boolean
    abstract fun storeTempSceneBuffer(sceneDef: SceneDef): Boolean
    abstract fun clearTempScene(sceneDef: SceneDef)
    abstract fun getLastOrderNumber(): Int
    abstract fun updateSceneOrder()
    abstract fun moveScene(from: Int, to: Int)

    fun onContentChanged(content: SceneContent) {
        editorScope.launch {
            contentChannel.send(content)
        }
    }

    private fun updateSceneBufferContent(content: SceneContent) {
        val oldBuffer = sceneBuffers[content.sceneDef.id]
        // Skip update if nothing is different
        if (content != oldBuffer?.content) {
            val newBuffer = SceneBuffer(content, true)
            updateSceneBuffer(newBuffer)
        }
    }

    protected fun updateSceneBuffer(newBuffer: SceneBuffer) {
        sceneBuffers[newBuffer.content.sceneDef.id] = newBuffer
        _bufferUpdateChannel.tryEmit(newBuffer)
    }

    protected fun getSceneBuffer(sceneDef: SceneDef): SceneBuffer? = sceneBuffers[sceneDef.id]
    protected fun hasSceneBuffer(sceneDef: SceneDef): Boolean =
        sceneBuffers.containsKey(sceneDef.id)

    protected fun hasDirtyBuffer(sceneDef: SceneDef): Boolean =
        getSceneBuffer(sceneDef)?.dirty == true

    fun hasDirtyBuffers(): Boolean = sceneBuffers.any { it.value.dirty }

    fun storeAllBuffers() {
        val dirtyScenes = sceneBuffers.filter { it.value.dirty }.map { it.value.content.sceneDef }
        dirtyScenes.forEach { scene ->
            storeSceneBuffer(scene)
        }
    }

    fun discardSceneBuffer(sceneDef: SceneDef) {
        if (hasSceneBuffer(sceneDef)) {
            sceneBuffers.remove(sceneDef.id)
            clearTempScene(sceneDef)
            loadSceneBuffer(sceneDef)
        }
    }

    private fun willNextSceneIncreaseMagnitude(): Boolean {
        return getLastOrderNumber().numDigits() < (getLastOrderNumber() + 1).numDigits()
    }

    fun getSceneFileName(
        sceneDef: SceneDef,
        isNewScene: Boolean = false
    ): String {
        val orderDigits = if (isNewScene && willNextSceneIncreaseMagnitude()) {
            getLastOrderNumber().numDigits() + 1
        } else {
            getLastOrderNumber().numDigits()
        }

        val order = sceneDef.order.toString().padStart(orderDigits, '0')
        return "$order-${sceneDef.name}-${sceneDef.id}.md"
    }

    fun getSceneTempFileName(sceneDef: SceneDef): String {
        return getSceneFileName(sceneDef, false) + ".temp"
    }

    @Throws(InvalidSceneFilename::class)
    fun getSceneDefFromFilename(fileName: String): SceneDef {
        val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
            ?: throw IllegalStateException("Scene filename was bad: $fileName")

        try {
            val sceneOrder = captures.groupValues[1].toInt()
            val sceneName = captures.groupValues[2]
            val sceneId = captures.groupValues[3].toInt()

            val sceneDef = SceneDef(
                projectDef = projectDef,
                id = sceneId,
                name = sceneName,
                order = sceneOrder
            )

            return sceneDef
        } catch (e: NumberFormatException) {
            throw InvalidSceneFilename("Number format exception", fileName)
        } catch (e: IllegalStateException) {
            throw InvalidSceneFilename("Invalid filename", fileName)
        }
    }

    fun validateSceneName(sceneName: String) = projectsRepository.validateFileName(sceneName)

    fun close() {
        contentChannel.close()
        runBlocking {
            storeTempJobs
            storeTempJobs.forEach { it.value.join() }
        }
        editorScope.cancel("Editor Closed")
    }

    companion object {
        val SCENE_FILENAME_PATTERN = Regex("""(\d+)-([\da-zA-Z _]+)-(\d+)\.md""")
        const val SCENE_DIRECTORY = "scenes"
        const val tempSuffix = ".temp"
    }
}

class InvalidSceneFilename(message: String, fileName: String) :
    IllegalStateException("$fileName failed to parse because: $message")