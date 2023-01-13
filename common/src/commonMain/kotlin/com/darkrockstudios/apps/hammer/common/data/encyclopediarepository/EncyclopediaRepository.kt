package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.InvalidSceneFilename
import com.darkrockstudios.apps.hammer.common.defaultDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okio.Closeable

abstract class EncyclopediaRepository(
	protected val projectDef: ProjectDef,
	protected val idRepository: IdRepository
) : Closeable {
	protected val scope = CoroutineScope(defaultDispatcher)

	private val _entryListFlow = MutableSharedFlow<List<EntryDef>>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val entryListFlow: SharedFlow<List<EntryDef>> = _entryListFlow

	protected suspend fun updateEntries(entries: List<EntryDef>) {
		_entryListFlow.emit(entries)
	}

	abstract fun getTypeDirectory(type: EntryType): HPath
	abstract fun getEncyclopediaDirectory(): HPath
	abstract fun getEntryPath(entryContent: EntryContent): HPath
	abstract fun getEntryPath(entryDef: EntryDef): HPath

	abstract fun loadEntries()
	abstract fun getEntryDef(entryPath: HPath): EntryDef
	abstract fun loadEntry(entryPath: HPath): EntryContainer
	abstract fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: List<String>
	): EntryResult

	fun validateEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: List<String>
	): EntryError {
		return if (name.trim().length > MAX_NAME_SIZE) {
			EntryError.NAME_TOO_LONG
		} else {
			EntryError.NONE
		}
	}

	override fun close() {
		scope.cancel("Closing EncyclopediaRepository")
	}

	companion object {
		val ENTRY_FILENAME_PATTERN = Regex("""([a-zA-Z]+)-(\d+)-([\da-zA-Z]+)\.toml""")
		const val ENCYCLOPEDIA_DIRECTORY = "encyclopedia"
		const val MAX_NAME_SIZE = 64

		fun getEntryFilename(entryDef: EntryDef): String =
			getEntryFilename(
				id = entryDef.id,
				type = entryDef.type,
				name = entryDef.name
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
	}
}

fun Sequence<HPath>.filterEntryPaths() = filter {
	!it.name.startsWith(".") && EncyclopediaRepository.ENTRY_FILENAME_PATTERN.matches(it.name)
}.sortedBy { it.name }

open class InvalidEntryFilename(message: String, fileName: String) :
	IllegalStateException("$fileName failed to parse because: $message")