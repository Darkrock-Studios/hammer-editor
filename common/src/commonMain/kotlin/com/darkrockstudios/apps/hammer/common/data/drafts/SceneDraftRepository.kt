package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock

class SceneDraftRepository(
	projectDef: ProjectDef,
	private val sceneEditorRepository: SceneEditorRepository,
	private val datasource: SceneDraftsDatasource,
	private val clock: Clock,
) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	private val idRepository: IdRepository by projectInject()
	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()

	suspend fun getAllDrafts(): Set<DraftDef> = datasource.getAllDrafts()
	fun getSceneIdsThatHaveDrafts(): List<Int> = datasource.getSceneIdsThatHaveDrafts()
	fun getDraftDef(draftId: Int): DraftDef? = datasource.getDraftDef(draftId)
	fun findDrafts(sceneId: Int): List<DraftDef> = datasource.findDrafts(sceneId)
	fun loadDraft(sceneItem: SceneItem, draftDef: DraftDef): SceneContent? =
		datasource.loadDraft(sceneItem, draftDef)

	fun loadDraftContent(draftDef: DraftDef): String? = datasource.loadDraftContent(draftDef)
	suspend fun reIdDraft(oldId: Int, newId: Int) = datasource.reIdDraft(oldId, newId)
	suspend fun reIdScene(oldId: Int, newId: Int) = datasource.reIdScene(oldId, newId)
	fun insertSyncDraft(draftEntity: ApiProjectEntity.SceneDraftEntity): DraftDef? =
		datasource.insertSyncDraft(draftEntity)

	fun deleteDraft(id: Int): Boolean = datasource.deleteDraft(id)

	suspend fun saveDraft(sceneItem: SceneItem, draftName: String): DraftDef? {
		if (!SceneDraftsDatasource.validDraftName(draftName)) {
			Napier.w { "saveDraft failed, draftName failed validation" }
			return null
		}

		val newId = idRepository.claimNextId()
		val newDraftTimestamp = clock.now()
		val newDef = DraftDef(
			id = newId,
			sceneId = sceneItem.id,
			draftTimestamp = newDraftTimestamp,
			draftName = draftName
		)

		val existingBuffer = sceneEditorRepository.getSceneBuffer(sceneItem.id)
		val content: String = if (existingBuffer != null) {
			Napier.i { "Draft content loaded from memory" }
			existingBuffer.content.coerceMarkdown()
		} else {
			Napier.i { "Draft content loaded from disk" }
			val loadedBuffer = sceneEditorRepository.loadSceneBuffer(sceneItem)
			loadedBuffer.content.coerceMarkdown()
		}

		datasource.storeDraft(newDef, content)

		return newDef
	}

	/**
	 * Drafts are never edited after creation. Creation marks them to be synced implicitly, then
	 * after the fact, we should never need to mark them for sync again, so this is unused.
	 * But I'm leaving it here just in case we need it at some point.
	 */
	protected suspend fun markForSynchronization(originalDef: DraftDef, originalContent: String) {
		if (projectSynchronizer.isServerSynchronized() && !projectSynchronizer.isEntityDirty(originalDef.id)) {
			val hash = EntityHasher.hashSceneDraft(
				id = originalDef.id,
				created = originalDef.draftTimestamp,
				name = originalDef.draftName,
				content = originalContent,
			)
			projectSynchronizer.markEntityAsDirty(originalDef.id, hash)
		}
	}
}