package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.projectrepository.ProjectRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import okio.FileSystem
import okio.IOException
import okio.Path


class SceneDraftRepositoryOkio(
    projectRepository: ProjectRepository,
    private val fileSystem: FileSystem
) : SceneDraftRepository(projectRepository) {

    override fun getDraftsDirectory(projectDef: ProjectDef): HPath {
        val editor = projectRepository.getProjectEditor(projectDef)
        val sceneDir = editor.getSceneDirectory().toOkioPath()

        val path: Path = sceneDir / DRAFTS_DIR
        val directory = path.parent ?: error("Parent path null for Drafts directory: $path")
        fileSystem.createDirectories(path)
        return path.toHPath()
    }

    override fun getSceneDraftsDirectory(projectDef: ProjectDef, sceneId: Int): HPath {
        val draftsDir = getDraftsDirectory(projectDef).toOkioPath()
        val sceneDrafts = draftsDir / sceneId.toString()
        return sceneDrafts.toHPath()
    }

    override fun findDrafts(
        projectDef: ProjectDef,
        sceneId: Int
    ): List<DraftDef> {
        val draftsDir = getSceneDraftsDirectory(projectDef, sceneId).toOkioPath()

        val drafts = if (fileSystem.exists(draftsDir)) {
            fileSystem.list(draftsDir).filter { path: Path ->
                validDraftFileName(path.name)
            }.mapNotNull { path: Path ->
                parseDraftFileName(path.name)
            }.filter { draftDef: DraftDef ->
                draftDef.sceneId == sceneId
            }.sortedBy { draftDef: DraftDef ->
                draftDef.draftTimestamp
            }
        } else {
            emptyList()
        }

        return drafts
    }

    override fun getDraftPath(sceneItem: SceneItem, draftDef: DraftDef): HPath {
        val dir = getSceneDraftsDirectory(sceneItem.projectDef, sceneItem.id)
        val filename = getFilename(draftDef)
        val path = dir.toOkioPath() / filename
        return path.toHPath()
    }

    override fun saveDraft(sceneItem: SceneItem, draftName: String): DraftDef? {
        if (!validDraftName(draftName)) {
            Napier.w { "saveDraft failed, draftName failed validation" }
            return null
        }

        val newDraftTimestamp = Clock.System.now()
        val newDef = DraftDef(
            sceneId = sceneItem.id,
            draftTimestamp = newDraftTimestamp,
            draftName = draftName
        )
        val path = getDraftPath(sceneItem, newDef).toOkioPath()
        val parentPath = path.parent ?: error("Draft path didn't have parent: $path")
        fileSystem.createDirectories(parentPath)

        if (fileSystem.exists(path)) {
            Napier.e("saveDraft failed: Draft file already exists: $path")
            return null
        }

        val editor = projectRepository.getProjectEditor(sceneItem.projectDef)

        val existingBuffer = editor.getSceneBuffer(sceneItem)
        val content: String = if (existingBuffer != null) {
            Napier.i { "Draft content loaded from memory" }
            existingBuffer.content.coerceMarkdown()
        } else {
            Napier.i { "Draft content loaded from disk" }
            val loadedBuffer = editor.loadSceneBuffer(sceneItem)
            loadedBuffer.content.coerceMarkdown()
        }

        fileSystem.write(path, true) {
            writeUtf8(content)
        }

        Napier.i { "Draft Saved: $path" }

        return newDef
    }

    override fun loadDraft(sceneItem: SceneItem, draftDef: DraftDef): SceneContent? {
        val path = getDraftPath(sceneItem, draftDef).toOkioPath()

        if (!fileSystem.exists(path)) {
            Napier.e("loadDraft failed: Draft file already exists: $path")
            return null
        }

        val sceneContent: SceneContent? = try {
            fileSystem.read(path) {
                val content = readUtf8()
                SceneContent(
                    scene = sceneItem,
                    markdown = content
                )
            }
        } catch (e: IOException) {
            Napier.e("Failed to load Scene (${sceneItem.name})")
            null
        }

        return sceneContent
    }
}