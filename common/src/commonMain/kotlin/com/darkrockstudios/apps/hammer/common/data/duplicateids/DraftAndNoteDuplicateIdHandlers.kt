package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.filterNotePathsOkio
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Draft ids are global. Copies are ordered like the draft lookup by id: scene directories in
 * listing order, then by timestamp, with the current filename before a legacy one.
 */
internal class DraftDuplicateIdHandler(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
) : DuplicateIdHandler(projectDef, fileSystem) {
	override val kind = DuplicateIdKind.Draft

	private val draftsDir
		get() = projectDir / SceneDatasource.SCENE_DIRECTORY / SceneDraftsDatasource.DRAFTS_DIR

	override val entityType = EntityType.SceneDraft

	override fun ids(): Set<Int> = files().mapTo(mutableSetOf()) { it.def.id }

	private fun files(): List<DraftFile> =
		fileSystem.listOrNull(draftsDir).orEmpty()
			.mapNotNull { dir -> dir.name.toIntOrNull()?.takeIf { it > 0 && fileSystem.metadata(dir).isDirectory }?.let { dir to it } }
			.flatMap { (dir, sceneId) ->
				fileSystem.list(dir)
					.mapNotNull { path ->
						SceneDraftsDatasource.parseDraftFileName(path.name)
							?.takeIf { it.sceneId == sceneId }
							?.let { DraftFile(path, it, SceneDraftsDatasource.DRAFT_FILENAME_PATTERN.matchEntire(path.name) == null) }
					}
					.sortedWith(compareBy({ it.def.draftTimestamp }, { it.legacy }))
			}

	override fun findDuplicates(): List<DuplicateIdSet> =
		files().toDuplicateSets({ it.def.id }) { copyOf(it.path, it.def.draftName, readBytes(it.path)) }

	private data class DraftFile(val path: Path, val def: DraftDef, val legacy: Boolean)

	override fun canonicalPath(copy: DuplicateCopy, id: Int): HPath {
		val def = SceneDraftsDatasource.parseDraftFileName(copy.path.name) ?: error("Not a draft: ${copy.path}")
		return (draftsDir / def.sceneId.toString() / SceneDraftsDatasource.getFilename(def.copy(id = id))).toHPath()
	}
}

/** A note's id is the one in its content, which is what the note loader uses. */
internal class NoteDuplicateIdHandler(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	private val toml: Toml,
) : DuplicateIdHandler(projectDef, fileSystem) {
	override val kind = DuplicateIdKind.Note

	private val notesDir get() = projectDir / NotesDatasource.NOTES_DIRECTORY

	override val entityType = EntityType.Note

	override fun ids(): Set<Int> = files().mapTo(mutableSetOf()) { (_, _, note) -> note.id }

	// The id is in the content, so every note is read.
	private fun files(): List<Triple<Path, ByteString, NoteContent>> =
		listRecursivelyOrEmpty(notesDir).filterNotePathsOkio()
			.mapNotNull { path ->
				val bytes = readBytes(path)
				toml.decodeOrNull<NoteContainer>(bytes)?.note?.let { note -> Triple(path, bytes, note) }
			}
			.toList()

	override fun findDuplicates(): List<DuplicateIdSet> =
		files().toDuplicateSets({ (_, _, note) -> note.id }) { (path, bytes, note) ->
			copyOf(path, firstLine(note.content), bytes, text = note.content)
		}

	override fun canonicalPath(copy: DuplicateCopy, id: Int): HPath =
		(notesDir / NotesDatasource.getNoteFilenameFromId(id)).toHPath()

	override fun claim(copy: DuplicateCopy, owner: DuplicateCopy, oldId: Int, newId: Int): Set<Int> {
		val note = toml.decodeOrNull<NoteContainer>(readBytes(copy.path.toOkioPath()))?.note
			?: error("Note no longer parses: ${copy.path}")
		val rewritten = note.takeIf { it.id != newId }?.let { toml.encodeToString(NoteContainer(it.copy(id = newId))) }
		rewriteOrMove(copy.path, canonicalPath(copy, newId), rewritten)
		return emptySet()
	}

	private fun firstLine(content: String): String =
		content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(NAME_LENGTH) ?: ""

	private companion object {
		const val NAME_LENGTH = 60
	}
}
