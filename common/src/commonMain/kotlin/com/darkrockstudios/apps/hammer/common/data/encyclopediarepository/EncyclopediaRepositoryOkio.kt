package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.Path

class EncyclopediaRepositoryOkio(
	projectDef: ProjectDef,
	idRepository: IdRepository,
	private val toml: Toml,
	private val fileSystem: FileSystem,
	private val externalFileIo: ExternalFileIo
) : EncyclopediaRepository(projectDef, idRepository) {

	override fun getTypeDirectory(type: EntryType): HPath {
		return getTypeDirectory(projectDef, type, fileSystem)
	}

	override fun getEncyclopediaDirectory(): HPath {
		return getEncyclopediaDirectory(projectDef, fileSystem)
	}

	override fun getEntryPath(entryContent: EntryContent): HPath {
		val dir = getTypeDirectory(entryContent.type).toOkioPath()
		val filename = getEntryFilename(entryContent)
		val path = dir / filename
		return path.toHPath()
	}

	override fun getEntryPath(entryDef: EntryDef): HPath {
		val dir = getTypeDirectory(entryDef.type).toOkioPath()
		val filename = getEntryFilename(entryDef)
		val path = dir / filename
		return path.toHPath()
	}

	override fun getEntryImagePath(entryDef: EntryDef, fileExension: String): HPath {
		val dir = getTypeDirectory(entryDef.type).toOkioPath()
		val filename = getEntryImageFilename(entryDef, fileExension)
		val path = dir / filename
		return path.toHPath()
	}

	override fun hasEntryImage(entryDef: EntryDef, fileExension: String): Boolean {
		val path = getEntryImagePath(entryDef, fileExension).toOkioPath()
		return fileSystem.exists(path)
	}

	override fun loadEntries() {
		val dir = getEncyclopediaDirectory().toOkioPath()
		val entryPaths = fileSystem.listRecursively(dir).filterEntryPathsOkio().toList()
		val entryDefs = entryPaths.map { path -> getEntryDef(path.toHPath()) }

		scope.launch {
			updateEntries(entryDefs)
		}
	}

	override fun getEntryDef(entryPath: HPath): EntryDef {
		return getEntryDefFromFilename(entryPath.name, projectDef)
	}

	override fun loadEntry(entryDef: EntryDef): EntryContainer {
		val path = getEntryPath(entryDef)
		return loadEntry(path)
	}

	override fun loadEntry(entryPath: HPath): EntryContainer {
		val path = entryPath.toOkioPath()
		val contentToml: String = fileSystem.read(path) {
			readUtf8()
		}

		val entry: EntryContainer = toml.decodeFromString(contentToml)
		return entry
	}

	override fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: List<String>,
		imagePath: String?
	): EntryResult {
		val result = validateEntry(name, type, text, tags)
		if (result != EntryError.NONE) return EntryResult(result)

		val cleanedTags = tags.map { it.trim() }

		val newId = idRepository.claimNextId()
		val entry = EntryContent(
			id = newId,
			name = name.trim(),
			type = type,
			text = text.trim(),
			tags = cleanedTags
		)
		val container = EntryContainer(entry)
		val entryToml = toml.encodeToString(container)

		val path = getEntryPath(entry).toOkioPath()

		fileSystem.write(path) {
			writeUtf8(entryToml)
		}

		if (imagePath != null) {
			setEntryImage(entry.toDef(projectDef), imagePath)
		}

		return EntryResult(container, EntryError.NONE)
	}

	override fun setEntryImage(entryDef: EntryDef, imagePath: String?) {
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

	companion object {
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

fun Sequence<Path>.filterEntryPathsOkio() =
	map { it.toHPath() }
		.filterEntryPaths()
		.map { it.toOkioPath() }
		.filter { path -> !path.segments.any { part -> part.startsWith(".") } }