package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.ProjectEditorRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import okio.FileSystem
import okio.IOException
import okio.Path

class SceneDraftRepositoryOkio(
	projectDef: ProjectDef,
	projectEditorRepository: ProjectEditorRepository,
	private val fileSystem: FileSystem
) : SceneDraftRepository(projectDef, projectEditorRepository) {

	private val idRepository: IdRepository by projectInject()

	override fun getDraftsDirectory(): HPath {
		val sceneDir = projectEditorRepository.getSceneDirectory().toOkioPath()

		val path: Path = sceneDir / DRAFTS_DIR
		val directory = path.parent ?: error("Parent path null for Drafts directory: $path")
		fileSystem.createDirectories(path)
		return path.toHPath()
	}

	override fun getSceneIdsThatHaveDrafts(): List<Int> {
		val dir = getDraftsDirectory().toOkioPath()
		val sceneIds = fileSystem.list(dir).mapNotNull { path: Path ->
			val possibleSceneId = path.name.toIntOrNull()
			if (possibleSceneId != null && possibleSceneId > 0) {
				possibleSceneId
			} else {
				Napier.w("Found non-numeric directory in Drafts directory: $path")
				null
			}
		}
		return sceneIds
	}

	override fun getDraftDef(draftId: Int): DraftDef? {
		val drafts = getSceneIdsThatHaveDrafts().flatMap { sceneId: Int ->
			findDrafts(sceneId)
		}
		return drafts.firstOrNull { draftDef: DraftDef ->
			draftDef.id == draftId
		}
	}

	override fun getSceneDraftsDirectory(sceneId: Int): HPath {
		val draftsDir = getDraftsDirectory().toOkioPath()
		val sceneDrafts = draftsDir / sceneId.toString()
		return sceneDrafts.toHPath()
	}

	override fun findDrafts(sceneId: Int): List<DraftDef> {
		val draftsDir = getSceneDraftsDirectory(sceneId).toOkioPath()

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

	override fun getDraftPath(draftDef: DraftDef): HPath {
		val dir = getSceneDraftsDirectory(draftDef.sceneId)
		val filename = getFilename(draftDef)
		val path = dir.toOkioPath() / filename
		return path.toHPath()
	}

	override suspend fun reIdDraft(oldId: Int, newId: Int) {
		val draftDef = getDraftDef(oldId) ?: error("Draft not found: $oldId")
		val oldPath = getDraftPath(draftDef).toOkioPath()
		val newPath = getDraftPath(draftDef.copy(id = newId)).toOkioPath()

		fileSystem.atomicMove(oldPath, newPath)
	}

	override suspend fun reIdScene(oldId: Int, newId: Int) {
		val draftsDir = getSceneDraftsDirectory(oldId).toOkioPath()
		val newDraftsDir = getSceneDraftsDirectory(newId).toOkioPath()

		fileSystem.atomicMove(draftsDir, newDraftsDir)
	}

	override fun saveDraft(sceneItem: SceneItem, draftName: String): DraftDef? {
		if (!validDraftName(draftName)) {
			Napier.w { "saveDraft failed, draftName failed validation" }
			return null
		}

		val newId = idRepository.claimNextId()
		val newDraftTimestamp = Clock.System.now()
		val newDef = DraftDef(
			id = newId,
			sceneId = sceneItem.id,
			draftTimestamp = newDraftTimestamp,
			draftName = draftName
		)
		val path = getDraftPath(newDef).toOkioPath()
		val parentPath = path.parent ?: error("Draft path didn't have parent: $path")
		fileSystem.createDirectories(parentPath)

		if (fileSystem.exists(path)) {
			Napier.e("saveDraft failed: Draft file already exists: $path")
			return null
		}

		val existingBuffer = projectEditorRepository.getSceneBuffer(sceneItem.id)
		val content: String = if (existingBuffer != null) {
			Napier.i { "Draft content loaded from memory" }
			existingBuffer.content.coerceMarkdown()
		} else {
			Napier.i { "Draft content loaded from disk" }
			val loadedBuffer = projectEditorRepository.loadSceneBuffer(sceneItem)
			loadedBuffer.content.coerceMarkdown()
		}

		fileSystem.write(path, true) {
			writeUtf8(content)
		}

		Napier.i { "Draft Saved: $path" }

		return newDef
	}

	override fun insertSyncDraft(draftEntity: ApiProjectEntity.SceneDraftEntity): DraftDef? {
		val draftDef = draftEntity.toDraftDef()
		val path = getDraftPath(draftDef).toOkioPath()
		val parentPath = path.parent ?: error("Draft path didn't have parent: $path")
		fileSystem.createDirectories(parentPath)

		if (fileSystem.exists(path)) {
			Napier.e("saveDraft failed: Draft file already exists: $path")
			return null
		}

		fileSystem.write(path, true) {
			writeUtf8(draftEntity.content)
		}

		Napier.i { "Draft Saved: $path" }

		return draftDef
	}

	override fun deleteDraft(id: Int): Boolean {
		val draftDef = getDraftDef(id)
		return if (draftDef != null) {
			val path = getDraftPath(draftDef).toOkioPath()
			fileSystem.delete(path, false)
			true
		} else {
			false
		}
	}

	override fun loadDraft(sceneItem: SceneItem, draftDef: DraftDef): SceneContent? {
		val path = getDraftPath(draftDef).toOkioPath()

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
			Napier.e("Failed to load Draft ${draftDef.id} for scene Scene (${sceneItem.id})")
			null
		}

		return sceneContent
	}

	override fun loadDraftRaw(draftDef: DraftDef): String? {
		val path = getDraftPath(draftDef).toOkioPath()

		if (!fileSystem.exists(path)) {
			Napier.e("loadDraft failed: Draft file already exists: $path")
			return null
		}

		val sceneContent: String? = try {
			fileSystem.read(path) {
				readUtf8()
			}
		} catch (e: IOException) {
			Napier.e("Failed to load draft (${draftDef.id})")
			null
		}

		return sceneContent
	}
}

private fun ApiProjectEntity.SceneDraftEntity.toDraftDef(): DraftDef {
	return DraftDef(
		id = id,
		sceneId = sceneId,
		draftTimestamp = created,
		draftName = name
	)
}