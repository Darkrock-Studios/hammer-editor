package com.darkrockstudios.apps.hammer.common.data.drafts

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import okio.FileSystem
import okio.IOException
import okio.Path

class SceneDraftsDatasource(
	private val fileSystem: FileSystem,
	private val sceneDatasource: SceneDatasource
) {
	fun getDraftsDirectory(): HPath {
		val sceneDir = sceneDatasource.getSceneDirectory().toOkioPath()

		val path: Path = sceneDir / DRAFTS_DIR
		val directory = path.parent ?: error("Parent path null for Drafts directory: $path")
		fileSystem.createDirectories(path)
		return path.toHPath()
	}

	fun getSceneDraftsDirectory(sceneId: Int): HPath {
		val draftsDir = getDraftsDirectory().toOkioPath()
		val sceneDrafts = draftsDir / sceneId.toString()
		return sceneDrafts.toHPath()
	}

	fun getDraftPath(draftDef: DraftDef): HPath {
		val dir = getSceneDraftsDirectory(draftDef.sceneId)
		val filename = getFilename(draftDef)
		val path = dir.toOkioPath() / filename
		return path.toHPath()
	}

	fun getSceneIdsThatHaveDrafts(): List<Int> {
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

	fun getDraftDef(draftId: Int): DraftDef? {
		val drafts = getSceneIdsThatHaveDrafts().flatMap { sceneId: Int ->
			findDrafts(sceneId)
		}
		return drafts.firstOrNull { draftDef: DraftDef ->
			draftDef.id == draftId
		}
	}

	fun findDrafts(sceneId: Int): List<DraftDef> {
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

	suspend fun getAllDrafts(): Set<DraftDef> {
		val parentDir = getDraftsDirectory().toOkioPath()
		val drafts = fileSystem.list(parentDir)
			.mapNotNull { dir -> dir.name.toIntOrNull() }
			.flatMap { id -> findDrafts(id) }
			.toSet()

		return drafts
	}

	fun deleteDraft(id: Int): Boolean {
		val draftDef = getDraftDef(id)
		return if (draftDef != null) {
			val path = getDraftPath(draftDef).toOkioPath()
			fileSystem.delete(path, false)
			true
		} else {
			false
		}
	}

	fun insertSyncDraft(draftEntity: ApiProjectEntity.SceneDraftEntity): DraftDef? {
		val draftDef = draftEntity.toDraftDef()
		val path = getDraftPath(draftDef).toOkioPath()
		val parentPath = path.parent ?: error("Draft path didn't have parent: $path")
		fileSystem.createDirectories(parentPath)

		fileSystem.write(path, false) {
			writeUtf8(draftEntity.content)
		}

		Napier.i { "Draft Saved: $path" }

		return draftDef
	}

	fun loadDraft(sceneItem: SceneItem, draftDef: DraftDef): SceneContent? {
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

	fun loadDraftContent(draftDef: DraftDef): String? {
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

	fun storeDraft(draftDef: DraftDef, content: String): DraftDef? {
		val path = getDraftPath(draftDef).toOkioPath()
		val parentPath = path.parent ?: error("Draft path didn't have parent: $path")
		fileSystem.createDirectories(parentPath)

		if (fileSystem.exists(path)) {
			Napier.e("saveDraft failed: Draft file already exists: $path")
			return null
		}

		fileSystem.write(path, true) {
			writeUtf8(content)
		}

		Napier.i { "Draft Saved: $path" }
		return draftDef
	}

	suspend fun reIdDraft(oldId: Int, newId: Int) {
		val draftDef = getDraftDef(oldId) ?: error("Draft not found: $oldId")
		val oldPath = getDraftPath(draftDef).toOkioPath()
		val newPath = getDraftPath(draftDef.copy(id = newId)).toOkioPath()

		fileSystem.atomicMove(oldPath, newPath)
	}

	suspend fun reIdScene(oldId: Int, newId: Int) {
		val draftsDir = getSceneDraftsDirectory(oldId).toOkioPath()
		val newDraftsDir = getSceneDraftsDirectory(newId).toOkioPath()

		fileSystem.atomicMove(draftsDir, newDraftsDir)
	}

	fun getFilename(draftDef: DraftDef): String {
		return "${draftDef.sceneId}-${draftDef.id}-${draftDef.draftName}-${draftDef.draftTimestamp.epochSeconds}.md"
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