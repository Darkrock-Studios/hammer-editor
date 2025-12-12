package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource.Companion.ENTRY_NAME_PATTERN
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import korlibs.crypto.encoding.Base64
import korlibs.io.async.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext

class EncyclopediaRepository(
	private val projectDef: ProjectDef,
	private val idRepository: IdRepository,
	private val datasource: EncyclopediaDatasource,
	private val syncDataRepository: SyncDataRepository,
) : ScopeCallback, ProjectScoped, KoinComponent {

	override val projectScope = ProjectDefScope(projectDef)

	init {
		projectScope.scope.registerCallback(this)
	}

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val scope = CoroutineScope(dispatcherDefault)

	private val _entryListFlow = MutableSharedFlow<List<EntryDef>>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)
	val entryListFlow: SharedFlow<List<EntryDef>> = _entryListFlow

	private suspend fun updateEntries(entries: List<EntryDef>) {
		_entryListFlow.emit(entries)
	}

	fun loadEntries() {
		scope.launch {
			loadEntriesImperative()
		}
	}

	suspend fun removeEntryImage(entryDef: EntryDef): Boolean {
		val result = datasource.removeEntryImage(entryDef)
		if (result) {
			markForSynchronization(entryDef)
		}
		return result
	}

	suspend fun updateEntry(
		oldEntryDef: EntryDef,
		name: String,
		text: String,
		tags: Set<String>,
	): EntryResult {
		val result = validateEntry(name, oldEntryDef.type, text, tags)
		if (result != EntryError.NONE) return EntryResult(result)

		markForSynchronization(oldEntryDef)

		val cleanedTags = cleanTags(tags)
		val container = datasource.updateEntry(
			oldEntryDef = oldEntryDef,
			name = name,
			text = text,
			tags = cleanedTags,
		)

		return EntryResult(container, EntryError.NONE)
	}

	suspend fun loadEntriesImperative() {
		val entryDefs = datasource.loadEntriesImperative()

		updateEntries(entryDefs)
	}

	fun loadEntry(entryDef: EntryDef): EntryContainer {
		val path = datasource.getEntryPath(entryDef)
		return datasource.loadEntry(path)
	}

	fun loadEntry(id: Int): EntryContainer {
		val path = datasource.getEntryPath(id)
		return datasource.loadEntry(path)
	}

	fun validateEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: Set<String>
	): EntryError {
		return when {
			name.trim().isEmpty() -> EntryError.NAME_TOO_SHORT
			name.trim().length > MAX_NAME_SIZE -> EntryError.NAME_TOO_LONG
			!ENTRY_NAME_PATTERN.matches(name.trim()) -> EntryError.NAME_INVALID_CHARACTERS
			tags.any { it.length > MAX_TAG_SIZE } -> EntryError.TAG_TOO_LONG
			else -> EntryError.NONE
		}
	}

	private suspend fun markForSynchronization(entryDef: EntryDef) {
		if (syncDataRepository.isServerSynchronized() && !syncDataRepository.isEntityDirty(
				entryDef.id
			)
		) {
			val DEFAULT_EXTENSION = "jpg"
			val entry = datasource.loadEntry(entryDef).entry
			val image = if (datasource.hasEntryImage(entryDef, DEFAULT_EXTENSION)) {
				val imageBytes = datasource.loadEntryImage(entryDef, DEFAULT_EXTENSION)
				val imageBase64 = Base64.encode(imageBytes, url = true)

				ApiProjectEntity.EncyclopediaEntryEntity.Image(
					base64 = imageBase64,
					fileExtension = DEFAULT_EXTENSION,
				)
			} else {
				null
			}
			val hash = EntityHasher.hashEncyclopediaEntry(
				id = entryDef.id,
				name = entryDef.name,
				entryType = entryDef.type.text,
				text = entry.text,
				tags = entry.tags,
				image = image
			)
			syncDataRepository.markEntityAsDirty(entryDef.id, hash)
		}
	}

	suspend fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: Set<String>,
		imagePath: String?,
		forceId: Int? = null
	): EntryResult {
		val result = validateEntry(name, type, text, tags)
		if (result != EntryError.NONE) return EntryResult(result)

		val cleanedTags = cleanTags(tags)

		val newId = forceId ?: idRepository.claimNextId()
		val entry = EntryContent(
			id = newId,
			name = name.trim(),
			type = type,
			text = text.trim(),
			tags = cleanedTags
		)
		val container = EntryContainer(entry)

		val newDef = datasource.createEntry(container)

		if (imagePath != null) {
			datasource.setEntryImage(container.toDef(projectDef), imagePath)
		}

		if (forceId == null) markForSynchronization(newDef)

		return EntryResult(container, EntryError.NONE)
	}

	suspend fun deleteEntry(entryDef: EntryDef): Boolean {
		datasource.deleteEntry(entryDef)
		syncDataRepository.recordIdDeletion(entryDef.id)
		return true
	}

	suspend fun setEntryImage(entryDef: EntryDef, imagePath: String?) {
		markForSynchronization(entryDef)
		datasource.setEntryImage(entryDef, imagePath)
	}

	suspend fun reIdEntry(oldId: Int, newId: Int) = datasource.reIdEntry(oldId, newId)

	fun hasEntryImage(entryDef: EntryDef, fileExension: String): Boolean =
		datasource.hasEntryImage(entryDef, fileExension)

	suspend fun calculateEntryImageHash(entryDef: EntryDef, fileExension: String): String? {
		return datasource.hashEntryImage(entryDef, fileExension)
	}

	fun getEntryImagePath(entryDef: EntryDef, fileExtension: String): HPath =
		datasource.getEntryImagePath(entryDef, fileExtension)

	fun loadEntryImage(entryDef: EntryDef, fileExtension: String): ByteArray =
		datasource.loadEntryImage(entryDef, fileExtension)

	fun getEntryDef(id: Int): EntryDef = datasource.getEntryDef(id)
	fun findEntryDef(id: Int): EntryDef? = datasource.findEntryDef(id)

	private fun cleanTags(tags: Set<String>): Set<String> {
		val regex = Regex("""[\w-]+""")
		return tags
			.asSequence()
			.map { it.trim() }
			.map {
				if (it.startsWith("#")) {
					it.substring(1)
				} else {
					it
				}
			}
			.filter { it.isNotEmpty() }
			.filter { regex.matches(it) }
			.toSet()
	}

	override fun onScopeClose(scope: Scope) {
		this.scope.cancel("Closing EncyclopediaRepository")
	}

	companion object {
		const val MAX_NAME_SIZE = 64
		const val MAX_TAG_SIZE = 64
	}
}
