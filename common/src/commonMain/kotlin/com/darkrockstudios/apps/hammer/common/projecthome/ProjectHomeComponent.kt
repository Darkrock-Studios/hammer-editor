package com.darkrockstudios.apps.hammer.common.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.projectInject
import com.darkrockstudios.apps.hammer.common.util.formatLocal
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectHomeComponent(
    componentContext: ComponentContext,
    projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), ProjectHome {

    private val projectEditorRepository: ProjectEditorRepository by projectInject()
    private val encyclopediaRepository: EncyclopediaRepository by projectInject()

    private val _state = MutableValue(
        ProjectHome.State(
            projectDef = projectDef,
            numberOfScenes = 0,
            created = ""
        )
    )
    override val state: Value<ProjectHome.State> = _state

    override fun onCreate() {
        super.onCreate()

        loadData()
    }

    private fun loadData() {
        scope.launch(dispatcherDefault) {
            val metadata = projectEditorRepository.getMetadata()
            val created = metadata.info.created.formatLocal("dd MMM `yy")

            val numScenes = projectEditorRepository.getSceneTree().root.totalChildren

            encyclopediaRepository.loadEntries()
            val entriesByType = mutableMapOf<EntryType, Int>()
            encyclopediaRepository.entryListFlow.take(1).collect { entries ->
                EntryType.values().forEach { type ->
                    val numEntriesOfType = entries.count { it.type == type }
                    entriesByType[type] = numEntriesOfType
                }
            }

            withContext(dispatcherMain) {
                _state.reduce {
                    it.copy(
                        created = created,
                        numberOfScenes = numScenes,
                        encyclopediaEntriesByType = entriesByType
                    )
                }
            }
        }
    }

    override fun isAtRoot() = true
}