package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.projectInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DraftsListComponent(
	componentContext: ComponentContext,
	private val sceneItem: SceneItem,
	private val closeDrafts: () -> Unit,
	private val compareDraft: (sceneDef: SceneItem, draftDef: DraftDef) -> Unit
) : ProjectComponentBase(sceneItem.projectDef, componentContext), DraftsList {

	private val draftsRepository: SceneDraftRepository by projectInject()

	private val _state = MutableValue(
		DraftsList.State(
			sceneItem = sceneItem,
			drafts = emptyList()
		)
	)
	override val state: Value<DraftsList.State> = _state

	override fun loadDrafts() {
		scope.launch(dispatcherDefault) {
            val drafts = draftsRepository.findDrafts(
                sceneItem.projectDef,
                sceneItem.id
            )

            withContext(dispatcherMain) {
                _state.reduce {
                    it.copy(drafts = drafts)
                }
            }
		}
	}

	override fun selectDraft(draftDef: DraftDef) {
		compareDraft(sceneItem, draftDef)
	}

	override fun cancel() {
		closeDrafts()
	}
}