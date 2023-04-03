package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.datetime.Instant

abstract class SceneDraftRepository(
	protected val projectDef: ProjectDef,
	protected val projectEditorRepository: ProjectEditorRepository,
) : ProjectScoped {
	override val projectScope = ProjectDefScope(projectDef)

	abstract fun getDraftsDirectory(): HPath
	abstract fun getSceneDraftsDirectory(sceneId: Int): HPath
	abstract fun findDrafts(sceneId: Int): List<DraftDef>

	abstract fun saveDraft(sceneItem: SceneItem, draftName: String): DraftDef?

	abstract fun loadDraft(sceneItem: SceneItem, draftDef: DraftDef): SceneContent?

	abstract fun getDraftPath(sceneItem: SceneItem, draftDef: DraftDef): HPath

	fun getFilename(draftDef: DraftDef): String {
		return "${draftDef.sceneId}-${draftDef.id}-${draftDef.draftName}-${draftDef.draftTimestamp.epochSeconds}.md"
	}

	abstract fun reIdScene(oldId: Int, newId: Int, projectDef: ProjectDef)

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