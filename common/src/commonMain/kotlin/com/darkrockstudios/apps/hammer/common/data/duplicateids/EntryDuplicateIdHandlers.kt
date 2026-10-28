package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.base.http.EntityType as SyncEntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.InvalidEntryFilename
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.filterEntryPathsOkio
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path

/** Entry ids are global across types. */
internal class EntryDuplicateIdHandler(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	private val toml: Toml,
) : DuplicateIdHandler(projectDef, fileSystem) {
	override val kind = DuplicateIdKind.Entry

	override val entityType = SyncEntityType.EncyclopediaEntry

	override fun ids(): Set<Int> = files().mapTo(mutableSetOf()) { (_, def) -> def.id }

	private fun files(): List<Pair<Path, EntryDef>> =
		listRecursivelyOrEmpty(projectDir / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY)
			.filterEntryPathsOkio()
			.mapNotNull { path -> filenameDef(path.toHPath())?.let { path to it } }
			.toList()

	override fun findDuplicates(): List<DuplicateIdSet> =
		files().toDuplicateSets({ (_, def) -> def.id }) { (path, def) ->
			val bytes = readBytes(path)
			val content = toml.decodeOrNull<EntryContainer>(bytes)?.entry
			copyOf(
				path = path,
				name = content?.name ?: def.name,
				bytes = bytes,
				text = content?.text ?: bytes.utf8(),
			)
		}

	override fun canonicalPath(copy: DuplicateCopy, id: Int): HPath = canonicalPath(copy, read(copy), id)

	private fun canonicalPath(copy: DuplicateCopy, content: EntryContent?, id: Int): HPath {
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
		val content = read(copy)
		val rewritten = content?.takeIf { it.id != newId }?.let { toml.encodeToString(EntryContainer(it.copy(id = newId))) }
		rewriteOrMove(copy.path, canonicalPath(copy, content, newId), rewritten)
		return emptySet()
	}

	private fun filenameDef(path: HPath): EntryDef? =
		try {
			EncyclopediaDatasource.getEntryDefFromFilename(path.name, projectDef)
		} catch (_: InvalidEntryFilename) {
			null
		}

	private fun requireFilenameDef(copy: DuplicateCopy): EntryDef =
		filenameDef(copy.path) ?: error("Not an entry filename: ${copy.path}")

	private fun read(copy: DuplicateCopy): EntryContent? =
		toml.decodeOrNull<EntryContainer>(readBytes(copy.path.toOkioPath()))?.entry
}
