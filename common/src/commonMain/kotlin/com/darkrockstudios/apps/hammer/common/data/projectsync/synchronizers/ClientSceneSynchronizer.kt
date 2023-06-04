package com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers

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
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.*
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

class ClientSceneSynchronizer(
	projectDef: ProjectDef,
	private val projectEditorRepository: ProjectEditorRepository,
	private val draftRepository: SceneDraftRepository,
	serverProjectApi: ServerProjectApi
) : EntitySynchronizer<ApiProjectEntity.SceneEntity>(
	projectDef, serverProjectApi
) {
	override suspend fun createEntityForId(id: Int): ApiProjectEntity.SceneEntity {
		val scene =
			projectEditorRepository.getSceneItemFromId(id) ?: throw IllegalStateException("Scene missing for ID $id")
		val path = projectEditorRepository.getPathSegments(scene)

		val contents = if (scene.type == SceneItem.Type.Scene) {
			projectEditorRepository.loadSceneMarkdownRaw(scene)
		} else {
			""
		}

		return ApiProjectEntity.SceneEntity(
			id = id,
			name = scene.name,
			order = scene.order,
			sceneType = scene.type.toApiType(),
			content = contents,
			path = path,
		)
	}

	override suspend fun prepareForSync() {
		projectEditorRepository.storeAllBuffers()
	}

	override suspend fun ownsEntity(id: Int): Boolean {
		return projectEditorRepository.getSceneItemFromId(id) != null
	}

	override suspend fun getEntityHash(id: Int): String? {
		val sceneItem = projectEditorRepository.getSceneItemFromId(id)
		return if (sceneItem != null) {
			val scenePath = projectEditorRepository.getPathFromFilesystem(sceneItem)
				?: throw IllegalStateException("Scene $id has no path")

			val sceneContent = projectEditorRepository.loadSceneMarkdownRaw(sceneItem, scenePath)
			EntityHasher.hashScene(
				id = sceneItem.id,
				name = sceneItem.name,
				order = sceneItem.order,
				type = sceneItem.type.toApiType(),
				content = sceneContent
			)
		} else {
			null
		}
	}

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.SceneEntity,
		syncId: String,
		onLog: OnSyncLog
	) {
		Napier.d("Storing Entity ${serverEntity.id}")
		val id = serverEntity.id
		val tree = projectEditorRepository.rawTree

		val parentId = serverEntity.path.lastOrNull()
		val parent = if (parentId != null) {
			projectEditorRepository.getSceneItemFromId(parentId)
		} else {
			null
		}

		if (serverEntity.sceneType == ApiSceneType.Scene) {
			Napier.d("Entity $id is a Scene")

			val existingScene = projectEditorRepository.getSceneItemFromId(id)
			val sceneItem = if (existingScene != null) {
				val existingTreeNode = tree.find { it.id == id }
				// Must move parents
				if (existingTreeNode.parent?.value?.order != serverEntity.path.lastOrNull()) {
					existingTreeNode.parent?.removeChild(existingTreeNode)

					val newParent = tree.find { it.id == serverEntity.path.lastOrNull() }
					newParent.addChild(existingTreeNode)
					onLog(syncLogI("Moved scene $id to new parent ${serverEntity.path.lastOrNull()}", projectDef))
				}

				existingScene
			} else {
				onLog(syncLogI("Creating new scene $id", projectDef))
				projectEditorRepository.createScene(
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

			val scenePath = projectEditorRepository.getPathFromFilesystem(sceneItem)
				?: throw IllegalStateException("Scene $id has no path")

			val content = SceneContent(sceneItem, serverEntity.content)
			if (!projectEditorRepository.storeSceneMarkdownRaw(content, scenePath)) {
				onLog(syncLogE("Failed to save downloaded scene content for: $id", projectDef))
			} else {
				onLog(syncLogI("Downloaded scene content for: $id", projectDef))
				projectEditorRepository.onContentChanged(content, UpdateSource.Sync)
			}
		} else {
			Napier.d("Entity $id is a Scene Group")

			val existingGroup = projectEditorRepository.getSceneItemFromId(id)
			val sceneItem = if (existingGroup != null) {
				val existingTreeNode = tree.find { it.id == id }
				// Must move parents
				if (existingTreeNode.parent?.value?.order != serverEntity.path.lastOrNull()) {
					existingTreeNode.parent?.removeChild(existingTreeNode)

					val newParent = tree.find { it.id == serverEntity.path.lastOrNull() }
					newParent.addChild(existingTreeNode)
					onLog(syncLogI("Moved scene $id to new parent ${serverEntity.path.lastOrNull()}", projectDef))
				}

				existingGroup
			} else {
				onLog(syncLogI("Creating new group $id", projectDef))
				projectEditorRepository.createGroup(
					parent = parent,
					groupName = serverEntity.name,
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

			onLog(syncLogI("Downloaded scene group for: $id", projectDef))
		}
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		Napier.d("Re-Id Scene $oldId to $newId")

		projectEditorRepository.reIdScene(oldId, newId)

		draftRepository.reIdScene(
			oldId = oldId,
			newId = newId,
		)
	}

	override suspend fun finalizeSync() {
		projectEditorRepository.rationalizeTree()
		projectEditorRepository.cleanupSceneOrder()

		// Wait for buffers to propagate before we save them
		delay(ProjectEditorRepository.BUFFER_COOL_DOWN * 0.25)

		projectEditorRepository.forceSceneListReload()
		projectEditorRepository.storeAllBuffers()
	}

	override fun getEntityType() = EntityType.Scene

	override suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		val sceneItem = projectEditorRepository.getSceneItemFromId(id)
		if (sceneItem != null) {
			if (projectEditorRepository.deleteScene(sceneItem)) {
				onLog(syncLogI("Deleting scene $id", projectDef))
			} else {
				onLog(syncLogE("Failed to delete scene $id", projectDef))
			}
		} else {
			onLog(syncLogE("Failed find scene to delete: $id", projectDef))
		}
	}

	override suspend fun hashEntities(newIds: List<Int>): Set<EntityHash> {
		return projectEditorRepository.rawTree.root()
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
