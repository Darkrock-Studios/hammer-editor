package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.InvalidSceneFilename
import com.darkrockstudios.apps.hammer.common.fileio.HPath

abstract class EncyclopediaRepository(protected val projectDef: ProjectDef) {

	abstract fun getTypeDirectory(type: EntryType): HPath
	abstract fun getEncyclopediaDirectory(): HPath

	companion object {
		val ENTRY_FILENAME_PATTERN = Regex("""([a-zA-Z]+)-(\d+)-([a-zA-Z]+)\.toml""")
		const val ENCYCLOPEDIA_DIRECTORY = "encyclopedia"
		const val MAX_NAME_SIZE = 64

		fun getEntryFilenameFromId(id: Int, type: EntryType, name: String): String {
			return "${type.name}-$id-$name.toml"
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
	}
}