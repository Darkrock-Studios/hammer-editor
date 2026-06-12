package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.review.ReviewStatus
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

class ReviewRequestDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.reviewRequestQueries

	suspend fun createRequest(
		userId: Long,
		projectId: Long,
		token: String,
		reviewerEmail: String,
		label: String,
		note: String?,
		status: ReviewStatus,
		expires: Instant?,
	): ReviewRequest = withContext(ioDispatcher) {
		queries.createRequest(
			userId = userId,
			projectId = projectId,
			token = token,
			reviewerEmail = reviewerEmail,
			label = label,
			note = note,
			status = status.toStringId(),
			expires = expires,
		)
		queries.getRequestByToken(token).executeAsOne()
	}

	suspend fun getRequestByToken(token: String): ReviewRequest? = withContext(ioDispatcher) {
		queries.getRequestByToken(token).executeAsOneOrNull()
	}

	suspend fun getRequest(id: Long, userId: Long): ReviewRequest? = withContext(ioDispatcher) {
		queries.getRequest(id, userId).executeAsOneOrNull()
	}

	suspend fun getRequestsForProject(userId: Long, projectId: Long): List<ReviewRequest> =
		withContext(ioDispatcher) {
				queries.getRequestsForProject(userId, projectId).executeAsList()
			}

		suspend fun updateStatus(id: Long, status: ReviewStatus) {
			withContext(ioDispatcher) {
			queries.updateStatus(status.toStringId(), id)
		}
	}

	suspend fun markOpened(id: Long, status: ReviewStatus, at: Instant) {
		withContext(ioDispatcher) {
			queries.markOpened(status.toStringId(), at, id)
		}
	}

	suspend fun touchActivity(id: Long, status: ReviewStatus, at: Instant) {
		withContext(ioDispatcher) {
			queries.touchActivity(status.toStringId(), at, id)
		}
	}

	suspend fun markSubmitted(id: Long, at: Instant) {
		withContext(ioDispatcher) {
			queries.markSubmitted(ReviewStatus.SUBMITTED.toStringId(), at, id)
		}
	}

	suspend fun markResolved(id: Long, at: Instant) {
		withContext(ioDispatcher) {
			queries.markResolved(ReviewStatus.RESOLVED.toStringId(), at, id)
		}
	}
}
