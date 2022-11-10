package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectrepository.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath

abstract class SceneDraftRepository(
    protected val projectEditorRepository: ProjectEditorRepository
) {
    abstract fun getDraftsDirectory(projectDef: ProjectDef): HPath
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

    companion object {
        const val DRAFTS_DIR = "drafts"
        val SCENE_FILENAME_PATTERN = Regex("""(\d+)-(\d+)-([\da-zA-Z _']+)\.md""")

        fun validDraftFileName(filename: String): Boolean = SCENE_FILENAME_PATTERN.matches(filename)

        fun parseDraftFileName(filename: String): DraftDef? {
            val matches = SCENE_FILENAME_PATTERN.matchEntire(filename)
            return if (validDraftFileName(filename) && matches != null) {
                val sceneId = matches.groups[0]?.value?.toInt()
                val draftSequence = matches.groups[1]?.value?.toInt()
                val draftName = matches.groups[2]?.value

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