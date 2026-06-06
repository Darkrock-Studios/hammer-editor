package com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.diff.DiffResult
import com.darkrockstudios.apps.hammer.base.diff.PreparedText
import com.darkrockstudios.apps.hammer.base.diff.ProseDiff
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DraftCompareComponent(
	componentContext: ComponentContext,
	override val sceneItem: SceneItem,
	override val draftDef: DraftDef,
	private val cancelCompare: () -> Unit,
	private val backToEditor: () -> Unit
) : ProjectComponentBase(sceneItem.projectDef, componentContext), DraftCompare {

	private val draftsRepository: SceneDraftRepository by projectInject()
	private val projectEditor: SceneEditorService by projectInject()

	private val _state = MutableValue(
		DraftCompare.State(
			sceneItem = sceneItem,
			draftDef = draftDef
		)
	)
	override val state: Value<DraftCompare.State> = _state

	private var diffJob: Job? = null

	// The rendered (markdown-stripped) text of each pane's editor. The diff runs on these so its
	// span offsets land in the editor's own coordinate space, which is what the UI highlights.
	private var draftText: String? = null
	private var currentText: String? = null

	// The draft is read-only, so we prepare (tokenize) it once and reuse it across recomputes
	// instead of re-tokenizing it on every edit of the current side.
	private var preparedDraft: PreparedText? = null

	override fun loadContents() {
		scope.launch {
			val currentBuffer = projectEditor.loadSceneBuffer(sceneItem)
			val draftContent = draftsRepository.loadDraft(sceneItem, draftDef)

			withContext(dispatcherMain) {
				_state.getAndUpdate {
					it.copy(
						sceneContent = currentBuffer.content,
						draftContent = draftContent
					)
				}
			}
		}
	}

	override fun onMergedContentChanged(richText: PlatformRichText) {
		_state.getAndUpdate {
			it.copy(
				mergedContent = richText,
			)
		}
	}

	override fun submitDraftText(text: String) {
		if (text == draftText) return
		draftText = text
		preparedDraft = null
		recomputeDiff()
	}

	override fun onCurrentTextChanged(text: String) {
		if (text == currentText) return
		currentText = text
		recomputeDiff()
	}

	override fun setShowDiff(show: Boolean) {
		_state.getAndUpdate { it.copy(showDiff = show) }
	}

	private fun recomputeDiff() {
		val left = draftText ?: return
		val right = currentText ?: return
		diffJob?.cancel()
		diffJob = scope.launch {
			val result: DiffResult = withContext(dispatcherDefault) {
				val preparedLeft = preparedDraft
					?: ProseDiff.preparePlain(left).also { preparedDraft = it }
				ProseDiff.diff(preparedLeft, ProseDiff.preparePlain(right))
			}
			withContext(dispatcherMain) {
				_state.getAndUpdate { it.copy(diffResult = result) }
			}
		}
	}

	override fun onCreate() {
		super.onCreate()

		loadContents()
	}

	override fun pickMerged() {
		val originalMarkdown = state.value.sceneContent?.markdown

		val content = if (state.value.mergedContent != null) {
			Napier.i { "Picking merged content" }
			SceneContent(
				scene = sceneItem,
				platformRepresentation = state.value.mergedContent
			)
		} else if (originalMarkdown != null) {
			Napier.i { "Picking merged content, but with no changes" }
			SceneContent(
				scene = sceneItem,
				markdown = originalMarkdown
			)
		} else {
			error("Cannot pick merged, both scene content and merged content were NULL")
		}

		projectEditor.onContentChanged(content, UpdateSource.Drafts)
		backToEditor()
	}

	override fun pickDraft() {
		val content = state.value.draftContent
		if (content != null) {
			projectEditor.onContentChanged(content, UpdateSource.Drafts)
			backToEditor()
		} else {
			Napier.e { "Cannot pick draft, draft content was NULL" }
		}
	}

	override fun cancel() {
		cancelCompare()
	}
}