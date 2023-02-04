package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.projectInject
import io.github.aakira.napier.Napier
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
	private val projectEditor: ProjectEditorRepository by projectInject()

	private val _state = MutableValue(
		DraftCompare.State(
			sceneItem = sceneItem,
			draftDef = draftDef
		)
	)
	override val state: Value<DraftCompare.State> = _state

	override fun loadContents() {
		scope.launch {
			val currentBuffer = projectEditor.loadSceneBuffer(sceneItem)
			val draftContent = draftsRepository.loadDraft(sceneItem, draftDef)

			withContext(dispatcherMain) {
				_state.reduce {
					it.copy(
						sceneContent = currentBuffer.content,
						draftContent = draftContent
					)
				}
			}
		}
	}

	override fun onContentChanged(content: PlatformRichText) {
		projectEditor.onContentChanged(
			SceneContent(
				scene = sceneItem,
				platformRepresentation = content
			)
		)
	}

	override fun pickMerged() {
		val content = state.value.sceneContent
		if (content != null) {
			projectEditor.onContentChanged(content)
			backToEditor()
		} else {
			Napier.e { "Cannot pick merged, merged content was NULL" }
		}
	}

	override fun pickDraft() {
		val content = state.value.draftContent
		if (content != null) {
			projectEditor.onContentChanged(content)
			backToEditor()
		} else {
			Napier.e { "Cannot pick draft, draft content was NULL" }
		}
	}

	override fun cancel() {
		cancelCompare()
	}
}