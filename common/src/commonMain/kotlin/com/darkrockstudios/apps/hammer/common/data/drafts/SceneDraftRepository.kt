package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import com.darkrockstudios.apps.hammer.common.data.*
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.data.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.datetime.Instant

abstract class SceneDraftRepository(
	protected val projectDef: ProjectDef,
	protected val projectEditorRepository: ProjectEditorRepository,
) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	private val projectSynchronizer: ClientProjectSynchronizer by projectInject()

	abstract fun getDraftsDirectory(): HPath
	abstract fun getSceneIdsThatHaveDrafts(): List<Int>
	abstract fun getDraftDef(draftId: Int): DraftDef?
	abstract fun getSceneDraftsDirectory(sceneId: Int): HPath
	abstract fun findDrafts(sceneId: Int): List<DraftDef>

	abstract fun saveDraft(sceneItem: SceneItem, draftName: String): DraftDef?

	abstract fun loadDraft(sceneItem: SceneItem, draftDef: DraftDef): SceneContent?

	abstract fun loadDraftRaw(draftDef: DraftDef): String?

	abstract fun getDraftPath(draftDef: DraftDef): HPath
	abstract suspend fun reIdDraft(oldId: Int, newId: Int)
	abstract suspend fun reIdScene(oldId: Int, newId: Int)

	fun getFilename(draftDef: DraftDef): String {
		return "${draftDef.sceneId}-${draftDef.id}-${draftDef.draftName}-${draftDef.draftTimestamp.epochSeconds}.md"
	}

	abstract fun insertSyncDraft(draftEntity: ApiProjectEntity.SceneDraftEntity): DraftDef?

	protected fun markForSynchronization(originalDef: DraftDef, originalContent: String) {
		if (projectSynchronizer.isServerSynchronized() && !projectSynchronizer.isEntityDirty(originalDef.id)) {
			val hash = EntityHash.hashSceneDraft(
				id = originalDef.id,
				created = originalDef.draftTimestamp,
				name = originalDef.draftName,
				content = originalContent,
			)
			projectSynchronizer.markEntityAsDirty(originalDef.id, hash)
		}
	}

	companion object {
		const val DRAFTS_DIR = ".drafts"
		val DRAFT_FILENAME_PATTERN = Regex("""(\d+)-(\d+)-([\da-zA-Z _']+)-(\d+)\.md""")
		val DRAFT_NAME_PATTERN = Regex("""[\da-zA-Z _']+""")
		val MAX_DRAFT_NAME_LENGTH = 128

		fun validDraftName(name: String): Boolean {
			return name.length <= MAX_DRAFT_NAME_LENGTH && DRAFT_NAME_PATTERN.matches(name)
		}

		fun validDraftFileName(filename: String): Boolean = DRAFT_FILENAME_PATTERN.matches(filename)

		fun parseDraftFileName(filename: String): DraftDef? {
			val matches = DRAFT_FILENAME_PATTERN.matchEntire(filename)
			return if (validDraftFileName(filename) && matches != null) {
				val sceneId = matches.groups[1]?.value?.toInt()
				val draftId = matches.groups[2]?.value?.toInt()
				val draftName = matches.groups[3]?.value
				val draftTimestamp = matches.groups[4]?.value?.toLong()

				if (draftId == null) error("Failed to parsed draft ID from draft file name")
				if (sceneId == null) error("Failed to parsed Scene ID from draft file name")
				if (draftTimestamp == null) error("Failed to parsed draft sequence from draft file name")
				if (draftName == null) error("Failed to parsed draft name from draft file name")

				val draftCreatedAt = Instant.fromEpochSeconds(draftTimestamp, 0)

				DraftDef(
					id = draftId,
					sceneId = sceneId,
					draftTimestamp = draftCreatedAt,
					draftName = draftName
				)
			} else {
				null
			}
		}
	}
}