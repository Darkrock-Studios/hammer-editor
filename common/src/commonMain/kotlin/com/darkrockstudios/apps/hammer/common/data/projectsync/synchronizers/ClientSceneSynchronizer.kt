package com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogE
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.projectsync.toApiType
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.findById
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

class ClientSceneSynchronizer(
	projectDef: ProjectDef,
	private val sceneEditorRepository: SceneEditorRepository,
	private val draftRepository: SceneDraftRepository,
	serverProjectApi: ServerProjectApi,
	private val strRes: StrRes,
) : EntitySynchronizer<ApiProjectEntity.SceneEntity>(
	projectDef, serverProjectApi
) {
	override suspend fun createEntityForId(id: Int): ApiProjectEntity.SceneEntity {
		val scene =
			sceneEditorRepository.getSceneItemFromId(id) ?: throw IllegalStateException("Scene missing for ID $id")
		val path = sceneEditorRepository.getPathSegments(scene)

		val contents = if (scene.type == SceneItem.Type.Scene) {
			sceneEditorRepository.loadSceneMarkdownRaw(scene)
		} else {
			""
		}

		val metadata = sceneEditorRepository.loadSceneMetadata(id)

		return ApiProjectEntity.SceneEntity(
			id = id,
			name = scene.name,
			order = scene.order,
			sceneType = scene.type.toApiType(),
			content = contents,
			path = path,
			outline = metadata.outline,
			notes = metadata.notes,
		)
	}

	override suspend fun prepareForSync() {
		sceneEditorRepository.storeAllBuffers()
	}

	override suspend fun ownsEntity(id: Int): Boolean {
		return sceneEditorRepository.getSceneItemFromId(id) != null
	}

	override suspend fun getEntityHash(id: Int): String? {
		val sceneItem = sceneEditorRepository.getSceneItemFromId(id)
		return if (sceneItem != null) {
			val scenePath = sceneEditorRepository.resolveScenePathFromFilesystem(sceneItem.id)
				?: throw IllegalStateException("Scene $id not found on filesystem")
			val pathSegments = sceneEditorRepository.getScenePathSegments(scenePath).pathSegments

			val sceneContent = sceneEditorRepository.loadSceneMarkdownRaw(sceneItem, scenePath)
			val metadata = sceneEditorRepository.loadSceneMetadata(sceneItem.id)

			EntityHasher.hashScene(
				id = sceneItem.id,
				name = sceneItem.name,
				order = sceneItem.order,
				path = pathSegments,
				type = sceneItem.type.toApiType(),
				content = sceneContent,
				outline = metadata.outline,
				notes = metadata.notes,
			)
		} else {
			null
		}
	}

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.SceneEntity,
		syncId: String,
		onLog: OnSyncLog
	): Boolean {
		Napier.d("Storing Entity ${serverEntity.id}")
		val id = serverEntity.id
		val tree = sceneEditorRepository.rawTree

		val parentId = serverEntity.path.lastOrNull()
		val parent = if (parentId != null) {
			sceneEditorRepository.getSceneItemFromId(parentId)
		} else {
			null
		}

		return if (serverEntity.sceneType == ApiSceneType.Scene) {
			Napier.d("Entity $id is a Scene")

			val existingScene = sceneEditorRepository.getSceneItemFromId(id)
			val sceneItem = if (existingScene != null) {
				existingScene
			} else {
				onLog(syncLogI(strRes.get(MR.strings.sync_scene_creating, id), projectDef))
				sceneEditorRepository.createScene(
					parent = parent,
					sceneName = serverEntity.name,
					forceId = serverEntity.id,
					forceOrder = serverEntity.order
				)
					?: throw IllegalStateException("Failed to create scene")
			}

			val treeNode = tree.find { it.id == id }
			treeNode.value = sceneItem.copy(
				name = serverEntity.name,
				order = serverEntity.order
			)

			val scenePath = sceneEditorRepository.getPathFromFilesystem(sceneItem)
				?: throw IllegalStateException("Scene $id has no path")

			val content = SceneContent(sceneItem, serverEntity.content)
			if (sceneEditorRepository.storeSceneMarkdownRaw(content, scenePath)) {
				// On success, update the scene metadata
				val updatedMetadata = SceneMetadata(
					notes = serverEntity.notes,
					outline = serverEntity.outline
				)
				sceneEditorRepository.storeMetadata(updatedMetadata, serverEntity.id)

				// Finally, log our success, and update the running apps data
				onLog(syncLogI(strRes.get(MR.strings.sync_scene_downloading, id), projectDef))
				sceneEditorRepository.onContentChanged(content, UpdateSource.Sync)

				if (existingScene != null) {
					val existingTreeNode = tree.findById(id)
					// Must move parents
					if (existingTreeNode.parent?.value?.id != serverEntity.path.lastOrNull()) {

						existingTreeNode.parent?.removeChild(existingTreeNode)

						val newParent = tree.find { it.id == serverEntity.path.lastOrNull() }
						newParent.addChild(existingTreeNode)
						onLog(
							syncLogI(
								strRes.get(
									MR.strings.sync_scene_moved_parent,
									id,
									serverEntity.path.lastOrNull() ?: -1
								), projectDef
							)
						)
					}
				}

				true
			} else {
				onLog(syncLogE(strRes.get(MR.strings.sync_scene_store_content_failed, id), projectDef))
				false
			}
		} else {
			Napier.d("Entity $id is a Scene Group")

			val existingGroup = sceneEditorRepository.getSceneItemFromId(id)
			val sceneItem = if (existingGroup != null) {
				existingGroup
			} else {
				onLog(syncLogI(strRes.get(MR.strings.sync_scene_group_creating, id), projectDef))
				sceneEditorRepository.createGroup(
					parent = parent,
					groupName = serverEntity.name,
					forceId = serverEntity.id,
					forceOrder = serverEntity.order
				)
					?: throw IllegalStateException("Failed to create scene")
			}

			val treeNode = tree.findById(id)
			treeNode.value = sceneItem.copy(
				name = serverEntity.name,
				order = serverEntity.order
			)

			if (existingGroup != null) {
				val existingTreeNode = tree.findById(id)
				// Must move parents
				if (existingTreeNode.parent?.value?.id != serverEntity.path.lastOrNull()) {
					existingTreeNode.parent?.removeChild(existingTreeNode)

					val newParent = tree.find { it.id == serverEntity.path.lastOrNull() }
					newParent.addChild(existingTreeNode)
					onLog(
						syncLogI(
							strRes.get(
								MR.strings.sync_scene_group_moved_parent,
								id,
								serverEntity.path.lastOrNull() ?: -1
							), projectDef
						)
					)
				}
			}

			onLog(syncLogI(strRes.get(MR.strings.sync_scene_group_downloading, id), projectDef))
			true
		}
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		Napier.d("Re-Id Scene $oldId to $newId")

		sceneEditorRepository.reIdScene(oldId, newId)

		draftRepository.reIdScene(
			oldId = oldId,
			newId = newId,
		)
	}

	override suspend fun finalizeSync() {
		sceneEditorRepository.rationalizeTree()
		sceneEditorRepository.cleanupSceneOrder()

		// Wait for buffers to propagate before we save them
		delay(SceneEditorRepository.BUFFER_COOL_DOWN * 0.25)

		sceneEditorRepository.forceSceneListReload()
		sceneEditorRepository.storeAllBuffers()
	}

	override fun getEntityType() = EntityType.Scene

	override suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		val sceneItem = sceneEditorRepository.getSceneItemFromId(id)
		if (sceneItem != null) {
			if (sceneEditorRepository.deleteScene(sceneItem)) {
				onLog(syncLogI(strRes.get(MR.strings.sync_scene_deleted, id), projectDef))
			} else {
				onLog(syncLogE(strRes.get(MR.strings.sync_scene_delete_failed, id), projectDef))
			}
		} else {
			onLog(syncLogE(strRes.get(MR.strings.sync_scene_delete_failed_not_found, id), projectDef))
		}
	}

	override suspend fun hashEntities(newIds: List<Int>): Set<EntityHash> {
		return sceneEditorRepository.rawTree.root()
			.filter { newIds.contains(it.value.id).not() }
			.mapNotNull { node ->
				if (!node.value.isRootScene) {
					getEntityHash(node.value.id)?.let { hash ->
						EntityHash(node.value.id, hash)
					}
				} else {
					null
				}
			}.toSet()
	}
}
