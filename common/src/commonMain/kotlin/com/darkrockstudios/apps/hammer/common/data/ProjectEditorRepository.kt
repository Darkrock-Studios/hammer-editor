package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

abstract class ProjectEditorRepository(
    val project: Project,
    protected val projectsRepository: ProjectsRepository
) {
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

    abstract fun getSceneDirectory(): HPath
    abstract fun getScenePath(scene: Scene): HPath
    abstract fun createScene(sceneName: String): Scene?
    abstract fun deleteScene(scene: Scene): Boolean
    abstract fun getScenes(): List<Scene>
    abstract fun loadSceneContent(scene: Scene): String?
    abstract fun storeSceneContent(newContent: SceneContent): Boolean
    abstract fun getNextOrderNumber(): Int

    fun onContentChanged(content: SceneContent) {
        editorScope.launch {
            contentChannel.send(content)
        }
    }

    fun getSceneFileName(scene: Scene): String {
        val order = scene.order.toString().padStart(4, '0')
        return "$order-${scene.name}.txt"
    }

    @Throws(IllegalStateException::class)
    fun getSceneNameFromFileName(fileName: String): String {
        val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
            ?: throw IllegalStateException("Scene filename was bad, what do?")
        val sceneName = captures.groupValues[2]
        return sceneName
    }

    @Throws(NumberFormatException::class, IllegalStateException::class)
    fun getSceneOrderNumber(fileName: String): Int {
        val captures = SCENE_FILENAME_PATTERN.matchEntire(fileName)
            ?: throw IllegalStateException("Scene filename was bad, what do?")
        val sceneOrder = captures.groupValues[1]
        val orderNumber = sceneOrder.toInt()
        return orderNumber
    }

    fun validateSceneName(sceneName: String) = projectsRepository.validateFileName(sceneName)

    fun close() {
        contentChannel.close()
        editorScope.cancel("Editor Closed")
    }

    companion object {
        val SCENE_FILENAME_PATTERN = Regex("""(\d+)-([\da-zA-Z _-]+)\.txt""")
        const val SCENE_DIRECTORY = "scenes"
    }
}