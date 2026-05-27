package com.darkrockstudios.apps.hammer.common.components.storyeditor.drafts

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.diff.DiffResult
import com.darkrockstudios.apps.hammer.base.diff.ProseDiff
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
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
	private val projectEditor: SceneEditorRepository by projectInject()

	private val _state = MutableValue(
		DraftCompare.State(
			sceneItem = sceneItem,
			draftDef = draftDef
		)
	)
	override val state: Value<DraftCompare.State> = _state

	private var diffJob: Job? = null

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

			val draftMd = draftContent?.markdown
			val currentMd = currentBuffer.content.markdown
			if (draftMd != null && currentMd != null) {
				recomputeDiff(draftMd = draftMd, currentMd = currentMd)
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

	override fun onCurrentMarkdownChanged(markdown: String) {
		val draftMd = state.value.draftContent?.markdown ?: return
		recomputeDiff(draftMd = draftMd, currentMd = markdown)
	}

	override fun setShowDiff(show: Boolean) {
		_state.getAndUpdate { it.copy(showDiff = show) }
	}

	private fun recomputeDiff(draftMd: String, currentMd: String) {
		diffJob?.cancel()
		diffJob = scope.launch {
			val result: DiffResult = withContext(dispatcherDefault) {
				ProseDiff.diff(draftMd, currentMd)
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