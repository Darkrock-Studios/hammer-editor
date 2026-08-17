package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.utilities.isFailure

sealed class SceneSetResult {
	data class Success(val scenes: List<ApiProjectEntity.SceneEntity>) : SceneSetResult()
	data class InvalidId(val id: Int, val exception: Throwable? = null) : SceneSetResult()

	/** The id resolves to a scene GROUP, which carries no prose of its own. */
	data class NotAScene(val id: Int) : SceneSetResult()
}

/**
 * Loads and validates a set of leaf-scene ids in one pass: existence is checked against a
 * single defs query, then each scene is loaded and group ids are rejected. Groups share
 * [ApiProjectEntity.Type.SCENE] with leaf scenes, so a type check alone cannot tell them apart.
 */
suspend fun ProjectEntityDatasource.loadSceneSet(
	userId: Long,
	projectDef: ProjectDefinition,
	sceneIds: Collection<Int>,
): SceneSetResult {
	val validIds = getEntityDefsByType(
		userId = userId,
		projectDef = projectDef,
		type = ApiProjectEntity.Type.SCENE,
	).map { it.id }.toSet()

	val scenes = ArrayList<ApiProjectEntity.SceneEntity>(sceneIds.size)
	for (sceneId in sceneIds) {
		if (sceneId !in validIds) return SceneSetResult.InvalidId(sceneId)
		val result = loadEntity(
			userId, projectDef, sceneId,
			ApiProjectEntity.Type.SCENE,
			ApiProjectEntity.SceneEntity.serializer(),
		)
		if (isFailure(result)) return SceneSetResult.InvalidId(sceneId, result.exception)
		val scene = result.data
		if (scene.sceneType != ApiSceneType.Scene) return SceneSetResult.NotAScene(sceneId)
		scenes += scene
	}
	return SceneSetResult.Success(scenes)
}
