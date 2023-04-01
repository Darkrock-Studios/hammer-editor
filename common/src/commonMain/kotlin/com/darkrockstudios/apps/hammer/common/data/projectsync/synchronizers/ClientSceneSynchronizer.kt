package com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.toApiType
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
			EntityHash.hashScene(
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
		onLog: suspend (String?) -> Unit
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
					onLog("Moved scene $id to new parent ${serverEntity.path.lastOrNull()}")
				}

				existingScene
			} else {
				onLog("Creating new scene $id")
				projectEditorRepository.createScene(parent = parent, sceneName = serverEntity.name)
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
				onLog("Failed to save downloaded scene content for: $id")
			} else {
				onLog("Downloaded scene content for: $id")
				projectEditorRepository.onContentChanged(content)
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
					onLog("Moved scene $id to new parent ${serverEntity.path.lastOrNull()}")
				}

				existingGroup
			} else {
				onLog("Creating new group $id")
				projectEditorRepository.createGroup(parent = parent, groupName = serverEntity.name)
					?: throw IllegalStateException("Failed to create scene")
			}

			val treeNode = tree.find { it.id == id }
			treeNode.value = sceneItem.copy(
				name = serverEntity.name,
				order = serverEntity.order
			)

			onLog("Downloaded scene group for: $id")
		}
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		Napier.d("Re-Id Scene $oldId to $newId")

		projectEditorRepository.reIdScene(oldId, newId)

		draftRepository.reIdScene(
			oldId = oldId,
			newId = newId,
			projectDef = projectDef
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
}
