package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.InvalidSceneFilename
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.*

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

	fun getEntryPath(entryContent: EntryContent): HPath =
		resolveEntryPath(entryContent.id, entryContent.type, entryContent.name).toHPath()

	fun getEntryPath(entryDef: EntryDef): HPath =
		resolveEntryPath(entryDef.id, entryDef.type, entryDef.name).toHPath()

	/**
	 * Where this entry lives on disk: the current `~`-delimited filename, or the pre-v3
	 * `-`-delimited one when that is what is actually there. Falls back to the current format so
	 * callers writing a new file always produce it.
	 */
	private fun resolveEntryPath(id: Int, type: EntryType, name: String): Path {
		val dir = getTypeDirectory(type).toOkioPath()
		val current = dir / getEntryFilename(id, type, name)
		if (fileSystem.exists(current)) return current

		val legacy = dir / getLegacyEntryFilename(id, type, name)
		return if (fileSystem.exists(legacy)) legacy else current
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
		val existing = resolveEntryImagePath(entryDef, fileExtension)
		val path = existing ?: dir / getEntryImageFilename(entryDef, fileExtension)
		return path.toHPath()
	}

	fun hasEntryImage(entryDef: EntryDef, fileExtension: String): Boolean =
		resolveEntryImagePath(entryDef, fileExtension) != null

	fun hasEntryImage(entryDef: EntryDef): Boolean = findEntryImagePath(entryDef) != null

	/**
	 * Locates an entry's stored image of a specific extension, preferring the current filename
	 * format and falling back to the pre-v3 `-`-delimited one.
	 */
	private fun resolveEntryImagePath(entryDef: EntryDef, fileExtension: String): Path? {
		val dir = getTypeDirectory(entryDef.type).toOkioPath()
		return entryImagePrefixes(entryDef)
			.map { prefix -> dir / "$prefix$fileExtension" }
			.firstOrNull { fileSystem.exists(it) }
	}

	/** Locates an entry's stored image regardless of its file extension. */
	fun findEntryImagePath(entryDef: EntryDef): HPath? {
		val dir = getTypeDirectory(entryDef.type).toOkioPath()
		val prefixes = entryImagePrefixes(entryDef)
		return fileSystem.list(dir)
			.firstOrNull { path ->
				prefixes.any { path.name.startsWith(it) } &&
					path.name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS
			}
			?.toHPath()
	}

	fun findEntryImageExtension(entryDef: EntryDef): String? =
		findEntryImagePath(entryDef)?.name?.substringAfterLast('.')

	suspend fun removeEntryImage(entryDef: EntryDef): Boolean {
		val imagePath = findEntryImagePath(entryDef)?.toOkioPath() ?: return false

		return try {
			fileSystem.delete(imagePath)
			true
		} catch (e: IOException) {
			Napier.w("Message: " + e.message)
			Napier.w("Failed to delete Entry Image: $imagePath", e)
			false
		}
	}

	suspend fun hashEntryImage(entryDef: EntryDef, fileExtension: String): String? {
		val path = resolveEntryImagePath(entryDef, fileExtension) ?: return null
		return calculateFileMd5(fileSystem, path)
	}

	private fun calculateFileMd5(fileSystem: FileSystem, path: Path): String {
		HashingSink.md5(blackholeSink()).use { hashingSink ->
			fileSystem.source(path).buffer().use { source ->
				source.readAll(hashingSink)
			}
			return hashingSink.hash.hex()
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
		} catch (e: IOException) {
			throw EntryLoadError(entryPath, e)
		} catch (e: SerializationException) {
			throw EntryLoadError(entryPath, e)
		} catch (e: IllegalArgumentException) {
			// tomlkt coercion failures (e.g. NumberFormatException on a non-integer id) are
			// IllegalArgumentException, not SerializationException.
			throw EntryLoadError(entryPath, e)
		} catch (e: IllegalStateException) {
			// tomlkt parser errors surface as IllegalStateException.
			throw EntryLoadError(entryPath, e)
		}
	}

	suspend fun reIdEntry(oldId: Int, newId: Int) {
		val oldDef = getEntryDef(oldId)
		val newDef = oldDef.copy(id = newId)
		val oldImagePath = findEntryImagePath(oldDef)
		if (oldImagePath != null) {
			val extension = oldImagePath.name.substringAfterLast('.')
			val newImagePath = getEntryImagePath(newDef, extension).toOkioPath()
			fileSystem.atomicMove(oldImagePath.toOkioPath(), newImagePath)
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
		val imagePath = getEntryImagePath(entryDef, fileExtension).toOkioPath()
		fileSystem.read(imagePath) {
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
		if (imagePath == null) {
			removeEntryImage(entryDef)
			return
		}

		val extension = imageExtensionOf(imagePath)
		if (extension == null) {
			Napier.w("Ignoring entry image with unsupported extension: $imagePath")
			return
		}

		val pixelData = externalFileIo.readExternalFile(imagePath)
		if (pixelData.size > MAX_IMAGE_SIZE_BYTES) {
			Napier.w("Ignoring entry image over ${MAX_IMAGE_SIZE_MB}MB: $imagePath")
			return
		}

		removeEntryImage(entryDef)
		val targetPath = getEntryImagePath(entryDef, extension).toOkioPath()
		fileSystem.write(targetPath) {
			write(pixelData)
		}
	}

	private fun imageExtensionOf(sourcePath: String): String? {
		val extension = sourcePath.substringAfterLast('.', "").lowercase()
		return if (extension in IMAGE_EXTENSIONS) extension else null
	}

	/** Writes raw image bytes (e.g. an image pulled from the server during sync) to the entry's image path. */
	suspend fun writeEntryImage(entryDef: EntryDef, imageBytes: ByteArray, fileExtension: String) {
		val targetPath = getEntryImagePath(entryDef, fileExtension).toOkioPath()

		val typeDir = getTypeDirectory(entryDef.type).toOkioPath()
		if (!targetPath.isWithin(typeDir)) {
			error("Refusing to write entry image outside its type directory: $targetPath")
		}

		fileSystem.write(targetPath) {
			write(imageBytes)
		}
	}

	suspend fun deleteEntry(entryDef: EntryDef): Boolean {
		val path = findEntryPath(entryDef.id)?.toOkioPath() ?: getEntryPath(entryDef).toOkioPath()
		fileSystem.delete(path)

		findEntryImagePath(entryDef)?.let { imagePath ->
			fileSystem.delete(imagePath.toOkioPath())
		}

		return true
	}

	suspend fun updateEntry(
		oldEntryDef: EntryDef,
		name: String,
		text: String,
		tags: Set<String>,
		aliases: List<String>,
		excludeFromDictionary: Boolean,
	): EntryContainer {
		val oldPath = getEntryPath(oldEntryDef.id).toOkioPath()
		fileSystem.delete(oldPath)

		val entry = EntryContent(
			id = oldEntryDef.id,
			name = name.trim(),
			type = oldEntryDef.type,
			text = text.trim(),
			tags = tags,
			aliases = aliases,
			excludeFromDictionary = excludeFromDictionary,
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
		// Entry format: type~id~name.toml (delimiter changed from `-` to `~` in data v3).
		// The name group accepts any char except path separators and the delimiter; encoded
		// lookalikes pass through naturally.
		val ENTRY_FILENAME_PATTERN = Regex("""([a-zA-Z]+)~(\d+)~([^~/\\]+)\.toml""")

		// Pre-v3 patterns. Kept for read compatibility (e.g. fixtures, projects mid-migration).
		// Old name set was the restricted `[\d\p{L}+ _']` so `-` is unambiguously a delimiter.
		val LEGACY_ENTRY_FILENAME_PATTERN = Regex("""([a-zA-Z]+)-(\d+)-([\d\p{L}+ _']+)\.toml""")
		val LEGACY_ENTRY_IMAGE_FILENAME_PATTERN = Regex("""([a-zA-Z]+)-(\d+)-image\.([a-zA-Z0-9]+)""")

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
			return "${type.text}~$id~${ProjectsRepository.encodeForFilename(name)}.toml"
		}

		private fun getEntryImageFilename(id: Int, type: EntryType, fileExtension: String): String {
			return "${type.text}~$id~image.$fileExtension"
		}

		private fun getLegacyEntryFilename(id: Int, type: EntryType, name: String): String {
			return "${type.text}-$id-$name.toml"
		}

		/** Image filename prefixes an entry's image may carry on disk, newest format first. */
		private fun entryImagePrefixes(entryDef: EntryDef): List<String> = listOf(
			"${entryDef.type.text}~${entryDef.id}~image.",
			"${entryDef.type.text}-${entryDef.id}-image.",
		)

		private fun matchEntryFilename(fileName: String): MatchResult? =
			ENTRY_FILENAME_PATTERN.matchEntire(fileName)
				?: LEGACY_ENTRY_FILENAME_PATTERN.matchEntire(fileName)

		fun isEntryFilename(fileName: String): Boolean = matchEntryFilename(fileName) != null

		fun getEntryIdFromFilename(fileName: String): Int {
			val captures = matchEntryFilename(fileName)
				?: throw IllegalStateException("Entry filename was bad: $fileName")
			try {
				val entryId = captures.groupValues[2].toInt()
				return entryId
			} catch (e: NumberFormatException) {
				throw InvalidSceneFilename("Number format exception", fileName, e)
			} catch (e: IllegalStateException) {
				throw InvalidSceneFilename("Invalid filename", fileName, e)
			}
		}

		fun getEntryDefFromFilename(fileName: String, projectDef: ProjectDef): EntryDef {
			val captures = matchEntryFilename(fileName)
				?: throw IllegalStateException("Entry filename was bad: $fileName")
			try {
				val typeString = captures.groupValues[1]
				val entryId = captures.groupValues[2].toInt()
				val entryName = ProjectsRepository.decodeFromFilename(captures.groupValues[3])

				val type = EntryType.fromString(typeString)

				val def = EntryDef(
					projectDef = projectDef,
					id = entryId,
					type = type,
					name = entryName
				)
				return def
			} catch (e: NumberFormatException) {
				throw InvalidEntryFilename("Number format exception", fileName, e)
			} catch (e: IllegalStateException) {
				throw InvalidEntryFilename("Invalid filename", fileName, e)
			} catch (e: IllegalArgumentException) {
				throw InvalidEntryFilename(e.message ?: "Invalid filename argument", fileName, cause = e)
			}
		}

		// Raster formats Coil renders on every target (Android BitmapFactory + desktop/iOS Skia).
		// Doubles as the allowlist for server-supplied sync image extensions.
		val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

		const val MAX_IMAGE_SIZE_MB = 16
		const val MAX_IMAGE_SIZE_BYTES = MAX_IMAGE_SIZE_MB * 1024L * 1024

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
	!it.name.startsWith(".") && EncyclopediaDatasource.isEntryFilename(it.name)
}.sortedBy { it.name }

open class InvalidEntryFilename(message: String, fileName: String, cause: Throwable? = null) :
	IllegalStateException("$fileName failed to parse because: $message", cause)