package com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import kotlinx.coroutines.launch

class OutlineOverviewComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	private val dismissDialog: () -> Unit
) : ProjectComponentBase(projectDef, componentContext), OutlineOverview {

	private val sceneEditor: SceneEditorRepository by projectInject()

	private val _state = MutableValue(OutlineOverview.State())
	override val state: Value<OutlineOverview.State> = _state

	override fun onCreate() {
		super.onCreate()

		scope.launch {
			loadOutline()
		}
	}

	private suspend fun loadOutline() {
		val tree = sceneEditor.getSceneTree()
		val storyOutline = tree.list()
			.mapNotNull { node ->
				val scene = node.value
				if (node.depth == 1) {
					when (scene.type) {
						SceneItem.Type.Scene -> {
							val metadata = sceneEditor.loadSceneMetadata(scene.id)
							OutlineOverview.OutlineItem.SceneOutline(
								sceneItem = scene,
								outline = metadata.outline,
							)
						}

						SceneItem.Type.Group -> {
							OutlineOverview.OutlineItem.ChapterOutline(
								sceneItem = scene,
							)
						}

						SceneItem.Type.Root -> null
					}
				} else {
					when (scene.type) {
						SceneItem.Type.Scene -> {
							val metadata = sceneEditor.loadSceneMetadata(scene.id)
							OutlineOverview.OutlineItem.SceneOutline(
								sceneItem = scene,
								outline = metadata.outline,
							)
						}

						SceneItem.Type.Group -> null
						SceneItem.Type.Root -> null
					}
				}
			}

		_state.getAndUpdate {
			it.copy(
				overview = storyOutline
			)
		}
	}

	override fun dismiss() {
		dismissDialog()
	}
}