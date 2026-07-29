package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.PreviewItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import io.github.aakira.napier.Napier

class ImportStoryUseCase(
	private val sceneEditorRepository: SceneEditorService,
) {
	/**
	 * Creates the scenes/groups described by [preview] at the project root.
	 * Returns the number of scenes successfully created.
	 */
	suspend fun execute(preview: ImportPreview): Int = sceneEditorRepository.withCoalescedReloads {
		var created = 0
		for (item in preview.items) {
			when (item) {
				is PreviewItem.Scene -> {
					if (createSceneWithContent(parent = null, scene = item)) created++
				}

				is PreviewItem.Group -> {
					val group = sceneEditorRepository.createGroup(parent = null, groupName = item.name)
					if (group == null) {
						Napier.w("Import: failed to create group '${item.name}'")
						continue
					}
					for (childScene in item.scenes) {
						if (createSceneWithContent(parent = group, scene = childScene)) created++
					}
				}
			}
		}
		created
	}

	private suspend fun createSceneWithContent(
		parent: SceneItem?,
		scene: PreviewItem.Scene,
	): Boolean {
		val sceneItem = sceneEditorRepository.createScene(parent = parent, sceneName = scene.name)
		if (sceneItem == null) {
			Napier.w("Import: failed to create scene '${scene.name}'")
			return false
		}
		if (scene.markdown.isEmpty()) return true

		val scenePath = sceneEditorRepository.resolveScenePathFromFilesystem(sceneItem.id)
		if (scenePath == null) {
			Napier.w("Import: scene '${scene.name}' has no filesystem path after creation")
			return false
		}
		val content = SceneContent(sceneItem, markdown = scene.markdown)
		return sceneEditorRepository.storeSceneMarkdownRaw(content, scenePath)
	}
}
