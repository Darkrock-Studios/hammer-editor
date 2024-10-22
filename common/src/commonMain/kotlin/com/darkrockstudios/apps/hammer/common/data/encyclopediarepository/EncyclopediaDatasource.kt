package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource.Companion.ENTRY_FILENAME_PATTERN
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.InvalidSceneFilename
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.Path

class EncyclopediaDatasource(
	private val projectDef: ProjectDef,
	private val toml: Toml,
	private val fileSystem: FileSystem,
	private val externalFileIo: ExternalFileIo,
) {
	private fun getTypeDirectory(type: EntryType): HPath {
		return getTypeDirectory(projectDef, type, fileSystem)
	}

	private fun getEncyclopediaDirectory(): HPath {
		return getEncyclopediaDirectory(projectDef, fileSystem)
	}

	fun getEntryPath(entryContent: EntryContent): HPath {
		val dir = getTypeDirectory(entryContent.type).toOkioPath()
		val filename = getEntryFilename(entryContent)
		val path = dir / filename
		return path.toHPath()
	}

	fun getEntryPath(entryDef: EntryDef): HPath {
		val dir = getTypeDirectory(entryDef.type).toOkioPath()
		val filename = getEntryFilename(entryDef)
		val path = dir / filename
		return path.toHPath()
	}

	fun getEntryPath(id: Int): HPath {
		return findEntryPath(id) ?: throw EntryNotFound(id)
	}

	fun findEntryPath(id: Int): HPath? {
		var path: HPath? = null

		val types = EntryType.entries.toTypedArray()
		for (type in types) {
			val typeDir = getTypeDirectory(type).toOkioPath()
			val files = fileSystem.listRecursively(typeDir)
			for (file in files) {
				try {
					val entryId = getEntryIdFromFilename(file.name)
					if (id == entryId) {
						path = file.toHPath()
						break
					}
				} catch (_: IllegalStateException) {
				}
			}
			if (path != null) break
		}

		return path
	}

	fun getEntryImagePath(entryDef: EntryDef, fileExtension: String): HPath {
		val dir = getTypeDirectory(entryDef.type).toOkioPath()
		val filename = getEntryImageFilename(entryDef, fileExtension)
		val path = dir / filename
		return path.toHPath()
	}

	fun hasEntryImage(entryDef: EntryDef, fileExension: String): Boolean {
		val path = getEntryImagePath(entryDef, fileExension).toOkioPath()
		return fileSystem.exists(path)
	}

	suspend fun removeEntryImage(entryDef: EntryDef): Boolean {
		val imagePath = getEntryImagePath(entryDef, "jpg").toOkioPath()

		return try {
			fileSystem.delete(imagePath)
			true
		} catch (e: IOException) {
			Napier.w("Message: " + e.message)
			Napier.w("Failed to delete Entry Image: $imagePath", e)
			false
		}
	}

	suspend fun loadEntriesImperative(): List<EntryDef> {
		val dir = getEncyclopediaDirectory().toOkioPath()
		val entryPaths = fileSystem.listRecursively(dir).filterEntryPathsOkio().toList()
		val entryDefs = entryPaths.map { path -> getEntryDef(path.toHPath()) }
		return entryDefs
	}

	fun loadEntry(entryDef: EntryDef): EntryContainer {
		val path = getEntryPath(entryDef)
		return loadEntry(path)
	}

	fun loadEntry(entryPath: HPath): EntryContainer {
		try {
			val path = entryPath.toOkioPath()
			val contentToml: String = fileSystem.read(path) {
				readUtf8()
			}

			val entry: EntryContainer = toml.decodeFromString(contentToml)
			return entry
		} catch (e: Exception) {
			throw EntryLoadError(entryPath, e)
		}
	}

	suspend fun reIdEntry(oldId: Int, newId: Int) {
		val oldDef = getEntryDef(oldId)
		val newDef = oldDef.copy(id = newId)
		if (hasEntryImage(oldDef, DEFAULT_IMAGE_EXT)) {
			val oldImagePath = getEntryImagePath(oldDef, DEFAULT_IMAGE_EXT).toOkioPath()
			val newImagePath = getEntryImagePath(newDef, DEFAULT_IMAGE_EXT).toOkioPath()
			fileSystem.atomicMove(oldImagePath, newImagePath)
		}

		val oldPath = getEntryPath(oldDef).toOkioPath()
		val newPath = getEntryPath(newDef).toOkioPath()
		fileSystem.atomicMove(oldPath, newPath)

		loadEntriesImperative()
	}

	fun getEntryDef(entryPath: HPath): EntryDef {
		return getEntryDefFromFilename(entryPath.name, projectDef)
	}

	fun getEntryDef(id: Int): EntryDef {
		val path = getEntryPath(id)
		return getEntryDefFromFilename(path.name, projectDef)
	}

	fun findEntryDef(id: Int): EntryDef? {
		val path = findEntryPath(id)
		return if (path != null) {
			getEntryDefFromFilename(path.name, projectDef)
		} else {
			null
		}
	}

	fun loadEntryImage(entryDef: EntryDef, fileExtension: String): ByteArray {
		val imagePath = getEntryImagePath(entryDef, fileExtension)
		fileSystem.read(imagePath.toOkioPath()) {
			return readByteArray()
		}
	}

	suspend fun createEntry(container: EntryContainer): EntryDef {
		val entryToml = toml.encodeToString(container)

		val path = getEntryPath(container.entry).toOkioPath()

		fileSystem.write(path) {
			writeUtf8(entryToml)
		}

		return container.entry.toDef(projectDef)
	}

	suspend fun setEntryImage(entryDef: EntryDef, imagePath: String?) {
		val targetPath = getEntryImagePath(entryDef, "jpg").toOkioPath()
		if (imagePath != null) {
			val pixelData = externalFileIo.readExternalFile(imagePath)
			fileSystem.write(targetPath) {
				write(pixelData)
			}
		} else {
			fileSystem.delete(targetPath)
		}
	}

	suspend fun deleteEntry(entryDef: EntryDef): Boolean {
		val path = getEntryPath(entryDef).toOkioPath()
		fileSystem.delete(path)

		val imagePath = getEntryImagePath(entryDef, "jpg").toOkioPath()
		fileSystem.delete(imagePath)

		return true
	}

	suspend fun updateEntry(
		oldEntryDef: EntryDef,
		name: String,
		text: String,
		tags: Set<String>,
	): EntryContainer {
		val oldPath = getEntryPath(oldEntryDef.id).toOkioPath()
		fileSystem.delete(oldPath)

		val entry = EntryContent(
			id = oldEntryDef.id,
			name = name.trim(),
			type = oldEntryDef.type,
			text = text.trim(),
			tags = tags
		)
		val container = EntryContainer(entry)
		val entryToml = toml.encodeToString(container)

		val path = getEntryPath(entry).toOkioPath()

		fileSystem.write(path) {
			writeUtf8(entryToml)
		}

		return container
	}

	companion object {
		val ENTRY_NAME_PATTERN = Regex("""([\d\p{L}+ _']+)""")
		val ENTRY_FILENAME_PATTERN = Regex("""([a-zA-Z]+)-(\d+)-([\d\p{L}+ _']+)\.toml""")
		const val ENCYCLOPEDIA_DIRECTORY = "encyclopedia"

		fun getEntryFilename(entryDef: EntryDef): String =
			getEntryFilename(
				id = entryDef.id,
				type = entryDef.type,
				name = entryDef.name
			)

		fun getEntryImageFilename(entryDef: EntryDef, fileExtension: String): String =
			getEntryImageFilename(
				id = entryDef.id,
				type = entryDef.type,
				fileExtension = fileExtension
			)

		fun getEntryFilename(entry: EntryContent): String =
			getEntryFilename(
				id = entry.id,
				type = entry.type,
				name = entry.name
			)

		private fun getEntryFilename(id: Int, type: EntryType, name: String): String {
			return "${type.text}-$id-$name.toml"
		}

		private fun getEntryImageFilename(id: Int, type: EntryType, fileExtension: String): String {
			return "${type.text}-$id-image.$fileExtension"
		}

		fun getEntryIdFromFilename(fileName: String): Int {
			val captures = ENTRY_FILENAME_PATTERN.matchEntire(fileName)
				?: throw IllegalStateException("Entry filename was bad: $fileName")
			try {
				val entryId = captures.groupValues[2].toInt()
				return entryId
			} catch (e: NumberFormatException) {
				throw InvalidSceneFilename("Number format exception", fileName)
			} catch (e: IllegalStateException) {
				throw InvalidSceneFilename("Invalid filename", fileName)
			}
		}

		fun getEntryDefFromFilename(fileName: String, projectDef: ProjectDef): EntryDef {
			val captures = ENTRY_FILENAME_PATTERN.matchEntire(fileName)
				?: throw IllegalStateException("Entry filename was bad: $fileName")
			try {
				val typeString = captures.groupValues[1]
				val entryId = captures.groupValues[2].toInt()
				val entryName = captures.groupValues[3]

				val type = EntryType.fromString(typeString)

				val def = EntryDef(
					projectDef = projectDef,
					id = entryId,
					type = type,
					name = entryName
				)
				return def
			} catch (e: NumberFormatException) {
				throw InvalidEntryFilename("Number format exception", fileName)
			} catch (e: IllegalStateException) {
				throw InvalidEntryFilename("Invalid filename", fileName)
			} catch (e: IllegalArgumentException) {
				throw InvalidEntryFilename(e.message ?: "Invalid filename argument", fileName)
			}
		}

		const val DEFAULT_IMAGE_EXT = "jpg"
		fun getTypeDirectory(
			projectDef: ProjectDef,
			type: EntryType,
			fileSystem: FileSystem
		): HPath {
			val parentDir: Path = getEncyclopediaDirectory(projectDef, fileSystem).toOkioPath()
			val typePath = parentDir / type.text
			if (!fileSystem.exists(typePath)) {
				fileSystem.createDirectories(typePath)
			}

			return typePath.toHPath()
		}

		fun getEncyclopediaDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val projOkPath = projectDef.path.toOkioPath()
			val sceneDirPath = projOkPath / ENCYCLOPEDIA_DIRECTORY
			if (!fileSystem.exists(sceneDirPath)) {
				fileSystem.createDirectories(sceneDirPath)
			}
			return sceneDirPath.toHPath()
		}
	}
}

class EntryNotFound(val id: Int) : IllegalArgumentException("Failed to find Entry for ID: $id")

fun Sequence<Path>.filterEntryPathsOkio() =
	map { it.toHPath() }
		.filterEntryPaths()
		.map { it.toOkioPath() }
		.filter { path -> !path.segments.any { part -> part.startsWith(".") } }

fun Sequence<HPath>.filterEntryPaths() = filter {
	!it.name.startsWith(".") && ENTRY_FILENAME_PATTERN.matches(it.name)
}.sortedBy { it.name }

open class InvalidEntryFilename(message: String, fileName: String) :
	IllegalStateException("$fileName failed to parse because: $message")