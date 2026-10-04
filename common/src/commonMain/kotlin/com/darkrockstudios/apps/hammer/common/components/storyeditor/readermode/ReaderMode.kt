package com.darkrockstudios.apps.hammer.common.components.storyeditor.readermode

import androidx.compose.runtime.Immutable
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings

interface ReaderMode {
	val state: Value<State>

	fun showChapter(chapterId: Int)
	fun nextChapter()
	fun previousChapter()

	/** Brings [sceneItem] into view, switching chapters when it lives in another one. */
	fun showScene(sceneItem: SceneItem)
	fun scrollHandled()
	fun sceneInView(sceneItem: SceneItem?)

	/** Opens [sceneItem] in the scene editor, or the scene being read when null. */
	fun editScene(sceneItem: SceneItem? = null)

	fun toggleSceneHeadings()
	fun increaseFontSize()
	fun decreaseFontSize()
	fun resetFontSize()
	fun setEditorMaxWidth(width: Float)
	fun resetEditorMaxWidth()
	fun close()

	@Immutable
	data class State(
		val chapters: List<ReaderChapter> = emptyList(),
		val activeChapterId: Int? = null,
		val scenes: List<ReaderScene> = emptyList(),
		val isLoading: Boolean = true,
		val showSceneHeadings: Boolean = false,
		val fontSize: Float = GlobalSettings.DEFAULT_FONT_SIZE,
		val editorMaxWidth: Float = GlobalSettings.DEFAULT_EDITOR_WIDTH,
		/** One-shot request for the UI to scroll to a scene; cleared by [scrollHandled]. */
		val scrollToSceneId: Int? = null,
		val sceneInView: SceneItem? = null,
	) {
		val activeChapterIndex: Int
			get() = chapters.indexOfFirst { it.chapter.id == activeChapterId }
	}

	@Immutable
	data class ReaderChapter(val chapter: SceneItem, val sceneCount: Int)

	@Immutable
	data class ReaderScene(val sceneItem: SceneItem, val markdown: String)
}
