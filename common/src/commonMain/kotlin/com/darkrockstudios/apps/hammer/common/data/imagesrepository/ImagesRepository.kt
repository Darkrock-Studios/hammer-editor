package com.darkrockstudios.apps.hammer.common.data.imagesrepository

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.base.image.ImageProblem
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okio.IOException
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext

class ImagesRepository(
	projectDef: ProjectDef,
	private val idAllocator: IdAllocator,
	private val syncJournal: SyncJournal,
	private val imagesDatasource: ImagesDatasource,
) : ScopeCallback, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val imagesScope = CoroutineScope(dispatcherDefault)

	private var images: List<ImageDef> = emptyList()

	private val _imagesFlow = MutableSharedFlow<List<ImageDef>>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		replay = 1
	)
	val imagesFlow: SharedFlow<List<ImageDef>> = _imagesFlow

	private val _imageChangedFlow = MutableSharedFlow<Int>(
		extraBufferCapacity = 16,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)

	/** Emits the id of an image whose bytes, name or id changed, or that was deleted. */
	val imageChangedFlow: SharedFlow<Int> = _imageChangedFlow

	private data class CachedHash(val def: ImageDef, val stamp: ImageFileStamp, val hash: String)

	private val hashLock = reentrantLock()
	private val hashCache = mutableMapOf<Int, CachedHash>()

	init {
		projectScope.scope.registerCallback(this)
		imagesScope.launch { loadImages() }
	}

	suspend fun loadImages(): List<ImageDef> {
		val loaded = imagesDatasource.loadImages()
		images = loaded
		_imagesFlow.emit(loaded)
		return loaded
	}

	/** `imagesFlow` should be preferred; this is the last loaded list. */
	fun getImages(): List<ImageDef> = images

	/** The loaded images, loading them first when that has not happened yet. */
	suspend fun ensureImagesLoaded(): List<ImageDef> =
		_imagesFlow.replayCache.firstOrNull() ?: loadImages()

	/** Reads from disk, so it is current even before the first load completes. */
	fun findImage(id: Int): ImageDef? = imagesDatasource.findImage(id)

	fun getImagePath(def: ImageDef): HPath = imagesDatasource.getImagePath(def)

	fun loadImageBytes(def: ImageDef): ByteArray? = imagesDatasource.loadImageBytes(def)

	/**
	 * The [EntityHasher.hashImage] digest of the image as stored on disk, or null when there is no
	 * image with [id]. Cached per id until the file's size or modification time changes.
	 */
	fun contentHash(id: Int): String? {
		val def = imagesDatasource.findImage(id) ?: return null
		return contentHash(def)
	}

	/** [contentHash] for an image already looked up, saving a directory listing. */
	fun contentHash(def: ImageDef): String? {
		val id = def.id
		val stamp = imagesDatasource.getFileStamp(def)
		if (stamp != null) {
			hashLock.withLock {
				val cached = hashCache[id]
				if (cached != null && cached.def == def && cached.stamp == stamp) return cached.hash
			}
		}

		val bytes = imagesDatasource.loadImageBytes(def) ?: return null
		val hash = EntityHasher.hashImage(
			id = def.id,
			name = def.name,
			fileExtension = def.fileExtension,
			bytes = bytes,
		)
		if (stamp != null) {
			hashLock.withLock { hashCache[id] = CachedHash(def, stamp, hash) }
		}
		return hash
	}

	private fun invalidateHash(id: Int) {
		hashLock.withLock { hashCache.remove(id) }
	}

	suspend fun create(name: String, bytes: ByteArray, fileExtension: String): ImageAddOutcome {
		val cleanName = name.trim()
		val extension = fileExtension.lowercase()
		val validation = validate(cleanName, bytes, extension)
		if (validation != ImageAddResult.Ok) return ImageAddOutcome(validation)

		val def = ImageDef(id = idAllocator.claimNextId(), name = cleanName, fileExtension = extension)
		if (!write(def, bytes)) return ImageAddOutcome(ImageAddResult.ReadFailed)

		syncJournal.markEntityAsDirty(def.id)
		onChanged(def.id)
		return ImageAddOutcome(ImageAddResult.Ok, def)
	}

	/** Replaces the bytes of image [id]; the extension may change. */
	suspend fun replace(id: Int, bytes: ByteArray, fileExtension: String): ImageAddOutcome {
		val existing = imagesDatasource.findImage(id) ?: return ImageAddOutcome(ImageAddResult.ReadFailed)
		val extension = fileExtension.lowercase()
		val validation = validate(existing.name, bytes, extension)
		if (validation != ImageAddResult.Ok) return ImageAddOutcome(validation)

		val def = existing.copy(fileExtension = extension)
		if (!write(def, bytes)) return ImageAddOutcome(ImageAddResult.ReadFailed)

		syncJournal.markEntityAsDirty(id)
		onChanged(id)
		return ImageAddOutcome(ImageAddResult.Ok, def)
	}

	suspend fun rename(id: Int, newName: String): ImageAddOutcome {
		val cleanName = newName.trim()
		if (!ImageFormat.isValidName(cleanName)) return ImageAddOutcome(ImageAddResult.NameInvalid)
		val existing = imagesDatasource.findImage(id) ?: return ImageAddOutcome(ImageAddResult.ReadFailed)
		if (existing.name == cleanName) return ImageAddOutcome(ImageAddResult.Ok, existing)

		val renamed = try {
			imagesDatasource.renameImage(existing, cleanName)
		} catch (e: IOException) {
			Napier.e("Failed to rename image $id", e)
			return ImageAddOutcome(ImageAddResult.ReadFailed)
		}

		syncJournal.markEntityAsDirty(id)
		onChanged(id)
		return ImageAddOutcome(ImageAddResult.Ok, renamed)
	}

	suspend fun delete(id: Int): Boolean {
		val deleted = imagesDatasource.deleteImage(id)
		if (deleted) {
			syncJournal.recordIdDeletion(id)
			onChanged(id)
		}
		return deleted
	}

	/** Writes an image received from the server. Does not mark it dirty. */
	suspend fun storeSyncImage(def: ImageDef, bytes: ByteArray): Boolean {
		if (!write(def, bytes)) return false
		onChanged(def.id)
		return true
	}

	/** Removes an image the server deleted. Does not touch the sync journal. */
	suspend fun deleteSyncImage(id: Int) {
		if (imagesDatasource.deleteImage(id)) {
			onChanged(id)
		}
	}

	suspend fun reIdImage(oldId: Int, newId: Int) {
		imagesDatasource.reIdImage(oldId, newId)
			?: error("Failed to find image for id $oldId")
		invalidateHash(newId)
		onChanged(oldId)
		_imageChangedFlow.emit(newId)
	}

	fun isValidName(name: String): Boolean = ImageFormat.isValidName(name)

	private fun validate(name: String, bytes: ByteArray, fileExtension: String): ImageAddResult =
		when (ImageFormat.validate(name, fileExtension, bytes)) {
			null -> ImageAddResult.Ok
			ImageProblem.InvalidName -> ImageAddResult.NameInvalid
			ImageProblem.UnsupportedType, ImageProblem.ContentMismatch -> ImageAddResult.UnsupportedType
			ImageProblem.TooLarge -> ImageAddResult.TooLarge
		}

	private fun write(def: ImageDef, bytes: ByteArray): Boolean {
		invalidateHash(def.id)
		return try {
			imagesDatasource.writeImage(def, bytes)
			true
		} catch (e: IOException) {
			Napier.e("Failed to write image ${def.id}", e)
			false
		}
	}

	private suspend fun onChanged(id: Int) {
		invalidateHash(id)
		loadImages()
		_imageChangedFlow.emit(id)
	}

	override fun onScopeClose(scope: Scope) {
		imagesScope.cancel("Closing ImagesRepository")
	}
}
