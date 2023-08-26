package com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier

class ClientSceneDraftSynchronizer(
	projectDef: ProjectDef,
	serverProjectApi: ServerProjectApi,
	private val strRes: StrRes,
) : EntitySynchronizer<ApiProjectEntity.SceneDraftEntity>(projectDef, serverProjectApi), ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	private val sceneDraftRepository: SceneDraftRepository by projectInject()

	override suspend fun prepareForSync() {

	}

	override suspend fun ownsEntity(id: Int): Boolean {
		return sceneDraftRepository.getDraftDef(draftId = id) != null
	}

	override suspend fun getEntityHash(id: Int): String? {
		val draftDef = sceneDraftRepository.getDraftDef(draftId = id)
		return if (draftDef != null) {
			val content: String? = sceneDraftRepository.loadDraftRaw(draftDef)
			if (content == null) {
				Napier.e("Failed to load draft content for draft ${draftDef.id}")
			}

			EntityHasher.hashSceneDraft(
				id = draftDef.id,
				created = draftDef.draftTimestamp,
				name = draftDef.draftName,
				content = content ?: "",
			)
		} else {
			null
		}
	}

	override suspend fun createEntityForId(id: Int): ApiProjectEntity.SceneDraftEntity {
		val draftDef = sceneDraftRepository.getDraftDef(draftId = id)
		return if (draftDef != null) {
			val content: String? = sceneDraftRepository.loadDraftRaw(draftDef)
			if (content == null) {
				Napier.e("Failed to load draft content for draft ${draftDef.id}")
			}

			ApiProjectEntity.SceneDraftEntity(
				id = draftDef.id,
				sceneId = draftDef.sceneId,
				name = draftDef.draftName,
				created = draftDef.draftTimestamp,
				content = content ?: "",
			)
		} else {
			error("Failed to find draft for id $id")
		}
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		sceneDraftRepository.reIdDraft(oldId, newId)
	}

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.SceneDraftEntity,
		syncId: String,
		onLog: OnSyncLog
	): Boolean {
		sceneDraftRepository.insertSyncDraft(serverEntity)
		return true
	}

	override suspend fun finalizeSync() {

	}

	override fun getEntityType() = EntityType.SceneDraft

	override suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		sceneDraftRepository.deleteDraft(id)
		onLog(syncLogI(strRes.get(MR.strings.sync_draft_deleted, id), projectDef))
	}

	override suspend fun hashEntities(newIds: List<Int>): Set<EntityHash> {
		return sceneDraftRepository.getAllDrafts()
			.filter { newIds.contains(it.id).not() }
			.mapNotNull { draft ->
				getEntityHash(draft.id)?.let { hash ->
					EntityHash(draft.id, hash)
				}
			}
			.toSet()
	}
}