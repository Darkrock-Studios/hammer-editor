package com.darkrockstudios.apps.hammer.common.components.storyeditor.readermode

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.clampEditorWidth
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.decreaseEditorTextSize
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.increaseEditorTextSize
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tree.ChapterScenes
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.collectChapters
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.core.component.inject

class ReaderModeComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	initialScene: SceneItem?,
	private val closeReader: () -> Unit,
	private val openSceneEditor: (SceneItem) -> Unit,
	private val onSceneInView: (SceneItem?) -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ReaderMode {

	private val sceneEditor: SceneEditorService by projectInject()
	private val settingsStore: GlobalSettingsStore by inject()

	private var lastTree: ImmutableTree<SceneItem> = sceneEditor.getSceneTree()
	private var chapterScenes: List<ChapterScenes> = lastTree.collectChapters()

	private var loadJob: Job? = null
	private val bufferJobs = mutableMapOf<Int, Job>()

	private val _state = MutableValue(initialState(initialScene))
	override val state: Value<ReaderMode.State> = _state

	init {
		stateKeeper.register(SAVED_STATE_KEY, ReaderSavedState.serializer()) {
			val current = _state.value
			ReaderSavedState(
				activeChapterId = current.activeChapterId,
				sceneId = current.scrollToSceneId ?: current.sceneInView?.id,
			)
		}
	}

	private fun initialState(initialScene: SceneItem?): ReaderMode.State {
		val saved = stateKeeper.consume(SAVED_STATE_KEY, ReaderSavedState.serializer())
		val startSceneId = if (saved != null) saved.sceneId else initialScene?.id
		val startChapter = chapterScenes.firstOrNull { it.chapter.id == saved?.activeChapterId }
			?: startSceneId?.let(::chapterContaining)
			?: chapterScenes.firstOrNull()
		val settings = settingsStore.globalSettings

		return ReaderMode.State(
			chapters = chapterScenes.toReaderChapters(),
			activeChapterId = startChapter?.chapter?.id,
			showSceneHeadings = settings.readerShowSceneHeadings,
			fontSize = settings.readerFontSize,
			editorMaxWidth = settings.editorMaxWidth,
			scrollToSceneId = startSceneId?.takeIf { id -> startChapter?.scenes?.any { it.id == id } == true },
		)
	}

	override fun onCreate() {
		super.onCreate()

		watchSettings()
		sceneEditor.subscribeToSceneUpdates(scope, ::onSceneTreeUpdate)
		sceneEditor.subscribeToBufferUpdates(null, scope, ::onBufferUpdate)

		val chapterId = _state.value.activeChapterId
		if (chapterId != null) {
			loadChapter(chapterId, clearScenes = true)
		} else {
			_state.update { it.copy(isLoading = false) }
		}
	}

	private fun chapterContaining(sceneId: Int): ChapterScenes? =
		chapterScenes.firstOrNull { chapter -> chapter.scenes.any { it.id == sceneId } }

	private fun List<ChapterScenes>.toReaderChapters() =
		map { ReaderMode.ReaderChapter(chapter = it.chapter, sceneCount = it.scenes.size) }

	private fun loadChapter(chapterId: Int, clearScenes: Boolean) {
		val scenes = chapterScenes.firstOrNull { it.chapter.id == chapterId }?.scenes ?: return

		loadJob?.cancel()
		bufferJobs.values.forEach { it.cancel() }
		bufferJobs.clear()

		_state.update {
			it.copy(
				activeChapterId = chapterId,
				scenes = if (clearScenes) emptyList() else it.scenes,
				isLoading = true,
			)
		}

		loadJob = scope.launch {
			val loaded = withContext(dispatcherIo) {
				scenes.map { scene ->
					ReaderMode.ReaderScene(scene, sceneEditor.getCurrentSceneContentOrNull(scene) ?: "")
				}
			}
			withContext(dispatcherMain) {
				_state.update {
					if (it.activeChapterId == chapterId) it.copy(scenes = loaded, isLoading = false) else it
				}
			}
		}
	}

	private fun watchSettings() {
		scope.launch {
			settingsStore.globalSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.update {
						it.copy(
							showSceneHeadings = settings.readerShowSceneHeadings,
							fontSize = settings.readerFontSize,
							editorMaxWidth = settings.editorMaxWidth,
						)
					}
				}
			}
		}
	}

	// Scene updates also fire on every autosave, so anything but a changed tree is ignored.
	private fun onSceneTreeUpdate(summary: SceneSummary) {
		val tree = summary.sceneTree
		if (tree == lastTree) return
		lastTree = tree

		val previousChapters = chapterScenes
		chapterScenes = tree.collectChapters()
		if (chapterScenes.isEmpty()) {
			closeReader()
			return
		}

		val activeId = _state.value.activeChapterId
		val previousIndex = previousChapters.indexOfFirst { it.chapter.id == activeId }
		val active = chapterScenes.firstOrNull { it.chapter.id == activeId }
			?: chapterScenes[previousIndex.coerceIn(0, chapterScenes.lastIndex)]
		val previousSceneIds = previousChapters.getOrNull(previousIndex)?.scenes?.map { it.id }
		val sceneIds = active.scenes.map { it.id }

		_state.update { it.copy(chapters = chapterScenes.toReaderChapters()) }

		if (active.chapter.id != activeId) {
			_state.update { it.copy(scrollToSceneId = null, sceneInView = null) }
			loadChapter(active.chapter.id, clearScenes = true)
			onSceneInView(null)
		} else if (sceneIds != previousSceneIds) {
			loadChapter(active.chapter.id, clearScenes = false)
		} else {
			val current = active.scenes.associateBy { it.id }
			_state.update { state ->
				state.copy(
					scenes = state.scenes.map { it.copy(sceneItem = current[it.sceneItem.id] ?: it.sceneItem) },
					sceneInView = state.sceneInView?.let { current[it.id] ?: it },
				)
			}
		}
	}

	private suspend fun onBufferUpdate(buffer: SceneBuffer) {
		val sceneId = buffer.content.scene.id
		if (_state.value.scenes.none { it.sceneItem.id == sceneId }) return

		bufferJobs[sceneId]?.cancel()
		bufferJobs[sceneId] = scope.launch {
			val markdown = withContext(dispatcherDefault) { buffer.content.coerceMarkdown() }
			withContext(dispatcherMain) {
				_state.update { state ->
					state.copy(
						scenes = state.scenes.map {
							if (it.sceneItem.id == sceneId && it.markdown != markdown) it.copy(markdown = markdown) else it
						}
					)
				}
			}
		}
	}

	override fun showChapter(chapterId: Int) {
		if (chapterId == _state.value.activeChapterId) return
		if (chapterScenes.none { it.chapter.id == chapterId }) return

		_state.update { it.copy(scrollToSceneId = null, sceneInView = null) }
		loadChapter(chapterId, clearScenes = true)
		onSceneInView(null)
	}

	private fun showChapterAtOffset(offset: Int) {
		val index = chapterScenes.indexOfFirst { it.chapter.id == _state.value.activeChapterId }
		if (index < 0) return
		val target = chapterScenes.getOrNull(index + offset) ?: return
		showChapter(target.chapter.id)
	}

	override fun nextChapter() = showChapterAtOffset(1)

	override fun previousChapter() = showChapterAtOffset(-1)

	override fun showScene(sceneItem: SceneItem) {
		val chapter = chapterScenes.firstOrNull { it.chapter.id == sceneItem.id }
			?: chapterContaining(sceneItem.id)
			?: return
		val targetId = if (sceneItem.type == SceneItem.Type.Scene) {
			sceneItem.id
		} else {
			chapter.scenes.firstOrNull()?.id
		}

		if (chapter.chapter.id != _state.value.activeChapterId) {
			_state.update { it.copy(sceneInView = null) }
			loadChapter(chapter.chapter.id, clearScenes = true)
		}
		_state.update { it.copy(scrollToSceneId = targetId) }
	}

	override fun scrollHandled() {
		val current = _state.value
		val targetId = current.scrollToSceneId ?: return
		val target = current.scenes.firstOrNull { it.sceneItem.id == targetId }?.sceneItem

		_state.update { it.copy(scrollToSceneId = null, sceneInView = target ?: it.sceneInView) }
		if (target != null && target != current.sceneInView) onSceneInView(target)
	}

	// Ignored while a scroll is pending, or the list highlight jumps to whatever is on screen first.
	override fun sceneInView(sceneItem: SceneItem?) {
		val current = _state.value
		if (current.scrollToSceneId != null || sceneItem == current.sceneInView) return

		_state.update { it.copy(sceneInView = sceneItem) }
		onSceneInView(sceneItem)
	}

	override fun editScene(sceneItem: SceneItem?) {
		val current = _state.value
		val target = sceneItem
			?: current.sceneInView
			?: chapterScenes.firstOrNull { it.chapter.id == current.activeChapterId }?.scenes?.firstOrNull()
			?: return
		openSceneEditor(target)
	}

	private fun updateSettings(transform: (GlobalSettings) -> GlobalSettings) {
		scope.launch { settingsStore.updateSettings(transform) }
	}

	override fun toggleSceneHeadings() =
		updateSettings { it.copy(readerShowSceneHeadings = !it.readerShowSceneHeadings) }

	override fun increaseFontSize() =
		updateSettings { it.copy(readerFontSize = increaseEditorTextSize(it.readerFontSize)) }

	override fun decreaseFontSize() =
		updateSettings { it.copy(readerFontSize = decreaseEditorTextSize(it.readerFontSize)) }

	override fun resetFontSize() =
		updateSettings { it.copy(readerFontSize = GlobalSettings.DEFAULT_FONT_SIZE) }

	override fun setEditorMaxWidth(width: Float) =
		updateSettings { it.copy(editorMaxWidth = clampEditorWidth(width)) }

	override fun resetEditorMaxWidth() =
		updateSettings { it.copy(editorMaxWidth = GlobalSettings.DEFAULT_EDITOR_WIDTH) }

	override fun close() = closeReader()

	@Serializable
	private data class ReaderSavedState(val activeChapterId: Int?, val sceneId: Int?)

	companion object {
		private const val SAVED_STATE_KEY = "ReaderMode"
	}
}
