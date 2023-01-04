package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import com.darkrockstudios.apps.hammer.common.projectInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DraftsListComponent(
	componentContext: ComponentContext,
	private val sceneItem: SceneItem,
	private val closeDrafts: () -> Unit
) : ProjectComponentBase(sceneItem.projectDef, componentContext), DraftsList {

	private val draftsRepository: SceneDraftRepository by projectInject()
	private val projectEditor: ProjectEditorRepository by projectInject()

	private val _state = MutableValue(
		DraftsList.State(
			sceneItem = sceneItem,
			drafts = emptyList()
		)
	)
	override val state: Value<DraftsList.State> = _state

	override fun loadDrafts() {
		scope.launch(defaultDispatcher) {
			val drafts = draftsRepository.findDrafts(
				sceneItem.projectDef,
				sceneItem.id
			)

			withContext(mainDispatcher) {
				_state.reduce {
					it.copy(drafts = drafts)
				}
			}
		}
	}

	override fun selectDraft(draftDef: DraftDef) {
		val draftContent = draftsRepository.loadDraft(sceneItem, draftDef)

		if (draftContent != null) {
			projectEditor.onContentChanged(draftContent)
		}

		closeDrafts()
	}

	override fun cancel() {
		closeDrafts()
	}
}