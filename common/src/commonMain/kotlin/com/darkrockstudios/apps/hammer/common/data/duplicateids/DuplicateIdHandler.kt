package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.Toml
import okio.ByteString
import okio.FileSystem
import okio.Path

/** Finds and resolves files sharing an id in one store. File access is blocking. */
internal abstract class DuplicateIdHandler(
	protected val projectDef: ProjectDef,
	protected val fileSystem: FileSystem,
) {
	abstract val kind: DuplicateIdKind

	/** Sets with copies ordered the way this store's read path picks them. */
	abstract fun findDuplicates(): List<DuplicateIdSet>

	/** Where [copy] belongs when it is the one file holding [id]. */
	abstract fun canonicalPath(copy: DuplicateCopy, id: Int): HPath

	/**
	 * Moves [copy] to its canonical path for [newId], rewriting any id stored in its content.
	 * Returns other ids whose synced state changed as a result.
	 */
	open fun claim(copy: DuplicateCopy, owner: DuplicateCopy, oldId: Int, newId: Int): Set<Int> {
		move(copy.path, canonicalPath(copy, newId))
		return emptySet()
	}

	open fun discard(copy: DuplicateCopy, owner: DuplicateCopy): Discarded {
		fileSystem.delete(copy.path.toOkioPath())
		return Discarded()
	}

	protected fun move(from: HPath, to: HPath) {
		if (from != to) fileSystem.atomicMove(from.toOkioPath(), to.toOkioPath())
	}

	/** Writes [rewritten] to [target] and drops [from], or just moves [from] when null. */
	protected fun rewriteOrMove(from: HPath, target: HPath, rewritten: String?) {
		if (rewritten == null) return move(from, target)
		fileSystem.write(target.toOkioPath()) { writeUtf8(rewritten) }
		if (target != from) fileSystem.delete(from.toOkioPath())
	}

	protected fun readBytes(path: Path): ByteString = fileSystem.read(path) { readByteString() }

	protected fun copyOf(
		path: Path,
		name: String,
		bytes: ByteString?,
		text: String = bytes?.utf8() ?: "",
		archived: Boolean = false,
	): DuplicateCopy {
		val metadata = fileSystem.metadataOrNull(path)
		return DuplicateCopy(
			path = path.toHPath(),
			relativePath = path.relativeTo(projectDir).segments.joinToString("/"),
			name = name,
			isGroup = bytes == null,
			archived = archived,
			sizeBytes = metadata?.size,
			modifiedAtMillis = metadata?.lastModifiedAtMillis,
			text = text,
			contentHash = bytes?.md5()?.hex(),
		)
	}

	/** Groups by the id in each filename first, so only files that share an id are read. */
	protected fun <T> List<T>.toDuplicateSets(idOf: (T) -> Int, copyOf: (T) -> DuplicateCopy): List<DuplicateIdSet> =
		groupBy(idOf)
			.filterValues { it.size > 1 }
			.map { (id, files) -> DuplicateIdSet(kind, id, files.map(copyOf)) }

	/** Lists without creating the directory, which the stores' own directory getters do. */
	protected fun listRecursivelyOrEmpty(dir: Path): Sequence<Path> =
		if (fileSystem.exists(dir)) fileSystem.listRecursively(dir) else emptySequence()

	protected val projectDir: Path get() = projectDef.path.toOkioPath()
}

@Suppress("SwallowedException")
internal inline fun <reified T : Any> Toml.decodeOrNull(bytes: ByteString): T? =
	try {
		decodeFromString<T>(bytes.utf8())
	} catch (_: SerializationException) {
		null
	} catch (_: IllegalArgumentException) {
		null
	} catch (_: IllegalStateException) {
		null
	}

internal data class Discarded(
	val markDirty: Set<Int> = emptySet(),
	/** Paths that moved while discarding, such as the children of a discarded scene group. */
	val moved: Map<HPath, HPath> = emptyMap(),
)
