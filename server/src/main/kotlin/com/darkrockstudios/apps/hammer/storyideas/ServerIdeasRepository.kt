package com.darkrockstudios.apps.hammer.storyideas

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaHashItem
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.DeletedIdeaDao
import com.darkrockstudios.apps.hammer.database.StoryIdeaDao
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeasStateHasher
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.ktor.util.logging.Logger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import java.security.GeneralSecurityException

/**
 * Account-level story-idea sync, gated on the account (projects) sync session — the ideas phase
 * runs inside that session with the same syncId, so there is no separate session type here.
 *
 * Idea content is stored the same way entity content is: an opaque blob (the client's serialized
 * `StoryIdea` JSON, encrypted at rest) plus a client-supplied hash. The server decodes the payload
 * only to validate its shape; unknown fields survive verbatim.
 */
class ServerIdeasRepository(
	private val storyIdeaDao: StoryIdeaDao,
	private val deletedIdeaDao: DeletedIdeaDao,
	private val accountDao: AccountDao,
	private val encryptor: ContentEncryptor,
	private val encryptorRegistry: ContentEncryptorRegistry,
	private val json: Json,
	private val log: Logger,
	private val maxContentLength: Int = StoryIdeaDao.MAX_IDEA_CONTENT_LENGTH,
) : KoinComponent {

	private val syncSessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession> by inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECTS_SYNC_MANAGER)
	)

	data class IdeasSyncState(
		val ideas: List<IdeaHashItem>,
		val deletedIdeas: Set<IdeaId>,
	)

	sealed class IdeaSaveResult {
		data class Saved(val dto: RawSavedIdeaDto) : IdeaSaveResult()
		data class Conflict(val conflict: RawIdeaConflictDto) : IdeaSaveResult()
	}

	/**
	 * [IdeasStateHasher] hash of the account's live idea set, sent in the begin-sync response so
	 * a client in agreement can skip the ideas phase. Read-only — no sync session required.
	 */
	suspend fun getIdeasStateHash(userId: Long): String =
		IdeasStateHasher.hash(storyIdeaDao.getIdeaHashes(userId).map { IdeaHashItem(it.id, it.hash) })

	suspend fun getSyncState(userId: Long, syncId: String): SResult<IdeasSyncState> {
		if (!syncSessionManager.validateSyncId(userId, syncId))
			return SResult.failure(InvalidSyncIdException())

		return SResult.success(
			IdeasSyncState(
				ideas = storyIdeaDao.getIdeaHashes(userId).map { IdeaHashItem(it.id, it.hash) },
				deletedIdeas = deletedIdeaDao.getDeletedIdeas(userId),
			)
		)
	}

	suspend fun loadIdea(userId: Long, syncId: String, ideaId: IdeaId): SResult<RawSavedIdeaDto> {
		if (!syncSessionManager.validateSyncId(userId, syncId))
			return SResult.failure(InvalidSyncIdException())

		val row = storyIdeaDao.getIdea(userId, ideaId)
			?: return SResult.failure(IdeaNotFound(ideaId))

		val element = decryptAndParse(userId, ideaId, row.content, row.cipher)
			?: return SResult.failure(IdeaNotFound(ideaId))

		return SResult.success(RawSavedIdeaDto(idea = element, hash = row.hash))
	}

	suspend fun saveIdea(
		userId: Long,
		syncId: String,
		ideaId: IdeaId,
		idea: JsonElement,
		originalHash: String?,
		clientHash: String,
	): SResult<IdeaSaveResult> {
		if (!syncSessionManager.validateSyncId(userId, syncId))
			return SResult.failure(InvalidSyncIdException())

		// Deletion wins over stale copies: a tombstoned idea can never be re-uploaded.
		if (deletedIdeaDao.isIdeaDeleted(userId, ideaId))
			return SResult.failure(IdeaDeletedException(ideaId))

		// Validation only — the raw element is what gets stored, so fields this server version
		// doesn't know survive; ignoreUnknownKeys makes them pass the decode.
		val decoded = decodeAsStoryIdea(idea)
			?: return SResult.failure(IllegalArgumentException("Payload does not decode as StoryIdea"))
		if (decoded.id != ideaId)
			return SResult.failure(IllegalArgumentException("Payload id does not match the idea id"))

		val existingRow = storyIdeaDao.getIdea(userId, ideaId)
		if (existingRow != null && existingRow.hash != originalHash) {
			// An unparseable server row heals via overwrite instead of conflicting forever.
			val serverElement = decryptAndParse(userId, ideaId, existingRow.content, existingRow.cipher)
			if (serverElement != null) {
				return SResult.success(
					IdeaSaveResult.Conflict(
						RawIdeaConflictDto(server = serverElement, serverHash = existingRow.hash),
					)
				)
			}
		}

		val account = accountDao.getAccount(userId) ?: error("User not found $userId")
		val encrypted = encryptor.encrypt(idea.toString(), account.cipher_secret)
		if (encrypted.length > maxContentLength) {
			return SResult.failure(IdeaTooLargeException(encrypted.length, maxContentLength))
		}

		storyIdeaDao.upsert(
			userId = userId,
			ideaId = ideaId,
			content = encrypted,
			hash = clientHash,
			cipher = encryptor.cipherName(),
		)

		return SResult.success(IdeaSaveResult.Saved(RawSavedIdeaDto(idea = idea, hash = clientHash)))
	}

	suspend fun deleteIdea(userId: Long, syncId: String, ideaId: IdeaId): SResult<Unit> {
		if (!syncSessionManager.validateSyncId(userId, syncId))
			return SResult.failure(InvalidSyncIdException())

		storyIdeaDao.deleteIdea(userId, ideaId)
		// Tombstone even if the row never existed, so an out-of-order delete still propagates.
		deletedIdeaDao.recordIdeaDeleted(userId, ideaId)

		return SResult.success()
	}

	/**
	 * Decrypts and parses a stored row, treating any failure (wrong key, corrupt ciphertext,
	 * malformed JSON, undecodable shape) as a missing row so the next client upload heals it.
	 */
	private suspend fun decryptAndParse(
		userId: Long,
		ideaId: IdeaId,
		content: String,
		cipher: String?,
	): JsonElement? {
		val account = accountDao.getAccount(userId) ?: error("User not found $userId")
		val plain = try {
			encryptorRegistry.resolve(cipher).decrypt(content, account.cipher_secret)
		} catch (e: GeneralSecurityException) {
			log.warn("Undecryptable story_idea row for user=$userId idea=${ideaId.id}; treating as missing")
			return null
		} catch (e: IllegalArgumentException) {
			log.warn("Corrupt story_idea ciphertext for user=$userId idea=${ideaId.id}; treating as missing")
			return null
		}

		val element = try {
			json.parseToJsonElement(plain)
		} catch (e: SerializationException) {
			log.warn("Malformed story_idea row for user=$userId idea=${ideaId.id}; treating as missing")
			return null
		}

		if (decodeAsStoryIdea(element) == null) {
			log.warn("Undecodable story_idea row for user=$userId idea=${ideaId.id}; treating as missing")
			return null
		}
		return element
	}

	private fun decodeAsStoryIdea(data: JsonElement): StoryIdea? {
		return try {
			json.decodeFromJsonElement(StoryIdea.serializer(), data)
		} catch (e: SerializationException) {
			null
		} catch (e: IllegalArgumentException) {
			null
		}
	}
}
