package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.datetime.Instant

abstract class SceneDraftRepository(
	protected val projectEditorRepository: ProjectEditorRepository,
) {
    abstract fun getDraftsDirectory(projectDef: ProjectDef): HPath
    abstract fun getSceneDraftsDirectory(projectDef: ProjectDef, sceneId: Int): HPath
    abstract fun findDrafts(
        projectDef: ProjectDef,
        sceneId: Int
    ): List<DraftDef>

    abstract fun saveDraft(sceneItem: SceneItem, draftName: String): DraftDef?

    abstract fun loadDraft(sceneItem: SceneItem, draftDef: DraftDef): SceneContent?

    abstract fun getDraftPath(sceneItem: SceneItem, draftDef: DraftDef): HPath

    fun getFilename(draftDef: DraftDef): String {
        return "${draftDef.sceneId}-${draftDef.draftTimestamp.epochSeconds}-${draftDef.draftName}.md"
    }

    companion object {
        const val DRAFTS_DIR = ".drafts"
        val DRAFT_FILENAME_PATTERN = Regex("""(\d+)-(\d+)-([\da-zA-Z _']+)\.md""")
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
                val draftTimestamp = matches.groups[2]?.value?.toLong()
                val draftName = matches.groups[3]?.value

                if (sceneId == null) error("Failed to parsed Scene ID from draft file name")
                if (draftTimestamp == null) error("Failed to parsed draft sequence from draft file name")
                if (draftName == null) error("Failed to parsed draft name from draft file name")

                val draftCreatedAt = Instant.fromEpochSeconds(draftTimestamp, 0)

                DraftDef(
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