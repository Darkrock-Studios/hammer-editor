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
    val editorScope = CoroutineScope(defaultDispatcher)
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
    abstract fun getScenePath(sceneName: String): HPath
    abstract fun createScene(sceneName: String): Scene?
    abstract fun getScenes(): List<Scene>
    abstract fun loadSceneContent(scene: Scene): String?
    abstract fun storeSceneContent(newContent: SceneContent): Boolean

    fun onContentChanged(content: SceneContent) {
        editorScope.launch {
            contentChannel.send(content)
        }
    }

    fun getSceneFileName(sceneName: String): String {
        return "$sceneName.txt"
    }

    fun getSceneNameFromFileName(fileName: String): String {
        return fileName.substringBefore(".")
    }

    fun close() {
        contentChannel.close()
        editorScope.cancel("Editor Closed")
    }

    companion object {
        const val SCENE_DIRECTORY = "scenes"
    }
}