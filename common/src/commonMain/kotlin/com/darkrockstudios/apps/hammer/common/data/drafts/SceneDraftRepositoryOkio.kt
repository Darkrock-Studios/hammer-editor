package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectrepository.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.FileSystem
import okio.Path


class SceneDraftRepositoryOkio(
    projectEditorRepository: ProjectEditorRepository,
    private val fileSystem: FileSystem
) : SceneDraftRepository(projectEditorRepository) {

    override fun getDraftsDirectory(projectDef: ProjectDef): HPath {
        val path: Path = projectDef.path.toOkioPath() / DRAFTS_DIR
        val directory = path.parent ?: error("Parent path null for Drafts directory: $path")
        fileSystem.createDirectories(directory)
        return path.toHPath()
    }

    override fun findDrafts(
        projectDef: ProjectDef,
        sceneId: Int
    ): List<DraftDef> {
        val draftsDir = getDraftsDirectory(projectDef).toOkioPath()

        val drafts = fileSystem.list(draftsDir).filter { path: Path ->
            validDraftFileName(path.name)
        }.mapNotNull { path: Path ->
            parseDraftFileName(path.name)
        }.filter { draftDef: DraftDef ->
            draftDef.sceneId == sceneId
        }.sortedBy { draftDef: DraftDef ->
            draftDef.draftSequence
        }

        return drafts
    }
}