package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath

abstract class SceneDraftRepository(
    protected val projectRepository: ProjectRepository,
) {
    abstract fun getDraftsDirectory(projectDef: ProjectDef): HPath
    abstract fun getSceneDraftsDirectory(projectDef: ProjectDef, sceneId: Int): HPath
    abstract fun findDrafts(
        projectDef: ProjectDef,
        sceneId: Int
    ): List<DraftDef>

    fun getNextSequence(
        projectDef: ProjectDef,
        sceneId: Int
    ): Int {
        val drafts = findDrafts(projectDef, sceneId)
        val latestDraft = drafts.lastOrNull()
        return if (latestDraft != null) {
            latestDraft.draftSequence + 1
        } else {
            0
        }
    }

    abstract fun saveDraft(sceneItem: SceneItem, draftName: String): DraftDef?

    abstract fun loadDraft(sceneItem: SceneItem, draftDef: DraftDef): SceneContent?

    abstract fun getDraftPath(sceneItem: SceneItem, draftDef: DraftDef): HPath

    fun getFilename(draftDef: DraftDef): String {
        return "${draftDef.sceneId}-${draftDef.draftSequence}-${draftDef.draftName}.md"
    }

    companion object {
        const val DRAFTS_DIR = "drafts"
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
                val draftSequence = matches.groups[2]?.value?.toInt()
                val draftName = matches.groups[3]?.value

                if (sceneId == null) error("Failed to parsed Scene ID from draft file name")
                if (draftSequence == null) error("Failed to parsed draft sequence from draft file name")
                if (draftName == null) error("Failed to parsed draft name from draft file name")

                DraftDef(
                    sceneId = sceneId,
                    draftSequence = draftSequence,
                    draftName = draftName
                )
            } else {
                null
            }
        }
    }
}