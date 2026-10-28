package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.InvalidEntryFilename
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.filterEntryPaths
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.id.datasources.EncyclopediaIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.IdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.ImageIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.NotesIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneDraftIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.SceneIdDatasource
import com.darkrockstudios.apps.hammer.common.data.id.datasources.TimeLineEventIdDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.readProjectSyncData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.writeProjectSyncData
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path

/**
 * Promotes each encyclopedia entry image (`type~id~image.ext`, or the pre-v3 `type-id-image.ext`)
 * to an Image entity in `images/`, named after its entry, and lists it in the entry's `images`.
 * An image whose entry is gone becomes an unattached Image named after its file; one whose entry
 * cannot be read stays where it is. Images over the size limit are kept.
 *
 * When the project has a sync journal, the new images are recorded as new ids and the entries
 * that gained one as dirty against their synced baseline, so the next sync uploads both.
 */
class Migration4_5(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	private val json: Json,
) : Migration {
	override val toVersion: Int = 5

	// A throw here would fail every launch, so a failing step is logged and the rest carries on.
	@Suppress("TooGenericExceptionCaught")
	override fun migrate(projectDef: ProjectDef) {
		Napier.i("Begin Migration4_5 for '${projectDef.name}'...")
		try {
			val result = promoteEntryImages(projectDef)
			if (result != null) {
				recordInSyncJournal(projectDef, result)
				Napier.i(
					"Migration4_5 for '${projectDef.name}' complete, promoted ${result.imageIds.size} image(s), " +
						"updated ${result.entryIds.size} entry(ies), skipped ${result.skipped}."
				)
			} else {
				Napier.i("Migration4_5 for '${projectDef.name}' complete, no entry images.")
			}
		} catch (e: Exception) {
			Napier.e("Migration4_5 failed for '${projectDef.name}'", e)
		}
	}

	private class Promotion(val imageIds: List<Int>, val entryIds: Set<Int>, val skipped: Int)

	private class EntryImageFile(val path: Path, val typeDir: Path, val entryId: Int, val extension: String)

	@Suppress("TooGenericExceptionCaught")
	private fun promoteEntryImages(projectDef: ProjectDef): Promotion? {
		val encyclopediaDir = projectDef.path.toOkioPath() / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY
		if (!fileSystem.exists(encyclopediaDir)) return null

		val images = findEntryImages(encyclopediaDir)
		if (images.isEmpty()) return null

		var nextId = firstFreeId(projectDef)
		val imagesDir = projectDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY
		val imageIds = mutableListOf<Int>()
		val entryIds = mutableSetOf<Int>()
		var skipped = 0

		for (image in images) {
			val entry = try {
				findEntry(projectDef, image)
			} catch (e: Exception) {
				Napier.w("Migration4_5: cannot read the entry for ${image.path}, leaving the image in place", e)
				skipped++
				continue
			}

			val def = ImageDef(
				id = nextId,
				name = ImagesDatasource.validImageName(entry?.second?.entry?.name ?: image.path.name.substringBeforeLast('.')),
				fileExtension = image.extension,
			)
			val target = imagesDir / ImagesDatasource.getFilename(def)
			try {
				fileSystem.createDirectories(imagesDir)
				if (fileSystem.exists(target)) {
					Napier.w("Migration4_5: $target already exists, leaving ${image.path} in place")
					skipped++
					continue
				}
				fileSystem.atomicMove(image.path, target)
			} catch (e: Exception) {
				Napier.w("Migration4_5: failed to move ${image.path}", e)
				skipped++
				continue
			}
			nextId++
			imageIds += def.id

			if (entry != null) {
				val (entryPath, container) = entry
				try {
					val updated = container.entry.copy(images = container.entry.images + def.id)
					fileSystem.write(entryPath) { writeUtf8(toml.encodeToString(EntryContainer(updated))) }
					entryIds += updated.id
				} catch (e: Exception) {
					Napier.w("Migration4_5: failed to list image ${def.id} in $entryPath; it stays unattached", e)
				}
			}
		}

		return Promotion(imageIds, entryIds, skipped)
	}

	@Suppress("TooGenericExceptionCaught")
	private fun findEntryImages(encyclopediaDir: Path): List<EntryImageFile> =
		EntryType.entries.flatMap { type ->
			val typeDir = encyclopediaDir / type.text
			val files = try {
				fileSystem.listOrNull(typeDir).orEmpty()
			} catch (e: Exception) {
				Napier.w("Migration4_5: cannot list $typeDir", e)
				emptyList()
			}
			files.mapNotNull { path -> parseEntryImage(path, typeDir, type) }
		}

	private fun parseEntryImage(path: Path, typeDir: Path, type: EntryType): EntryImageFile? {
		val match = EncyclopediaDatasource.ENTRY_IMAGE_FILENAME_PATTERN.matchEntire(path.name)
			?: EncyclopediaDatasource.LEGACY_ENTRY_IMAGE_FILENAME_PATTERN.matchEntire(path.name)
			?: return null
		if (match.groupValues[1] != type.text) return null
		val entryId = match.groupValues[2].toIntOrNull() ?: return null
		val extension = match.groupValues[3].lowercase()
		if (!ImageFormat.isAllowedExtension(extension)) return null
		return EntryImageFile(path, typeDir, entryId, extension)
	}

	/** The entry the image belongs to, null when it has none. Throws when the entry cannot be read. */
	private fun findEntry(projectDef: ProjectDef, image: EntryImageFile): Pair<Path, EntryContainer>? {
		val entryPath = fileSystem.list(image.typeDir)
			.asSequence()
			.map { it.toHPath() }
			.filterEntryPaths()
			.firstOrNull { path -> entryIdOf(projectDef, path.name) == image.entryId }
			?.toOkioPath()
			?: return null

		val container = toml.decodeFromString<EntryContainer>(fileSystem.read(entryPath) { readUtf8() })
		return entryPath to container
	}

	private fun entryIdOf(projectDef: ProjectDef, fileName: String): Int? = try {
		EncyclopediaDatasource.getEntryDefFromFilename(fileName, projectDef).id
	} catch (_: InvalidEntryFilename) {
		null
	}

	/** Above every id on disk and every id the sync journal knows, so no promoted image collides. */
	private fun firstFreeId(projectDef: ProjectDef): Int {
		val sources: Set<IdDatasource> = setOf(
			SceneIdDatasource(fileSystem),
			NotesIdDatasource(fileSystem),
			EncyclopediaIdDatasource(fileSystem),
			TimeLineEventIdDatasource(fileSystem, toml),
			SceneDraftIdDatasource(fileSystem),
			ImageIdDatasource(fileSystem),
		).mapTo(mutableSetOf()) { GuardedIdSource(it) }

		val onDisk = IdAllocator.findEntityIds(projectDef, sources)
		if (!onDisk.complete) {
			Napier.w("Migration4_5: some ids for '${projectDef.name}' could not be read")
		}

		val sync = readProjectSyncData(projectDef, fileSystem, json)
		val highest = listOfNotNull(
			onDisk.ids.maxOrNull(),
			sync?.deletedIds?.maxOrNull(),
			sync?.newIds?.maxOrNull(),
			sync?.lastId,
		).maxOrNull() ?: 0
		return maxOf(highest, 0) + 1
	}

	private fun recordInSyncJournal(projectDef: ProjectDef, promotion: Promotion) {
		if (promotion.imageIds.isEmpty()) return
		val sync = readProjectSyncData(projectDef, fileSystem, json) ?: return

		val dirtyIds = sync.dirty.mapTo(mutableSetOf()) { it.id }
		val newlyDirty = promotion.entryIds
			.filter { it !in dirtyIds }
			.map { EntityOriginalState(it, sync.syncedHashes[it]) }
		val updated = sync.copy(
			newIds = sync.newIds + promotion.imageIds.filter { it !in sync.newIds },
			dirty = sync.dirty + newlyDirty,
			cachedProjectHash = null,
		)
		writeProjectSyncData(projectDef, fileSystem, json, updated)
	}

	/** An id source that cannot be read counts as incomplete instead of failing the migration. */
	private class GuardedIdSource(private val source: IdDatasource) : IdDatasource {
		@Suppress("TooGenericExceptionCaught")
		override fun findIds(projectDef: ProjectDef): Set<Int>? = try {
			source.findIds(projectDef)
		} catch (e: Exception) {
			Napier.w("Migration4_5: failed to read ids from ${source::class.simpleName}", e)
			null
		}
	}
}
