package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier

class ProjectSynchronizer(
	private val projectDef: ProjectDef,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val projectEditorRepository: ProjectEditorRepository,
	private val serverProjectApi: ServerProjectApi
) {
	private val userId: Long
		get() = globalSettingsRepository.serverSettings?.userId
			?: throw IllegalStateException("Server settings missing")

	suspend fun sync() {
		val result = serverProjectApi.hasProject(userId, projectDef.name)
		if (result.isSuccess) {
			val response = result.getOrThrow()
			if (response.exists) {
				//syncProject()
				uploadProject()
			} else {
				uploadProject()
			}
		}
	}

	private suspend fun uploadProject() {
		Napier.d("Uploading project ${projectDef.name}")

		val testScene = projectEditorRepository.getSceneAtIndex(0)
		val contents = projectEditorRepository.loadSceneMarkdownRaw(testScene)
		val path = getPathSegments(testScene)

		val result = serverProjectApi.uploadScene(testScene, path, contents)
		if(!result.isSuccess) {
			result.exceptionOrNull()?.let { e -> Napier.e("Failed to upload scene", e) }
		}
	}

	private suspend fun syncProject() {
		Napier.d("Syncing project ${projectDef.name}")
	}

	private fun getPathSegments(sceneItem: SceneItem): List<String> {
		val hpath = projectEditorRepository.getSceneFilePath(sceneItem.id)
		val pathSegments = projectEditorRepository.getScenePathSegments(hpath)

		return pathSegments.pathSegments.map { id ->
			val pathItem = projectEditorRepository.getSceneItemFromId(id)
			pathItem?.name ?: throw IllegalStateException("Scene missing for path!")
		}
	}
}