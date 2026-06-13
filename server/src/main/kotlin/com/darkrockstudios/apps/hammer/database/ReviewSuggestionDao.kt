package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.review.ReviewSuggestionStatus
import com.darkrockstudios.apps.hammer.review.ReviewSuggestionType
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

class ReviewSuggestionDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.reviewSuggestionQueries

	suspend fun createSuggestion(
		reviewSceneId: Long,
		type: ReviewSuggestionType,
		paragraph: Int,
		startOffset: Int,
		endOffset: Int,
		originalText: String,
		replacementText: String?,
		reason: String?,
		status: ReviewSuggestionStatus,
	): Long = withContext(ioDispatcher) {
		queries.createSuggestion(
			reviewSceneId = reviewSceneId,
			type = type.toStringId(),
			paragraph = paragraph,
			startOffset = startOffset,
			endOffset = endOffset,
			originalText = originalText,
			replacementText = replacementText,
			reason = reason,
			status = status.toStringId(),
		).executeAsOne()
	}

	suspend fun getSuggestionsForScene(reviewSceneId: Long): List<ReviewSuggestion> =
		withContext(ioDispatcher) {
			queries.getSuggestionsForScene(reviewSceneId).executeAsList()
		}

	suspend fun getSuggestionsForRequest(reviewRequestId: Long): List<ReviewSuggestion> =
		withContext(ioDispatcher) {
			queries.getSuggestionsForRequest(reviewRequestId).executeAsList()
		}

	suspend fun getSuggestion(id: Long): ReviewSuggestion? = withContext(ioDispatcher) {
		queries.getSuggestion(id).executeAsOneOrNull()
	}

	suspend fun deleteSuggestion(id: Long) = withContext(ioDispatcher) {
		queries.deleteSuggestion(id)
	}

	suspend fun updateSuggestionStatus(id: Long, status: ReviewSuggestionStatus, at: Instant) {
		withContext(ioDispatcher) {
			queries.updateSuggestionStatus(status.toStringId(), at, id)
		}
	}

	suspend fun updateSuggestionContent(id: Long, replacementText: String?, reason: String?, at: Instant) =
		withContext(ioDispatcher) {
			queries.updateSuggestionContent(replacementText, reason, at, id)
		}

	suspend fun countSuggestionsForRequest(reviewRequestId: Long): Long = withContext(ioDispatcher) {
		queries.countSuggestionsForRequest(reviewRequestId).executeAsOne()
	}
}
