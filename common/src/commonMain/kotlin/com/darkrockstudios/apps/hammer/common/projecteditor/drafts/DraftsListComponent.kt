package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.mainDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject


class DraftsListComponent(
    componentContext: ComponentContext,
    private val sceneItem: SceneItem,
    private val onDraftSelected: (draftDef: DraftDef) -> Unit
) : ComponentBase(componentContext), DraftsList {

    private val draftsRepository: SceneDraftRepository by inject()

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

    override fun selectDraft(draftDef: DraftDef) = onDraftSelected(draftDef)
}