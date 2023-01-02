package com.darkrockstudios.apps.hammer.common.projecteditor.drafts

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface DraftsList : HammerComponent {
    val state: Value<State>

    fun loadDrafts()

    fun selectDraft(draftDef: DraftDef)

    fun cancel()

    data class State(
        val sceneItem: SceneItem,
        val drafts: List<DraftDef>
    )
}