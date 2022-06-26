package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.util.numDigits
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    init {
        editorScope.launch {
            while (isActive) {
                val result = contentChannel.receiveCatching()
                if (result.isSuccess) {
                    val content = result.getOrNull()
                    if (content == null) {
                        Napier.w { "ProjectEditorRepository failed to get content from contentChannel" }
                    } else {
                        storeSceneContent(content)
                    }
                }
            }
        }
    }

    /**
     * This needs to be called during init from implementing classes
     */
    fun initializeProjectEditor() {
        val lastSceneId = findLastSceneId()
        if (lastSceneId != null) {
            setLastSceneId(lastSceneId)
        } else {
            setLastSceneId(0)
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
    abstract fun createScene(sceneName: String): SceneDef?
    abstract fun deleteScene(sceneDef: SceneDef): Boolean
    abstract fun getScenes(): List<SceneDef>
    abstract fun getSceneAtIndex(index: Int): SceneDef
    abstract fun getSceneFromPath(path: HPath): SceneDef
    abstract fun loadSceneContent(sceneDef: SceneDef): String?
    abstract fun storeSceneContent(newContent: SceneContent): Boolean
    abstract fun getLastOrderNumber(): Int
    abstract fun updateSceneOrder()
    abstract fun moveScene(from: Int, to: Int)

    fun onContentChanged(content: SceneContent) {
        editorScope.launch {
            contentChannel.send(content)
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

    @Throws(NumberFormatException::class, IllegalStateException::class)
    fun getSceneIdNumber(fileName: String): Int {
        val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
            ?: throw IllegalStateException("Scene filename was bad, what do?")
        val sceneOrder = captures.groupValues[3]
        val sceneId = sceneOrder.toInt()
        return sceneId
    }

    @Throws(NumberFormatException::class, IllegalStateException::class)
    fun getSceneOrderNumber(fileName: String): Int {
        val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
            ?: throw IllegalStateException("Scene filename was bad, what do?")
        val sceneOrder = captures.groupValues[3]
        val orderNumber = sceneOrder.toInt()
        return orderNumber
    }

    fun validateSceneName(sceneName: String) = projectsRepository.validateFileName(sceneName)

    fun close() {
        contentChannel.close()
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