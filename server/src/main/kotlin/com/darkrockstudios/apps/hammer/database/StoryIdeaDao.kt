package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class StoryIdeaDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.storyIdeaQueries

	suspend fun getIdeaHashes(userId: Long): List<IdeaHashRow> =
		withContext(ioDispatcher) {
			queries.getIdeaHashes(userId)
				.executeAsList()
				.map { IdeaHashRow(id = IdeaId(it.uuid), hash = it.hash) }
		}

	suspend fun getIdea(userId: Long, ideaId: IdeaId): StoryIdeaRow? =
		withContext(ioDispatcher) {
			queries.getIdea(userId, ideaId.id)
				.executeAsOneOrNull()
				?.let { StoryIdeaRow(content = it.content, hash = it.hash, cipher = it.cipher) }
		}

	suspend fun getHash(userId: Long, ideaId: IdeaId): String? =
		withContext(ioDispatcher) {
			queries.getHash(userId, ideaId.id).executeAsOneOrNull()
		}

	suspend fun upsert(
		userId: Long,
		ideaId: IdeaId,
		content: String,
		hash: String,
		cipher: String?,
	) = withContext(ioDispatcher) {
		queries.upsert(
			userId = userId,
			uuid = ideaId.id,
			content = content,
			hash = hash,
			cipher = cipher,
		)
	}

	suspend fun deleteIdea(userId: Long, ideaId: IdeaId) = withContext(ioDispatcher) {
		queries.deleteIdea(userId, ideaId.id)
	}

	companion object {
		/**
		 * Size cap on the stored (encrypted) blob, in characters. Must match the
		 * `story_idea_content_max` CHECK constraint in StoryIdea.sq. The client separately
		 * enforces a 10k-character content limit; this is the server-side backstop on the
		 * whole opaque payload.
		 */
		const val MAX_IDEA_CONTENT_LENGTH = 65536
	}
}

data class IdeaHashRow(val id: IdeaId, val hash: String)
data class StoryIdeaRow(val content: String, val hash: String, val cipher: String?)
