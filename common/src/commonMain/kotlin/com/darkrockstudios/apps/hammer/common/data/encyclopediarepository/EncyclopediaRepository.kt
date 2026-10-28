package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.base.validate.ProjectNameValidationResult
import com.darkrockstudios.apps.hammer.base.validate.ProjectNameValidator
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.tagindex.cleanTags
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
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
	private val idAllocator: IdAllocator,
	private val datasource: EncyclopediaDatasource,
	private val syncJournal: SyncJournal,
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

	private val _entryContentChangedFlow = MutableSharedFlow<Unit>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val entryContentChangedFlow: SharedFlow<Unit> = _entryContentChangedFlow

	private val _entryChangedFlow = MutableSharedFlow<Int>(extraBufferCapacity = 64)

	/** Emits the id of each entry that was created, updated or deleted. */
	val entryChangedFlow: SharedFlow<Int> = _entryChangedFlow

	private suspend fun updateEntries(entries: List<EntryDef>) {
		_entryListFlow.emit(entries)
	}

	fun loadEntries() {
		scope.launch {
			loadEntriesImperative()
		}
	}

	suspend fun updateEntry(
		oldEntryDef: EntryDef,
		name: String,
		text: String,
		tags: Set<String>,
		aliases: List<String> = emptyList(),
		excludeFromDictionary: Boolean,
		images: List<Int>,
	): EntryResult {
		val result = validateEntry(name, oldEntryDef.type, text, tags, aliases)
		if (result != EntryError.NONE) return EntryResult(result)

		markForSynchronization(oldEntryDef)

		val cleanedTags = cleanTags(tags)
		val cleanedAliases = cleanAliases(aliases, name)
		val container = datasource.updateEntry(
			oldEntryDef = oldEntryDef,
			name = name,
			text = text,
			tags = cleanedTags,
			aliases = cleanedAliases,
			excludeFromDictionary = excludeFromDictionary,
			images = images,
		)

		_entryContentChangedFlow.emit(Unit)
		_entryChangedFlow.emit(oldEntryDef.id)
		return EntryResult(container, EntryError.NONE)
	}

	suspend fun loadEntriesImperative(): List<EntryDef> {
		val entryDefs = datasource.loadEntriesImperative()

		updateEntries(entryDefs)
		return entryDefs
	}

	suspend fun ensureEntriesLoaded(): List<EntryDef> {
		entryListFlow.replayCache.firstOrNull()?.let { return it }
		loadEntriesImperative()
		return entryListFlow.replayCache.firstOrNull().orEmpty()
	}

	/**
	 * The entries on disk now, without emitting them. [ensureEntriesLoaded] can be behind, since
	 * creating, renaming, or deleting an entry does not refresh [entryListFlow].
	 */
	suspend fun readEntryDefs(): List<EntryDef> = datasource.loadEntriesImperative()

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
		tags: Set<String>,
		aliases: List<String> = emptyList(),
	): EntryError {
		return when {
			name.trim().isEmpty() -> EntryError.NAME_TOO_SHORT
			name.trim().length > MAX_NAME_SIZE -> EntryError.NAME_TOO_LONG
			// The entry name is stored wrapped as `type~id~name`, so it never lands on disk as a
			// bare basename; only the character rules apply.
			ProjectNameValidator.validate(name.trim(), usedAsRawFilename = false) !=
				ProjectNameValidationResult.VALID -> EntryError.NAME_INVALID_CHARACTERS
			tags.any { it.length > MAX_TAG_SIZE } -> EntryError.TAG_TOO_LONG
			aliases.any { it.trim().length > MAX_NAME_SIZE } -> EntryError.ALIAS_TOO_LONG
			else -> EntryError.NONE
		}
	}

	private suspend fun markForSynchronization(entryDef: EntryDef) {
		if (syncJournal.isServerSynchronized() && !syncJournal.isEntityDirty(entryDef.id)) {
			syncJournal.markEntityAsDirty(entryDef.id)
		}
	}

	suspend fun createEntry(
		name: String,
		type: EntryType,
		text: String,
		tags: Set<String>,
		forceId: Int? = null,
		aliases: List<String> = emptyList(),
		excludeFromDictionary: Boolean = false,
		images: List<Int> = emptyList(),
	): EntryResult {
		val result = validateEntry(name, type, text, tags, aliases)
		if (result != EntryError.NONE) return EntryResult(result)

		val cleanedTags = cleanTags(tags)
		val cleanedAliases = cleanAliases(aliases, name)

		val newId = forceId ?: idAllocator.claimNextId()
		val entry = EntryContent(
			id = newId,
			name = name.trim(),
			type = type,
			text = text.trim(),
			tags = cleanedTags,
			aliases = cleanedAliases,
			excludeFromDictionary = excludeFromDictionary,
			images = images,
		)
		val container = EntryContainer(entry)

		val newDef = datasource.createEntry(container)

		if (forceId == null) markForSynchronization(newDef)

		_entryContentChangedFlow.emit(Unit)
		_entryChangedFlow.emit(newId)
		return EntryResult(container, EntryError.NONE)
	}

	suspend fun deleteEntry(entryDef: EntryDef): Boolean {
		datasource.deleteEntry(entryDef)
		syncJournal.recordIdDeletion(entryDef.id)
		_entryContentChangedFlow.emit(Unit)
		_entryChangedFlow.emit(entryDef.id)
		return true
	}

	suspend fun reIdEntry(oldId: Int, newId: Int) = datasource.reIdEntry(oldId, newId)

	/** Appends image [imageId] to the entry's images; false when the entry already has it. */
	suspend fun addEntryImage(entryDef: EntryDef, imageId: Int): Boolean =
		changeImages(entryDef) { images -> if (imageId in images) images else images + imageId }

	/** Detaches image [imageId] from the entry. The Image entity itself is left alone. */
	suspend fun removeEntryImage(entryDef: EntryDef, imageId: Int): Boolean =
		changeImages(entryDef) { images -> images.filterNot { it == imageId } }

	/** Moves image [imageId] to the front of the entry's images; false when the entry does not have it. */
	suspend fun setPrimaryImage(entryDef: EntryDef, imageId: Int): Boolean =
		changeImages(entryDef) { images ->
			if (imageId in images) listOf(imageId) + images.filterNot { it == imageId } else images
		}

	private suspend fun changeImages(entryDef: EntryDef, change: (List<Int>) -> List<Int>): Boolean =
		rewriteEntry(entryDef) { entry -> entry.copy(images = change(entry.images)) }

	/**
	 * Writes [transform] of the entry's stored content without validating it, so a legacy entry that
	 * would fail [validateEntry] can still be rewritten. The name must not change. False, and nothing
	 * written, when [transform] returns the content unchanged.
	 */
	suspend fun rewriteEntry(entryDef: EntryDef, transform: (EntryContent) -> EntryContent): Boolean {
		val current = loadEntry(entryDef).entry
		val updated = transform(current)
		if (updated == current) return false
		require(updated.id == current.id && updated.name == current.name && updated.type == current.type) {
			"rewriteEntry may not change an entry's id, name or type"
		}

		markForSynchronization(entryDef)
		datasource.writeEntry(entryDef, updated)

		_entryContentChangedFlow.emit(Unit)
		_entryChangedFlow.emit(entryDef.id)
		return true
	}

	fun getEntryDef(id: Int): EntryDef = datasource.getEntryDef(id)
	fun findEntryDef(id: Int): EntryDef? = datasource.findEntryDef(id)

	private fun cleanAliases(aliases: List<String>, entryName: String): List<String> {
		val trimmedName = entryName.trim()
		val seen = mutableSetOf<String>()
		return aliases.asSequence()
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.filter { it != trimmedName }
			.filter { seen.add(it) }
			.toList()
	}

	override fun onScopeClose(scope: Scope) {
		this.scope.cancel("Closing EncyclopediaRepository")
	}

	companion object {
		const val MAX_NAME_SIZE = 64
		const val MAX_TAG_SIZE = 64
	}
}
