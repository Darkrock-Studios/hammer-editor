package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.filterNotePathsOkio
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.ByteString
import okio.FileSystem

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

	override fun findDuplicates(): List<DuplicateIdSet> =
		listOrEmpty(draftsDir)
			.mapNotNull { dir -> dir.name.toIntOrNull()?.takeIf { it > 0 && fileSystem.metadata(dir).isDirectory }?.let { dir to it } }
			.flatMap { (dir, sceneId) ->
				fileSystem.list(dir)
					.mapNotNull { path ->
						SceneDraftsDatasource.parseDraftFileName(path.name)
							?.takeIf { it.sceneId == sceneId }
							?.let { path to it }
					}
					.sortedWith(
						compareBy(
							{ (_, def) -> def.draftTimestamp },
							{ (path, _) -> SceneDraftsDatasource.DRAFT_FILENAME_PATTERN.matchEntire(path.name) == null },
						)
					)
			}
			.toDuplicateSets({ (_, def) -> def.id }) { (path, def) -> copyOf(path, def.draftName, readBytes(path)) }

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

	// The id is in the content, so every note is read.
	override fun findDuplicates(): List<DuplicateIdSet> =
		listRecursivelyOrEmpty(notesDir).filterNotePathsOkio()
			.mapNotNull { path ->
				val bytes = readBytes(path)
				decode(bytes)?.let { note -> Triple(path, bytes, note) }
			}
			.toList()
			.toDuplicateSets({ (_, _, note) -> note.id }) { (path, bytes, note) ->
				copyOf(path, firstLine(note.content), bytes, text = note.content)
			}

	override fun canonicalPath(copy: DuplicateCopy, id: Int): HPath =
		(notesDir / NotesDatasource.getNoteFilenameFromId(id)).toHPath()

	override fun claim(copy: DuplicateCopy, owner: DuplicateCopy, oldId: Int, newId: Int): Set<Int> {
		val note = decode(readBytes(copy.path.toOkioPath())) ?: error("Note no longer parses: ${copy.path}")
		val target = canonicalPath(copy, newId)
		if (note.id != newId) {
			fileSystem.write(target.toOkioPath()) {
				writeUtf8(toml.encodeToString(NoteContainer(note.copy(id = newId))))
			}
			if (target != copy.path) fileSystem.delete(copy.path.toOkioPath())
		} else {
			move(copy.path, target)
		}
		return emptySet()
	}

	private fun firstLine(content: String): String =
		content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(NAME_LENGTH) ?: ""

	@Suppress("SwallowedException")
	private fun decode(bytes: ByteString): NoteContent? =
		try {
			toml.decodeFromString<NoteContainer>(bytes.utf8()).note
		} catch (_: SerializationException) {
			null
		} catch (_: IllegalArgumentException) {
			null
		} catch (_: IllegalStateException) {
			null
		}

	private companion object {
		const val NAME_LENGTH = 60
	}
}
