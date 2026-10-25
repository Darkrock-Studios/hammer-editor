package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.InvalidEntryFilename
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.filterEntryPathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.ByteString
import okio.FileSystem
import okio.Path

/** Entry ids are global across types. */
internal class EntryDuplicateIdHandler(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	private val toml: Toml,
) : DuplicateIdHandler(projectDef, fileSystem) {
	override val kind = DuplicateIdKind.Entry

	private val images = EntryImageFiles(projectDef, fileSystem)

	override fun findDuplicates(): List<DuplicateIdSet> =
		listRecursivelyOrEmpty(projectDir / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY)
			.filterEntryPathsOkio()
			.mapNotNull { path -> filenameDef(path.toHPath())?.let { path to it } }
			.toList()
			.toDuplicateSets({ (_, def) -> def.id }) { (path, def) ->
				val bytes = readBytes(path)
				val content = decode(bytes)
				copyOf(
					path = path,
					name = content?.name ?: def.name,
					bytes = bytes,
					text = content?.text ?: bytes.utf8(),
				)
			}

	override fun canonicalPath(copy: DuplicateCopy, id: Int): HPath {
		val content = decode(readBytes(copy.path.toOkioPath()))
		return if (content != null) {
			val dir = EncyclopediaDatasource.getTypeDirectory(projectDef, content.type, fileSystem).toOkioPath()
			dir / EncyclopediaDatasource.getEntryFilename(content.copy(id = id))
		} else {
			val def = requireFilenameDef(copy).copy(id = id)
			val dir = EncyclopediaDatasource.getTypeDirectory(projectDef, def.type, fileSystem).toOkioPath()
			dir / EncyclopediaDatasource.getEntryFilename(def)
		}.toHPath()
	}

	override fun claim(copy: DuplicateCopy, owner: DuplicateCopy, oldId: Int, newId: Int): Set<Int> {
		val content = decode(readBytes(copy.path.toOkioPath()))
		val target = canonicalPath(copy, newId)
		if (content != null && content.id != newId) {
			fileSystem.write(target.toOkioPath()) {
				writeUtf8(toml.encodeToString(EntryContainer(content.copy(id = newId))))
			}
			if (target != copy.path) fileSystem.delete(copy.path.toOkioPath())
		} else {
			move(copy.path, target)
		}

		// Images belong to (type, id): one of another type than the owner's leaves with its entry.
		val type = requireFilenameDef(copy).type
		if (newId != oldId && type != requireFilenameDef(owner).type) {
			images.move(type, oldId, newId)
		}
		return emptySet()
	}

	override fun discard(copy: DuplicateCopy, owner: DuplicateCopy): Discarded {
		fileSystem.delete(copy.path.toOkioPath())

		val def = requireFilenameDef(copy)
		if (def.type != requireFilenameDef(owner).type) {
			images.delete(def.type, def.id)
		}
		return Discarded()
	}

	private fun filenameDef(path: HPath): EntryDef? =
		try {
			EncyclopediaDatasource.getEntryDefFromFilename(path.name, projectDef)
		} catch (_: InvalidEntryFilename) {
			null
		}

	private fun requireFilenameDef(copy: DuplicateCopy): EntryDef =
		filenameDef(copy.path) ?: error("Not an entry filename: ${copy.path}")

	@Suppress("SwallowedException")
	private fun decode(bytes: ByteString): EntryContent? =
		try {
			toml.decodeFromString<EntryContainer>(bytes.utf8()).entry
		} catch (_: SerializationException) {
			null
		} catch (_: IllegalArgumentException) {
			null
		} catch (_: IllegalStateException) {
			null
		}
}

/** More than one image for an entry: legacy and current names, or different extensions. */
internal class EntryImageDuplicateIdHandler(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
) : DuplicateIdHandler(projectDef, fileSystem) {
	override val kind = DuplicateIdKind.EntryImage

	private val images = EntryImageFiles(projectDef, fileSystem)

	override fun findDuplicates(): List<DuplicateIdSet> =
		EntryType.entries.flatMap { type ->
			images.all(type).toDuplicateSets({ it.id }) { image ->
				copyOf(image.path, image.path.name, readBytes(image.path))
			}
		}

	override fun canonicalPath(copy: DuplicateCopy, id: Int): HPath {
		val image = images.parse(copy.path.toOkioPath()) ?: error("Not an entry image: ${copy.path}")
		return images.canonicalPath(image.type, id, image.extension).toHPath()
	}
}

internal class EntryImageFiles(
	private val projectDef: ProjectDef,
	private val fileSystem: FileSystem,
) {
	data class Image(val path: Path, val type: EntryType, val id: Int, val extension: String)

	/** In directory order, which is the order the image lookup picks them. */
	fun all(type: EntryType): List<Image> {
		val dir = typeDirectory(type)
		if (!fileSystem.exists(dir)) return emptyList()
		return fileSystem.list(dir).mapNotNull { parse(it) }.filter { it.type == type }
	}

	fun parse(path: Path): Image? {
		val match = IMAGE_PATTERN.matchEntire(path.name) ?: LEGACY_IMAGE_PATTERN.matchEntire(path.name)
			?: return null
		val extension = match.groupValues[3]
		if (extension.lowercase() !in EncyclopediaDatasource.IMAGE_EXTENSIONS) return null
		val type = EntryType.entries.firstOrNull { it.text == match.groupValues[1] } ?: return null
		val id = match.groupValues[2].toIntOrNull() ?: return null
		return Image(path, type, id, extension)
	}

	fun canonicalPath(type: EntryType, id: Int, extension: String): Path =
		typeDirectory(type) / "${type.text}~$id~image.$extension"

	fun move(type: EntryType, oldId: Int, newId: Int) {
		all(type).filter { it.id == oldId }.forEach { image ->
			fileSystem.atomicMove(image.path, canonicalPath(type, newId, image.extension))
		}
	}

	fun delete(type: EntryType, id: Int) {
		all(type).filter { it.id == id }.forEach { fileSystem.delete(it.path) }
	}

	private fun typeDirectory(type: EntryType): Path =
		projectDef.path.toOkioPath() / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY / type.text

	private companion object {
		val IMAGE_PATTERN = Regex("""([a-zA-Z]+)~(\d+)~image\.([a-zA-Z0-9]+)""")
		val LEGACY_IMAGE_PATTERN = EncyclopediaDatasource.LEGACY_ENTRY_IMAGE_FILENAME_PATTERN
	}
}
